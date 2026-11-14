// db-check 는 운영 DB 를 읽기 전용 백업 계정으로 쿼리하는 임시 파드를 만들고 숫자 결과만 출력한다
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"regexp"
	"slices"
	"strings"
	"syscall"
	"time"

	"github.com/musqat/ggeolmuse/tools/internal/kube"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/utils/ptr"
	"sigs.k8s.io/yaml"
)

const (
	secretName = "ggeolmuse-secrets"
	image      = "postgres:16-alpine"
	marker     = "--"
)

// check 는 DB 하나에 보낼 쿼리
type check struct{ db, query string }

// databases 는 DB 이름마다 접속 주소 시크릿 키와 실제 DB 이름(keycloak 은 user 서버)
var databases = map[string]struct{ key, name string }{
	"user":     {"DB_URL_USER", ""},
	"trade":    {"DB_URL_TRADE", ""},
	"market":   {"DB_URL_MARKET", ""},
	"backtest": {"DB_URL_BACKTEST", ""},
	"keycloak": {"DB_URL_USER", "keycloak"},
}

// 파드 상태를 다시 묻는 간격
var pollEvery = 2 * time.Second

// 기다려도 시작되지 않는 컨테이너 대기 까닭
var stuckReasons = []string{"CreateContainerConfigError", "CreateContainerError", "ImagePullBackOff", "InvalidImageName"}

// 결과 칸 하나(정수 · 소수 · 지수 · NULL)
var numberField = regexp.MustCompile(`^(-?[0-9]+(\.[0-9]+)?([eE][-+]?[0-9]+)?|NULL)$`)

func main() {
	kubeconfig := flag.String("kubeconfig", "", "kubeconfig 경로(비우면 KUBECONFIG · ~/.kube/config)")
	namespace := flag.String("namespace", "default", "네임스페이스")
	timeout := flag.Duration("timeout", 3*time.Minute, "파드가 끝날 때까지 기다리는 시간")
	renderOnly := flag.Bool("render", false, "클러스터에 만들지 않고 매니페스트만 출력")
	flag.Parse()
	checks, err := parseArgs(flag.Args())
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		fmt.Fprintln(os.Stderr, "사용: db-check [-render] [-namespace 이름] [-timeout 3m] <DB> <쿼리> [<DB> <쿼리>]...  (DB: backtest · keycloak · market · trade · user)")
		os.Exit(2)
	}
	pod := manifest(fmt.Sprintf("db-check-%d", time.Now().Unix()), *namespace, checks)
	if *renderOnly {
		text, err := render(pod)
		if err != nil {
			fmt.Println("FAIL ", err)
			os.Exit(1)
		}
		fmt.Print(text)
		return
	}
	client, err := kube.Client(*kubeconfig)
	if err != nil {
		fmt.Println("FAIL  kubeconfig:", err)
		os.Exit(1)
	}
	// Ctrl+C 도 기다리기를 끝내고 파드를 지움
	interrupted, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	ctx, cancel := context.WithTimeout(interrupted, *timeout)
	code := run(ctx, client, pod, checks, os.Stdout)
	cancel()
	stop()
	os.Exit(code)
}

// parseArgs 는 <DB> <쿼리> 쌍
func parseArgs(args []string) ([]check, error) {
	if len(args) == 0 || len(args)%2 != 0 {
		return nil, errors.New("DB 와 쿼리를 쌍으로")
	}
	var checks []check
	for i := 0; i < len(args); i += 2 {
		if _, ok := databases[args[i]]; !ok {
			return nil, fmt.Errorf("모르는 DB: %s", args[i])
		}
		if strings.TrimSpace(args[i+1]) == "" {
			return nil, fmt.Errorf("%s 쿼리가 비어 있음", args[i])
		}
		checks = append(checks, check{args[i], args[i+1]})
	}
	return checks, nil
}

// manifest 는 쿼리마다 psql 을 돌리는 파드(계정 · 주소는 secretKeyRef)
func manifest(name, ns string, checks []check) *corev1.Pod {
	env := []corev1.EnvVar{
		secretEnv("PGUSER", "DB_USERNAME_BACKUP"),
		secretEnv("PGPASSWORD", "DB_PASSWORD_BACKUP"),
		{Name: "PGOPTIONS", Value: "-c default_transaction_read_only=on -c statement_timeout=60000"},
	}
	var keys []string
	script := []string{
		`url() { c="${1#jdbc:}"; c="${c%%\?*}"; [ -z "${2:-}" ] || c="${c%/*}/$2"; echo "$c"; }`,
		`q() { psql -d "$1" -X -A -t -F ' ' -P null=NULL -v ON_ERROR_STOP=1 -c "$2"; }`,
	}
	for i, c := range checks {
		d := databases[c.db]
		if !slices.Contains(keys, d.key) {
			keys = append(keys, d.key)
		}
		query := fmt.Sprintf("QUERY_%d", i+1)
		env = append(env, corev1.EnvVar{Name: query, Value: c.query})
		if i > 0 {
			script = append(script, "echo '"+marker+"'")
		}
		target := `"$` + d.key + `"`
		if d.name != "" {
			target += " " + d.name
		}
		script = append(script, fmt.Sprintf(`q "$(url %s)" "$%s"`, target, query))
	}
	slices.Sort(keys)
	for _, key := range keys {
		env = append(env, secretEnv(key, key))
	}
	return &corev1.Pod{
		TypeMeta:   metav1.TypeMeta{APIVersion: "v1", Kind: "Pod"},
		ObjectMeta: metav1.ObjectMeta{Name: name, Namespace: ns, Labels: map[string]string{"app": "db-check"}},
		Spec: corev1.PodSpec{
			RestartPolicy:                corev1.RestartPolicyNever,
			ActiveDeadlineSeconds:        ptr.To[int64](300),
			AutomountServiceAccountToken: ptr.To(false),
			SecurityContext: &corev1.PodSecurityContext{
				RunAsNonRoot:   ptr.To(true),
				RunAsUser:      ptr.To[int64](70),
				RunAsGroup:     ptr.To[int64](70),
				SeccompProfile: &corev1.SeccompProfile{Type: corev1.SeccompProfileTypeRuntimeDefault},
			},
			Containers: []corev1.Container{{
				Name:            "psql",
				Image:           image,
				ImagePullPolicy: corev1.PullIfNotPresent,
				Command:         []string{"sh", "-eu", "-c"},
				Args:            []string{strings.Join(script, "\n") + "\n"},
				Env:             env,
				SecurityContext: &corev1.SecurityContext{
					AllowPrivilegeEscalation: ptr.To(false),
					ReadOnlyRootFilesystem:   ptr.To(true),
					Capabilities:             &corev1.Capabilities{Drop: []corev1.Capability{"ALL"}},
				},
				Resources: corev1.ResourceRequirements{
					Requests: corev1.ResourceList{corev1.ResourceCPU: resource.MustParse("50m"), corev1.ResourceMemory: resource.MustParse("64Mi")},
					Limits:   corev1.ResourceList{corev1.ResourceMemory: resource.MustParse("128Mi")},
				},
			}},
		},
	}
}

func secretEnv(name, key string) corev1.EnvVar {
	return corev1.EnvVar{Name: name, ValueFrom: &corev1.EnvVarSource{
		SecretKeyRef: &corev1.SecretKeySelector{LocalObjectReference: corev1.LocalObjectReference{Name: secretName}, Key: key},
	}}
}

// render 는 kubectl apply 로 넣을 수 있는 매니페스트 YAML
func render(pod *corev1.Pod) (string, error) {
	b, err := yaml.Marshal(pod)
	return string(b), err
}

// run 은 파드를 만들어 결과를 쓰고 파드를 지운 뒤 종료 코드(0 통과 · 1 실패)
func run(ctx context.Context, client kubernetes.Interface, pod *corev1.Pod, checks []check, w io.Writer) int {
	pods := client.CoreV1().Pods(pod.Namespace)
	if _, err := pods.Create(ctx, pod, metav1.CreateOptions{}); err != nil {
		fmt.Fprintln(w, "FAIL  파드 만들기 실패:", err)
		return 1
	}
	code := report(ctx, client, pod, checks, w)
	cleanup, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := pods.Delete(cleanup, pod.Name, metav1.DeleteOptions{}); err != nil {
		fmt.Fprintf(w, "FAIL  파드 지우기 실패: %v · kubectl delete pod -n %s %s\n", err, pod.Namespace, pod.Name)
		code = 1
	}
	return code
}

// report 는 파드가 끝나면 숫자 결과를 쓰고 종료 코드
func report(ctx context.Context, client kubernetes.Interface, pod *corev1.Pod, checks []check, w io.Writer) int {
	pods := client.CoreV1().Pods(pod.Namespace)
	done, err := waitDone(ctx, client, pod)
	if err != nil {
		fmt.Fprintln(w, "FAIL  기다리기 실패:", err)
		return 1
	}
	if done.Status.Phase == corev1.PodFailed {
		fmt.Fprintln(w, "FAIL  파드 실패 ·", failure(done))
		return 1
	}
	logs, err := pods.GetLogs(pod.Name, &corev1.PodLogOptions{Container: "psql"}).DoRaw(ctx)
	if err != nil {
		fmt.Fprintln(w, "FAIL  로그 읽기 실패:", err)
		return 1
	}
	groups, err := results(string(logs), len(checks))
	if err != nil {
		fmt.Fprintln(w, "FAIL ", err)
		return 1
	}
	for i, c := range checks {
		fmt.Fprintf(w, "%s · %s\n", c.db, c.query)
		if len(groups[i]) == 0 {
			fmt.Fprintln(w, "  (행 없음)")
		}
		for _, line := range groups[i] {
			fmt.Fprintln(w, " ", line)
		}
	}
	return 0
}

// waitDone 은 끝난 파드(시작을 못 하거나 기한이 지나면 오류)
func waitDone(ctx context.Context, client kubernetes.Interface, pod *corev1.Pod) (*corev1.Pod, error) {
	for {
		p, err := client.CoreV1().Pods(pod.Namespace).Get(ctx, pod.Name, metav1.GetOptions{})
		if err != nil {
			return nil, err
		}
		if p.Status.Phase == corev1.PodSucceeded || p.Status.Phase == corev1.PodFailed {
			return p, nil
		}
		for _, s := range p.Status.ContainerStatuses {
			if s.State.Waiting != nil && slices.Contains(stuckReasons, s.State.Waiting.Reason) {
				return nil, fmt.Errorf("컨테이너 대기 %s", s.State.Waiting.Reason)
			}
		}
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-time.After(pollEvery):
		}
	}
}

// psql -c 종료 코드의 뜻
var exitMeanings = map[int32]string{1: "쿼리 오류", 2: "접속 실패"}

// failure 는 실패한 파드의 까닭(로그는 안 봄)
func failure(p *corev1.Pod) string {
	for _, s := range p.Status.ContainerStatuses {
		if t := s.State.Terminated; t != nil {
			meaning, ok := exitMeanings[t.ExitCode]
			if !ok {
				meaning = t.Reason
			}
			if meaning == "" {
				return fmt.Sprintf("psql 종료 코드 %d", t.ExitCode)
			}
			return fmt.Sprintf("psql 종료 코드 %d(%s)", t.ExitCode, meaning)
		}
	}
	if p.Status.Reason != "" {
		return p.Status.Reason
	}
	return "까닭 모름"
}

// results 는 쿼리별 결과 줄(숫자 아닌 줄이 있으면 개수만 담은 오류)
func results(logs string, n int) ([][]string, error) {
	groups := [][]string{nil}
	bad := 0
	// 끝 줄바꿈 뒤 조각만 제외(빈 줄은 빈 문자열 행)
	lines := strings.Split(logs, "\n")
	if lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	for _, line := range lines {
		line = strings.TrimRight(line, "\r")
		switch {
		case line == marker:
			groups = append(groups, nil)
		case !numeric(line):
			bad++
		default:
			groups[len(groups)-1] = append(groups[len(groups)-1], line)
		}
	}
	if bad > 0 {
		return nil, fmt.Errorf("숫자 아닌 줄 %d개(출력 안 함)", bad)
	}
	if len(groups) != n {
		return nil, fmt.Errorf("결과 %d개, 쿼리 %d개", len(groups), n)
	}
	return groups, nil
}

// numeric 은 공백으로 나눈 칸이 모두 숫자 · NULL 인 줄
func numeric(line string) bool {
	for _, field := range strings.Split(line, " ") {
		if !numberField.MatchString(field) {
			return false
		}
	}
	return true
}
