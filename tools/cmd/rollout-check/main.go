// rollout-check 는 Deployment 마다 이미지 태그 · 준비 · 재시작 수를 보고 운영 값 파일 태그와 대조한다
//
//	go run ./cmd/rollout-check -values ../helm/ggeolmuse/values-prod.yaml
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"os"
	"slices"
	"strings"
	"time"

	"github.com/musqat/ggeolmuse/tools/internal/kube"
	"github.com/musqat/ggeolmuse/tools/internal/values"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
)

func main() {
	kubeconfig := flag.String("kubeconfig", "", "kubeconfig 경로(비우면 KUBECONFIG · ~/.kube/config)")
	namespace := flag.String("namespace", "default", "네임스페이스")
	valuesPath := flag.String("values", "", "대조할 운영 값 파일(비우면 대조 안 함)")
	flag.Parse()
	var want map[string]string
	if *valuesPath != "" {
		text, err := os.ReadFile(*valuesPath)
		if err == nil {
			want, err = expectedTags(string(text))
		}
		if err != nil {
			fmt.Println("FAIL  값 파일:", err)
			os.Exit(1)
		}
	}
	client, err := kube.Client(*kubeconfig)
	if err != nil {
		fmt.Println("FAIL  kubeconfig:", err)
		os.Exit(1)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	code := run(ctx, client, *namespace, want, os.Stdout)
	cancel()
	os.Exit(code)
}

// expectedTags 는 값 파일의 서비스별 태그(서비스 이름이 Deployment 이름)
func expectedTags(text string) (map[string]string, error) {
	entries, err := values.Find(text)
	if err != nil {
		return nil, err
	}
	want := map[string]string{}
	for svc, e := range entries {
		want[svc] = e.Tag
	}
	return want, nil
}

// run 은 Deployment 마다 한 줄씩 쓰고 종료 코드(0 통과 · 1 실패)
func run(ctx context.Context, client kubernetes.Interface, ns string, want map[string]string, w io.Writer) int {
	list, err := client.AppsV1().Deployments(ns).List(ctx, metav1.ListOptions{})
	if err != nil {
		fmt.Fprintln(w, "FAIL  Deployment 목록 실패:", err)
		return 1
	}
	if len(list.Items) == 0 {
		fmt.Fprintf(w, "FAIL  %s 네임스페이스에 Deployment 없음\n", ns)
		return 1
	}
	deployments := list.Items
	slices.SortFunc(deployments, func(a, b appsv1.Deployment) int { return strings.Compare(a.Name, b.Name) })
	code := 0
	for i := range deployments {
		d := &deployments[i]
		tag := imageTag(d)
		fields := []string{d.Name, tag, fmt.Sprintf("READY %d/%d", d.Status.ReadyReplicas, replicas(d))}
		var problems []string
		if restarts, err := restartCount(ctx, client, d); err != nil {
			problems = append(problems, "파드 목록 실패: "+err.Error())
		} else {
			fields = append(fields, fmt.Sprintf("재시작 %d", restarts))
		}
		if p := rolloutProblem(d); p != "" {
			problems = append(problems, p)
		}
		if expected, ok := want[d.Name]; ok && tag != expected {
			problems = append(problems, "기대 "+expected)
		}
		mark := "ok   "
		if len(problems) > 0 {
			mark, code = "FAIL ", 1
		}
		fmt.Fprintln(w, mark, strings.Join(append(fields, problems...), " · "))
	}
	var missing []string
	for svc := range want {
		if !slices.ContainsFunc(deployments, func(d appsv1.Deployment) bool { return d.Name == svc }) {
			missing = append(missing, svc)
		}
	}
	slices.Sort(missing)
	for _, svc := range missing {
		fmt.Fprintf(w, "FAIL  %s · Deployment 없음\n", svc)
		code = 1
	}
	return code
}

// replicas 는 바라는 레플리카 수(비우면 1)
func replicas(d *appsv1.Deployment) int32 {
	if d.Spec.Replicas == nil {
		return 1
	}
	return *d.Spec.Replicas
}

// rolloutProblem 은 롤아웃이 덜 끝난 까닭(kubectl rollout status 기준, 끝났으면 빈 문자열)
func rolloutProblem(d *appsv1.Deployment) string {
	if d.Generation > d.Status.ObservedGeneration {
		return "새 설정 반영 대기"
	}
	for _, c := range d.Status.Conditions {
		if c.Type == appsv1.DeploymentProgressing && c.Reason == "ProgressDeadlineExceeded" {
			return "진행 기한 넘김"
		}
	}
	want, s := replicas(d), d.Status
	switch {
	case s.UpdatedReplicas < want:
		return fmt.Sprintf("새 파드 %d/%d", s.UpdatedReplicas, want)
	case s.Replicas > s.UpdatedReplicas:
		return fmt.Sprintf("옛 파드 %d개 종료 대기", s.Replicas-s.UpdatedReplicas)
	case s.AvailableReplicas < s.UpdatedReplicas:
		return fmt.Sprintf("사용 가능 %d/%d", s.AvailableReplicas, s.UpdatedReplicas)
	}
	return ""
}

// imageTag 는 Deployment 이름과 같은 컨테이너(없으면 첫 컨테이너)의 이미지 태그
func imageTag(d *appsv1.Deployment) string {
	containers := d.Spec.Template.Spec.Containers
	if len(containers) == 0 {
		return ""
	}
	image := containers[0].Image
	for _, c := range containers {
		repo, _ := splitImage(c.Image)
		if repo == d.Name || strings.HasSuffix(repo, "/"+d.Name) {
			image = c.Image
			break
		}
	}
	_, tag := splitImage(image)
	return tag
}

// splitImage 는 이미지 참조의 저장소와 태그(digest 고정이면 digest)
func splitImage(image string) (string, string) {
	if repo, digest, ok := strings.Cut(image, "@"); ok {
		return repo, digest
	}
	if i := strings.LastIndex(image, ":"); i > strings.LastIndex(image, "/") {
		return image[:i], image[i+1:]
	}
	return image, "latest"
}

// restartCount 는 Deployment 파드들의 재시작 수 합
func restartCount(ctx context.Context, client kubernetes.Interface, d *appsv1.Deployment) (int32, error) {
	selector, err := metav1.LabelSelectorAsSelector(d.Spec.Selector)
	if err != nil {
		return 0, err
	}
	pods, err := client.CoreV1().Pods(d.Namespace).List(ctx, metav1.ListOptions{LabelSelector: selector.String()})
	if err != nil {
		return 0, err
	}
	var total int32
	for _, p := range pods.Items {
		for _, statuses := range [][]corev1.ContainerStatus{p.Status.InitContainerStatuses, p.Status.ContainerStatuses} {
			for _, s := range statuses {
				total += s.RestartCount
			}
		}
	}
	return total, nil
}
