// netpol-check 는 NetworkPolicy 의 게이트웨이 허용 포트와 business 서비스의 containerPort 를 대조한다
//
//	go run ./cmd/netpol-check rendered.yaml
package main

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"slices"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	utilyaml "k8s.io/apimachinery/pkg/util/yaml"
	"sigs.k8s.io/yaml"
)

const (
	tierKey, tierValue = "tier", "business-service"
	gwKey, gwValue     = "app", "gateway-server"
)

type workload struct {
	name       string
	labels     map[string]string
	containers []corev1.Container
}

type target struct {
	name string
	port int
}

func main() {
	path := "rendered.yaml"
	if len(os.Args) > 1 {
		path = os.Args[1]
	}
	f, err := os.Open(path)
	if err != nil {
		fmt.Println("FAIL  읽기 실패:", err)
		os.Exit(1)
	}
	defer f.Close()
	os.Exit(run(f, os.Stdout))
}

// 렌더 결과를 대조해 결과를 w 에 쓰고 종료 코드를 돌려줌(0 통과 · 1 실패)
func run(r io.Reader, w io.Writer) int {
	policies, workloads, err := load(r)
	if err != nil {
		fmt.Fprintln(w, "FAIL  읽기 실패:", err)
		return 1
	}

	allowed, named := allowedPorts(policies)
	if len(named) > 0 {
		fmt.Fprintf(w, "FAIL  이름으로 적은 포트 %v · 숫자 포트만 대조함\n", named)
		return 1
	}
	if len(allowed) == 0 {
		fmt.Fprintln(w, "FAIL  NetworkPolicy 허용 포트를 읽지 못함")
		return 1
	}
	targets := businessPorts(workloads)
	if len(targets) == 0 {
		fmt.Fprintln(w, "FAIL  business 워크로드를 찾지 못함")
		return 1
	}

	fmt.Fprintf(w, "허용 포트  %v\n", allowed)
	code := 0
	for _, t := range targets {
		if slices.Contains(allowed, t.port) {
			fmt.Fprintf(w, "ok    %s %d\n", t.name, t.port)
		} else {
			fmt.Fprintf(w, "FAIL  %s %d · 허용 목록에 없음\n", t.name, t.port)
			code = 1
		}
	}
	return code
}

// 문서 단위로 읽어 NetworkPolicy 와 워크로드(Deployment · StatefulSet)만 고름
func load(r io.Reader) ([]networkingv1.NetworkPolicy, []workload, error) {
	var policies []networkingv1.NetworkPolicy
	var workloads []workload
	reader := utilyaml.NewYAMLReader(bufio.NewReader(r))
	for {
		doc, err := reader.Read()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return nil, nil, err
		}

		var head struct {
			Kind string `json:"kind"`
		}
		if err := yaml.Unmarshal(doc, &head); err != nil {
			return nil, nil, err
		}

		switch head.Kind {
		case "NetworkPolicy":
			var np networkingv1.NetworkPolicy
			if err := yaml.Unmarshal(doc, &np); err != nil {
				return nil, nil, err
			}
			policies = append(policies, np)
		case "Deployment":
			var d appsv1.Deployment
			if err := yaml.Unmarshal(doc, &d); err != nil {
				return nil, nil, err
			}
			workloads = append(workloads, workload{d.Name, d.Spec.Template.Labels, d.Spec.Template.Spec.Containers})
		case "StatefulSet":
			var s appsv1.StatefulSet
			if err := yaml.Unmarshal(doc, &s); err != nil {
				return nil, nil, err
			}
			workloads = append(workloads, workload{s.Name, s.Spec.Template.Labels, s.Spec.Template.Spec.Containers})
		}
	}
	return policies, workloads, nil
}

// 게이트웨이 → business 정책의 허용 포트(숫자)와 이름으로 적은 포트
func allowedPorts(policies []networkingv1.NetworkPolicy) ([]int, []string) {
	seen := map[int]bool{}
	var named []string
	for _, np := range policies {
		if np.Spec.PodSelector.MatchLabels[tierKey] != tierValue {
			continue
		}
		for _, rule := range np.Spec.Ingress {
			if !fromGateway(rule.From) {
				continue
			}
			for _, p := range rule.Ports {
				switch {
				case p.Port == nil:
				case p.Port.Type == intstr.String:
					named = append(named, p.Port.StrVal)
				default:
					seen[p.Port.IntValue()] = true
				}
			}
		}
	}
	ports := make([]int, 0, len(seen))
	for p := range seen {
		ports = append(ports, p)
	}
	slices.Sort(ports)
	return ports, named
}

func fromGateway(peers []networkingv1.NetworkPolicyPeer) bool {
	for _, peer := range peers {
		if peer.PodSelector != nil && peer.PodSelector.MatchLabels[gwKey] == gwValue {
			return true
		}
	}
	return false
}

// business 워크로드의 containerPort
func businessPorts(workloads []workload) []target {
	var out []target
	for _, w := range workloads {
		if w.labels[tierKey] != tierValue {
			continue
		}
		for _, c := range w.containers {
			for _, p := range c.Ports {
				out = append(out, target{w.name, int(p.ContainerPort)})
			}
		}
	}
	return out
}
