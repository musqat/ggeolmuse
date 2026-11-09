package main

import (
	"context"
	"errors"
	"slices"
	"strings"
	"testing"
	"time"

	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes/fake"
	k8stesting "k8s.io/client-go/testing"
	"sigs.k8s.io/yaml"
)

var checks = []check{
	{"trade", "SELECT count(*) FROM trades"},
	{"user", "SELECT count(*), count(DISTINCT email) FROM users"},
	{"keycloak", "SELECT count(*) FROM user_entity"},
}

func TestManifestEnv(t *testing.T) {
	pod := manifest("db-check-1", "default", checks)
	env := map[string]corev1.EnvVar{}
	for _, e := range pod.Spec.Containers[0].Env {
		if _, dup := env[e.Name]; dup {
			t.Errorf("환경 변수 %s 가 두 번", e.Name)
		}
		env[e.Name] = e
	}
	cases := []struct {
		name, secretKey, value string
	}{
		{"PGUSER", "DB_USERNAME_BACKUP", ""},
		{"PGPASSWORD", "DB_PASSWORD_BACKUP", ""},
		{"DB_URL_TRADE", "DB_URL_TRADE", ""},
		{"DB_URL_USER", "DB_URL_USER", ""},
		{"PGOPTIONS", "", "-c default_transaction_read_only=on -c statement_timeout=60000"},
		{"QUERY_1", "", "SELECT count(*) FROM trades"},
		{"QUERY_2", "", "SELECT count(*), count(DISTINCT email) FROM users"},
		{"QUERY_3", "", "SELECT count(*) FROM user_entity"},
	}
	for _, c := range cases {
		e, ok := env[c.name]
		switch {
		case !ok:
			t.Errorf("%s 없음", c.name)
		case c.secretKey != "":
			ref := e.ValueFrom
			if e.Value != "" || ref == nil || ref.SecretKeyRef == nil || ref.SecretKeyRef.Name != "ggeolmuse-secrets" || ref.SecretKeyRef.Key != c.secretKey {
				t.Errorf("%s: 시크릿 %s 의 secretKeyRef 아님 (%+v)", c.name, c.secretKey, e)
			}
		case e.Value != c.value || e.ValueFrom != nil:
			t.Errorf("%s: %q, 기대 %q", c.name, e.Value, c.value)
		}
	}
	if len(env) != len(cases) {
		t.Errorf("환경 변수 %d개, 기대 %d개(쓰지 않는 DB 주소 없음)", len(env), len(cases))
	}
}

func TestManifestPod(t *testing.T) {
	pod := manifest("db-check-1", "default", checks)
	c := pod.Spec.Containers[0]
	cases := []struct {
		name string
		ok   bool
	}{
		{"이름 · 네임스페이스", pod.Name == "db-check-1" && pod.Namespace == "default"},
		{"app 라벨", pod.Labels["app"] == "db-check"},
		{"다시 시작 안 함", pod.Spec.RestartPolicy == corev1.RestartPolicyNever},
		{"실행 기한", pod.Spec.ActiveDeadlineSeconds != nil && *pod.Spec.ActiveDeadlineSeconds > 0},
		{"서비스 계정 토큰 없음", pod.Spec.AutomountServiceAccountToken != nil && !*pod.Spec.AutomountServiceAccountToken},
		{"postgres 사용자 70", pod.Spec.SecurityContext.RunAsNonRoot != nil && *pod.Spec.SecurityContext.RunAsNonRoot &&
			*pod.Spec.SecurityContext.RunAsUser == 70 && *pod.Spec.SecurityContext.RunAsGroup == 70},
		{"seccomp RuntimeDefault", pod.Spec.SecurityContext.SeccompProfile.Type == corev1.SeccompProfileTypeRuntimeDefault},
		{"권한 상승 막음", c.SecurityContext.AllowPrivilegeEscalation != nil && !*c.SecurityContext.AllowPrivilegeEscalation},
		{"capability 전부 뺌", slices.Equal(c.SecurityContext.Capabilities.Drop, []corev1.Capability{"ALL"})},
		{"읽기 전용 루트", c.SecurityContext.ReadOnlyRootFilesystem != nil && *c.SecurityContext.ReadOnlyRootFilesystem},
		{"메모리 한도 · CPU 요청", !c.Resources.Limits.Memory().IsZero() && !c.Resources.Requests.Cpu().IsZero()},
		{"psql 이미지", c.Name == "psql" && c.Image == "postgres:16-alpine"},
	}
	for _, tc := range cases {
		if !tc.ok {
			t.Errorf("%s 아님", tc.name)
		}
	}
}

func TestManifestScript(t *testing.T) {
	c := manifest("db-check-1", "default", checks).Spec.Containers[0]
	if !slices.Equal(c.Command, []string{"sh", "-eu", "-c"}) || len(c.Args) != 1 {
		t.Fatalf("명령 %q · 인자 %d개", c.Command, len(c.Args))
	}
	script := c.Args[0]
	for _, line := range []string{
		`q "$(url "$DB_URL_TRADE")" "$QUERY_1"`,
		`q "$(url "$DB_URL_USER")" "$QUERY_2"`,
		`q "$(url "$DB_URL_USER" keycloak)" "$QUERY_3"`,
	} {
		if !strings.Contains(script, line+"\n") {
			t.Errorf("스크립트에 %q 없음\n%s", line, script)
		}
	}
	if n := strings.Count(script, "echo '--'\n"); n != 2 {
		t.Errorf("구분 줄 %d개, 기대 2개", n)
	}
	for _, c := range checks {
		if strings.Contains(script, c.query) {
			t.Errorf("쿼리 글자가 스크립트에 들어감: %q", c.query)
		}
	}
}

func TestRender(t *testing.T) {
	text, err := render(manifest("db-check-1", "default", checks[:1]))
	if err != nil {
		t.Fatal(err)
	}
	var back corev1.Pod
	if err := yaml.Unmarshal([]byte(text), &back); err != nil {
		t.Fatal(err)
	}
	if back.Kind != "Pod" || back.APIVersion != "v1" || back.Spec.Containers[0].Env[0].ValueFrom.SecretKeyRef == nil {
		t.Fatalf("다시 읽은 매니페스트 %s/%s\n%s", back.APIVersion, back.Kind, text)
	}
}

func TestParseArgs(t *testing.T) {
	got, err := parseArgs([]string{"trade", "SELECT 1", "keycloak", "SELECT 2"})
	if err != nil || !slices.Equal(got, []check{{"trade", "SELECT 1"}, {"keycloak", "SELECT 2"}}) {
		t.Fatalf("%v %v", got, err)
	}
	for _, args := range [][]string{nil, {"trade"}, {"orders", "SELECT 1"}, {"trade", " "}} {
		if _, err := parseArgs(args); err == nil {
			t.Errorf("%q: 오류 아님", args)
		}
	}
}

func TestResults(t *testing.T) {
	cases := []struct {
		name, logs string
		n          int
		want       [][]string
		err        string
	}{
		{"한 쿼리 한 줄", "42\n", 1, [][]string{{"42"}}, ""},
		{"여러 칸 · 빈 결과", "4 4\n--\n", 2, [][]string{{"4 4"}, nil}, ""},
		{"소수 · 음수 · 지수 · NULL", "1.5 -3\n1e+06\nNULL\n--\n7\r\n", 2, [][]string{{"1.5 -3", "1e+06", "NULL"}, {"7"}}, ""},
		{"출력 없음", "", 1, [][]string{nil}, ""},
		{"숫자 아닌 줄", "3\nalice@example.com\n2026-11-10\n", 1, nil, "숫자 아닌 줄 2개(출력 안 함)"},
		{"숫자 뒤 글자", "42 alice\n", 1, nil, "숫자 아닌 줄 1개(출력 안 함)"},
		{"빈 문자열 한 행", "\n", 1, nil, "숫자 아닌 줄 1개(출력 안 함)"},
		{"빈 문자열 행 뒤 숫자", "\n--\n1\n", 2, nil, "숫자 아닌 줄 1개(출력 안 함)"},
		{"끝 줄바꿈 없음", "42\n--\n7", 2, [][]string{{"42"}, {"7"}}, ""},
		{"결과 수가 다름", "42\n", 2, nil, "결과 1개, 쿼리 2개"},
	}
	for _, c := range cases {
		got, err := results(c.logs, c.n)
		switch {
		case c.err != "":
			if err == nil || err.Error() != c.err {
				t.Errorf("%s: 오류 %v, 기대 %q", c.name, err, c.err)
			}
		case err != nil:
			t.Errorf("%s: %v", c.name, err)
		case !slices.EqualFunc(got, c.want, func(a, b []string) bool { return slices.Equal(a, b) }):
			t.Errorf("%s: %q, 기대 %q", c.name, got, c.want)
		}
	}
}

// 만들 때 status 를 채우고(kubelet 대신) 로그는 logs 로 돌려주는 가짜 클라이언트
func fakeCluster(status corev1.PodStatus, logs string) *fake.Clientset {
	client := fake.NewClientset()
	client.PrependReactor("create", "pods", func(a k8stesting.Action) (bool, runtime.Object, error) {
		a.(k8stesting.CreateAction).GetObject().(*corev1.Pod).Status = status
		return false, nil, nil
	})
	client.PrependReactor("get", "pods", func(a k8stesting.Action) (bool, runtime.Object, error) {
		if a.GetSubresource() != "log" {
			return false, nil, nil
		}
		return true, &runtime.Unknown{Raw: []byte(logs)}, nil
	})
	return client
}

func terminated(code int32, reason string) corev1.PodStatus {
	return corev1.PodStatus{Phase: corev1.PodFailed, ContainerStatuses: []corev1.ContainerStatus{{
		Name: "psql", State: corev1.ContainerState{Terminated: &corev1.ContainerStateTerminated{ExitCode: code, Reason: reason}},
	}}}
}

func TestRun(t *testing.T) {
	pollEvery = time.Millisecond
	cases := []struct {
		name   string
		status corev1.PodStatus
		logs   string
		code   int
		lines  []string
	}{
		{"숫자 결과", corev1.PodStatus{Phase: corev1.PodSucceeded}, "42\n--\n3 4\n--\n", 0, []string{
			"trade · SELECT count(*) FROM trades\n  42\n",
			"user · SELECT count(*), count(DISTINCT email) FROM users\n  3 4\n",
			"keycloak · SELECT count(*) FROM user_entity\n  (행 없음)\n",
		}},
		{"숫자 아닌 결과", corev1.PodStatus{Phase: corev1.PodSucceeded}, "alice@example.com\n--\n1\n--\n2\n", 1, []string{
			"FAIL  숫자 아닌 줄 1개(출력 안 함)\n",
		}},
		{"쿼리 오류", terminated(1, "Error"), "ERROR: relation \"alice\" does not exist\n", 1, []string{
			"FAIL  파드 실패 · psql 종료 코드 1(쿼리 오류)\n",
		}},
		{"접속 실패", terminated(2, "Error"), "", 1, []string{"FAIL  파드 실패 · psql 종료 코드 2(접속 실패)\n"}},
		{"메모리 한도", terminated(137, "OOMKilled"), "", 1, []string{"FAIL  파드 실패 · psql 종료 코드 137(OOMKilled)\n"}},
		{"시크릿 키 없음", corev1.PodStatus{Phase: corev1.PodPending, ContainerStatuses: []corev1.ContainerStatus{{
			Name: "psql", State: corev1.ContainerState{Waiting: &corev1.ContainerStateWaiting{Reason: "CreateContainerConfigError"}},
		}}}, "", 1, []string{"FAIL  기다리기 실패: 컨테이너 대기 CreateContainerConfigError\n"}},
		{"실행 기한 넘김", corev1.PodStatus{Phase: corev1.PodFailed, Reason: "DeadlineExceeded"}, "", 1, []string{
			"FAIL  파드 실패 · DeadlineExceeded\n",
		}},
	}
	for _, c := range cases {
		client := fakeCluster(c.status, c.logs)
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		var out strings.Builder
		code := run(ctx, client, manifest("db-check-1", "default", checks), checks, &out)
		cancel()
		if code != c.code {
			t.Errorf("%s: 종료 코드 %d, 기대 %d\n%s", c.name, code, c.code, out.String())
		}
		for _, line := range c.lines {
			if !strings.Contains(out.String(), line) {
				t.Errorf("%s: 출력에 %q 없음\n%s", c.name, line, out.String())
			}
		}
		if strings.Contains(out.String(), "alice") {
			t.Errorf("%s: 숫자 아닌 로그가 출력에 나옴\n%s", c.name, out.String())
		}
		if _, err := client.CoreV1().Pods("default").Get(context.Background(), "db-check-1", metav1.GetOptions{}); !apierrors.IsNotFound(err) {
			t.Errorf("%s: 끝난 뒤 파드가 남음(%v)", c.name, err)
		}
	}
}

func TestRunTimeout(t *testing.T) {
	pollEvery = time.Millisecond
	client := fakeCluster(corev1.PodStatus{Phase: corev1.PodRunning}, "")
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
	defer cancel()

	var out strings.Builder
	if code := run(ctx, client, manifest("db-check-1", "default", checks), checks, &out); code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1", code)
	}
	if !strings.Contains(out.String(), "FAIL  기다리기 실패: context deadline exceeded") {
		t.Fatalf("출력\n%s", out.String())
	}
	if _, err := client.CoreV1().Pods("default").Get(context.Background(), "db-check-1", metav1.GetOptions{}); !apierrors.IsNotFound(err) {
		t.Fatalf("제한 시간이 지나도 파드는 지움(%v)", err)
	}
}

func TestRunCreateAndDeleteErrors(t *testing.T) {
	client := fake.NewClientset()
	client.PrependReactor("create", "pods", func(k8stesting.Action) (bool, runtime.Object, error) {
		return true, nil, errors.New("forbidden")
	})
	var out strings.Builder
	if code := run(context.Background(), client, manifest("db-check-1", "default", checks), checks, &out); code != 1 ||
		!strings.Contains(out.String(), "FAIL  파드 만들기 실패: forbidden") {
		t.Fatalf("만들기 실패: 종료 코드 %d\n%s", code, out.String())
	}

	client = fakeCluster(corev1.PodStatus{Phase: corev1.PodSucceeded}, "1\n--\n2\n--\n3\n")
	client.PrependReactor("delete", "pods", func(k8stesting.Action) (bool, runtime.Object, error) {
		return true, nil, errors.New("timeout")
	})
	out.Reset()
	if code := run(context.Background(), client, manifest("db-check-1", "default", checks), checks, &out); code != 1 ||
		!strings.Contains(out.String(), "FAIL  파드 지우기 실패: timeout · kubectl delete pod -n default db-check-1") {
		t.Fatalf("지우기 실패: 종료 코드 %d\n%s", code, out.String())
	}
}
