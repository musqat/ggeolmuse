// rollout-check 는 배포된 이미지 태그 · 상태를 운영 값 파일 태그와 대조한다(-argocd 면 ArgoCD API 와 공개 주소로)
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
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
	services := flag.String("only", "", "대조할 서비스(쉼표 구분, 비우면 값 파일 전부)")
	argocd := flag.String("argocd", "", "ArgoCD 주소(클러스터 대신 ArgoCD API 로 확인, 토큰은 ARGOCD_TOKEN)")
	app := flag.String("app", "ggeolmuse", "ArgoCD 애플리케이션 이름")
	site := flag.String("site", "", "스모크 요청 · 운영 시간 확인용 공개 주소")
	wait := flag.Duration("wait", 20*time.Minute, "ArgoCD 반영 대기 시간")
	flag.Parse()
	var want map[string]string
	if *valuesPath != "" {
		text, err := os.ReadFile(*valuesPath)
		if err == nil {
			want, err = expectedTags(string(text))
		}
		if err == nil {
			want, err = only(want, *services)
		}
		if err != nil {
			fmt.Println("FAIL  값 파일:", err)
			os.Exit(1)
		}
	}
	if *argocd != "" {
		p := public{argocd: strings.TrimSuffix(*argocd, "/"), app: *app, token: os.Getenv("ARGOCD_TOKEN"),
			accessID: os.Getenv("CF_ACCESS_CLIENT_ID"), accessSecret: os.Getenv("CF_ACCESS_CLIENT_SECRET"),
			site: strings.TrimSuffix(*site, "/"), wait: *wait, interval: 15 * time.Second,
			client: &http.Client{Timeout: 20 * time.Second}, latest: masterTags(*valuesPath)}
		ctx, cancel := context.WithTimeout(context.Background(), *wait+time.Minute)
		code := runPublic(ctx, p, want, os.Stdout)
		cancel()
		os.Exit(code)
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

// masterTags 는 origin master 값 파일의 서비스별 태그를 읽는 함수(못 읽으면 nil)
func masterTags(path string) func(context.Context) map[string]string {
	rel := filepath.ToSlash(path)
	if !filepath.IsAbs(path) && !strings.HasPrefix(rel, "./") && !strings.HasPrefix(rel, "../") {
		rel = "./" + rel
	}
	return func(ctx context.Context) map[string]string {
		if exec.CommandContext(ctx, "git", "fetch", "-q", "--depth=1", "origin", "master").Run() != nil {
			return nil
		}
		text, err := exec.CommandContext(ctx, "git", "show", "FETCH_HEAD:"+rel).Output()
		if err != nil {
			return nil
		}
		tags, err := expectedTags(string(text))
		if err != nil {
			return nil
		}
		return tags
	}
}

// only 는 쉼표로 고른 서비스의 태그(비우면 전부, 값 파일에 없는 서비스는 오류)
func only(want map[string]string, list string) (map[string]string, error) {
	if strings.TrimSpace(list) == "" {
		return want, nil
	}
	picked := map[string]string{}
	for _, svc := range strings.Split(list, ",") {
		svc = strings.TrimSpace(svc)
		tag, ok := want[svc]
		if !ok {
			return nil, fmt.Errorf("값 파일에 %s 없음", svc)
		}
		picked[svc] = tag
	}
	return picked, nil
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
