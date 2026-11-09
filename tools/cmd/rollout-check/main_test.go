package main

import (
	"context"
	"errors"
	"strings"
	"testing"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes/fake"
	k8stesting "k8s.io/client-go/testing"
	"k8s.io/utils/ptr"
)

// 롤아웃이 끝난 레플리카 1 Deployment
func done(name string, images ...string) *appsv1.Deployment {
	d := &appsv1.Deployment{
		ObjectMeta: metav1.ObjectMeta{Name: name, Namespace: "default", Generation: 2},
		Spec: appsv1.DeploymentSpec{
			Replicas: ptr.To[int32](1),
			Selector: &metav1.LabelSelector{MatchLabels: map[string]string{"app": name}},
		},
		Status: appsv1.DeploymentStatus{ObservedGeneration: 2, Replicas: 1, UpdatedReplicas: 1, ReadyReplicas: 1, AvailableReplicas: 1},
	}
	for _, image := range images {
		d.Spec.Template.Spec.Containers = append(d.Spec.Template.Spec.Containers, corev1.Container{Name: "app", Image: image})
	}
	return d
}

// app 라벨 파드(컨테이너 · init 컨테이너 하나씩의 재시작 수)
func pod(name, app string, restarts, initRestarts int32) *corev1.Pod {
	return &corev1.Pod{
		ObjectMeta: metav1.ObjectMeta{Name: name, Namespace: "default", Labels: map[string]string{"app": app}},
		Status: corev1.PodStatus{
			ContainerStatuses:     []corev1.ContainerStatus{{Name: "app", RestartCount: restarts}},
			InitContainerStatuses: []corev1.ContainerStatus{{Name: "wait", RestartCount: initRestarts}},
		},
	}
}

func TestRun(t *testing.T) {
	trade := done("trade-service", "muscathan/trade-service:1.8.1")
	trade.Generation, trade.Status.ObservedGeneration, trade.Status.UpdatedReplicas = 3, 3, 0
	client := fake.NewClientset(
		done("backtest-service", "muscathan/backtest-service:1.8.0"),
		done("user-service", "muscathan/user-service:05a3c0e"),
		trade,
		done("keycloak", "quay.io/keycloak/keycloak:26.8.1"),
		pod("backtest-service-a", "backtest-service", 1, 2),
		pod("backtest-service-b", "backtest-service", 0, 0),
		pod("other", "redis", 9, 0),
	)
	want := map[string]string{"backtest-service": "1.8.0", "user-service": "1.8.1", "trade-service": "1.8.1", "chat-service": "1.7.0"}

	var out strings.Builder
	if code := run(context.Background(), client, "default", want, &out); code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out.String())
	}
	for _, line := range []string{
		"ok    backtest-service · 1.8.0 · READY 1/1 · 재시작 3",
		"ok    keycloak · 26.8.1 · READY 1/1 · 재시작 0",
		"FAIL  trade-service · 1.8.1 · READY 1/1 · 재시작 0 · 새 파드 0/1",
		"FAIL  user-service · 05a3c0e · READY 1/1 · 재시작 0 · 기대 1.8.1",
		"FAIL  chat-service · Deployment 없음",
	} {
		if !strings.Contains(out.String(), line+"\n") {
			t.Errorf("출력에 %q 없음\n%s", line, out.String())
		}
	}
}

func TestRunAllDone(t *testing.T) {
	client := fake.NewClientset(done("frontend-web", "muscathan/frontend-web:1.8.0"), done("redis", "redis:7-alpine"))

	var out strings.Builder
	if code := run(context.Background(), client, "default", map[string]string{"frontend-web": "1.8.0"}, &out); code != 0 {
		t.Fatalf("종료 코드 %d, 기대 0\n%s", code, out.String())
	}
	if !strings.Contains(out.String(), "ok    redis · 7-alpine · READY 1/1 · 재시작 0\n") {
		t.Fatalf("기대 태그 없는 Deployment 도 롤아웃 결과 한 줄\n%s", out.String())
	}
}

func TestRunWithoutValues(t *testing.T) {
	client := fake.NewClientset(done("user-service", "muscathan/user-service:05a3c0e"))

	var out strings.Builder
	if code := run(context.Background(), client, "default", nil, &out); code != 0 {
		t.Fatalf("값 파일 없이는 태그 대조 없음, 종료 코드 %d\n%s", code, out.String())
	}
}

func TestRunOtherNamespace(t *testing.T) {
	other := done("user-service", "muscathan/user-service:1.8.0")
	other.Namespace = "staging"
	client := fake.NewClientset(other)

	var out strings.Builder
	if code := run(context.Background(), client, "default", nil, &out); code != 1 {
		t.Fatalf("Deployment 가 없는 네임스페이스는 실패, 종료 코드 %d\n%s", code, out.String())
	}
	if !strings.Contains(out.String(), "FAIL  default 네임스페이스에 Deployment 없음") {
		t.Fatalf("출력\n%s", out.String())
	}
}

func TestRunListError(t *testing.T) {
	client := fake.NewClientset()
	client.PrependReactor("list", "deployments", func(k8stesting.Action) (bool, runtime.Object, error) {
		return true, nil, errors.New("connection refused")
	})

	var out strings.Builder
	if code := run(context.Background(), client, "default", nil, &out); code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1", code)
	}
	if !strings.Contains(out.String(), "FAIL  Deployment 목록 실패: connection refused") {
		t.Fatalf("출력\n%s", out.String())
	}
}

func TestRunPodListError(t *testing.T) {
	client := fake.NewClientset(done("redis", "redis:7-alpine"))
	client.PrependReactor("list", "pods", func(k8stesting.Action) (bool, runtime.Object, error) {
		return true, nil, errors.New("forbidden")
	})

	var out strings.Builder
	if code := run(context.Background(), client, "default", nil, &out); code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1", code)
	}
	if !strings.Contains(out.String(), "FAIL  redis · 7-alpine · READY 1/1 · 파드 목록 실패: forbidden") {
		t.Fatalf("출력\n%s", out.String())
	}
}

func TestRolloutProblem(t *testing.T) {
	cases := []struct {
		name   string
		change func(*appsv1.Deployment)
		want   string
	}{
		{"끝난 롤아웃", func(*appsv1.Deployment) {}, ""},
		{"레플리카를 비우면 1", func(d *appsv1.Deployment) { d.Spec.Replicas = nil }, ""},
		{"0 으로 줄인 Deployment", func(d *appsv1.Deployment) {
			d.Spec.Replicas = ptr.To[int32](0)
			d.Status = appsv1.DeploymentStatus{ObservedGeneration: 2}
		}, ""},
		{"새 설정 반영 전", func(d *appsv1.Deployment) { d.Generation = 3 }, "새 설정 반영 대기"},
		{"진행 기한 넘김", func(d *appsv1.Deployment) {
			d.Status.UpdatedReplicas = 0
			d.Status.Conditions = []appsv1.DeploymentCondition{{Type: appsv1.DeploymentProgressing, Status: corev1.ConditionFalse, Reason: "ProgressDeadlineExceeded"}}
		}, "진행 기한 넘김"},
		{"새 파드 부족", func(d *appsv1.Deployment) { d.Status.UpdatedReplicas = 0 }, "새 파드 0/1"},
		{"옛 파드가 남음", func(d *appsv1.Deployment) { d.Status.Replicas = 2 }, "옛 파드 1개 종료 대기"},
		{"새 파드가 아직 사용 불가", func(d *appsv1.Deployment) { d.Status.AvailableReplicas, d.Status.ReadyReplicas = 0, 0 }, "사용 가능 0/1"},
	}
	for _, c := range cases {
		d := done("user-service", "muscathan/user-service:1.8.0")
		c.change(d)
		if got := rolloutProblem(d); got != c.want {
			t.Errorf("%s: %q, 기대 %q", c.name, got, c.want)
		}
	}
}

func TestImageTag(t *testing.T) {
	cases := []struct {
		name   string
		images []string
		want   string
	}{
		{"user-service", []string{"muscathan/user-service:1.8.0"}, "1.8.0"},
		{"user-service", []string{"envoyproxy/envoy:v1.36.0", "muscathan/user-service:abc1234"}, "abc1234"},
		{"keycloak", []string{"quay.io/keycloak/keycloak:26.8.1"}, "26.8.1"},
		{"redis", []string{"redis"}, "latest"},
		{"app", []string{"localhost:5000/app"}, "latest"},
		{"app", []string{"localhost:5000/app:2"}, "2"},
		{"app", []string{"muscathan/app@sha256:abcd"}, "sha256:abcd"},
		{"empty", nil, ""},
	}
	for _, c := range cases {
		if got := imageTag(done(c.name, c.images...)); got != c.want {
			t.Errorf("%s %q: %q, 기대 %q", c.name, c.images, got, c.want)
		}
	}
}

func TestExpectedTags(t *testing.T) {
	text := "configServer:\n  image:\n    tag: \"1.7.0\"\nservices:\n  userService:\n    image:\n      tag: \"05a3c0e\"\n"
	got, err := expectedTags(text)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 || got["config-server"] != "1.7.0" || got["user-service"] != "05a3c0e" {
		t.Fatalf("기대 태그 %v", got)
	}
	if _, err := expectedTags("global: {}\n"); err == nil {
		t.Fatal("서비스 태그가 없는 값 파일은 오류")
	}
}
