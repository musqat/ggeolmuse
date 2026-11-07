// netpol-check 는 NetworkPolicy 가 게이트웨이에서 business 서비스의 containerPort 로 오는 트래픽을 여는지 대조한다
//
//	go run ./cmd/netpol-check rendered.yaml
package main

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/apimachinery/pkg/util/intstr"
	utilyaml "k8s.io/apimachinery/pkg/util/yaml"
	"sigs.k8s.io/yaml"
)

const (
	tierKey, tierValue = "tier", "business-service"
	gwKey, gwValue     = "app", "gateway-server"
)

// 워크로드 이름 · 파드 라벨 · 포트를 여는 컨테이너(네이티브 사이드카 포함)
type workload struct {
	name       string
	labels     map[string]string
	containers []corev1.Container
}

// 열린 포트 구간(proto 가 비면 모든 프로토콜)
type portRange struct {
	proto    corev1.Protocol
	from, to int
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
	var gateway map[string]string
	var business []workload
	for _, wl := range workloads {
		if wl.labels[gwKey] == gwValue && gateway == nil {
			gateway = wl.labels
		}
		if wl.labels[tierKey] == tierValue {
			business = append(business, wl)
		}
	}
	if gateway == nil {
		fmt.Fprintf(w, "FAIL  게이트웨이 워크로드를 찾지 못함(파드 라벨 %s=%s)\n", gwKey, gwValue)
		return 1
	}
	if len(business) == 0 {
		fmt.Fprintln(w, "FAIL  business 워크로드를 찾지 못함")
		return 1
	}

	opened := make([][]portRange, len(business))
	anyPolicy := false
	for i, wl := range business {
		ranges, matched, named, err := allowed(policies, wl.labels, gateway)
		if err != nil {
			fmt.Fprintln(w, "FAIL  선택자 읽기 실패:", err)
			return 1
		}
		if len(named) > 0 {
			fmt.Fprintf(w, "FAIL  이름으로 적은 포트 %v · 숫자 포트만 대조함\n", named)
			return 1
		}
		opened[i], anyPolicy = ranges, anyPolicy || matched
	}
	if !anyPolicy {
		fmt.Fprintln(w, "FAIL  게이트웨이에서 오는 트래픽을 여는 business NetworkPolicy 없음")
		return 1
	}

	code := 0
	for i, wl := range business {
		for _, c := range wl.containers {
			for _, p := range c.Ports {
				if covers(opened[i], protocolOf(p.Protocol), int(p.ContainerPort)) {
					fmt.Fprintf(w, "ok    %s %d\n", wl.name, p.ContainerPort)
				} else {
					fmt.Fprintf(w, "FAIL  %s %d · 허용 목록에 없음\n", wl.name, p.ContainerPort)
					code = 1
				}
			}
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
			workloads = append(workloads, newWorkload(d.Name, d.Spec.Template))
		case "StatefulSet":
			var s appsv1.StatefulSet
			if err := yaml.Unmarshal(doc, &s); err != nil {
				return nil, nil, err
			}
			workloads = append(workloads, newWorkload(s.Name, s.Spec.Template))
		}
	}
	return policies, workloads, nil
}

// 컨테이너와 네이티브 사이드카(restartPolicy: Always 인 initContainer)
func newWorkload(name string, tmpl corev1.PodTemplateSpec) workload {
	containers := append([]corev1.Container{}, tmpl.Spec.Containers...)
	for _, c := range tmpl.Spec.InitContainers {
		if c.RestartPolicy != nil && *c.RestartPolicy == corev1.ContainerRestartPolicyAlways {
			containers = append(containers, c)
		}
	}
	return workload{name, tmpl.Labels, containers}
}

// 파드 라벨을 고르는 정책 가운데 게이트웨이에서 오는 규칙의 포트 구간 · 그런 규칙이 있었는지 · 이름으로 적은 포트
func allowed(policies []networkingv1.NetworkPolicy, pod, gateway map[string]string) ([]portRange, bool, []string, error) {
	var ranges []portRange
	var named []string
	matched := false
	for _, np := range policies {
		ok, err := selects(&np.Spec.PodSelector, pod)
		if err != nil || !ok {
			if err != nil {
				return nil, false, nil, err
			}
			continue
		}
		for _, rule := range np.Spec.Ingress {
			ok, err := fromGateway(rule.From, gateway)
			if err != nil {
				return nil, false, nil, err
			}
			if !ok {
				continue
			}
			matched = true
			if len(rule.Ports) == 0 {
				ranges = append(ranges, portRange{"", 1, 65535})
				continue
			}
			for _, p := range rule.Ports {
				proto := protocolOf(ptrValue(p.Protocol))
				switch {
				case p.Port == nil:
					ranges = append(ranges, portRange{proto, 1, 65535})
				case p.Port.Type == intstr.String:
					named = append(named, p.Port.StrVal)
				default:
					from, to := p.Port.IntValue(), p.Port.IntValue()
					if p.EndPort != nil {
						to = int(*p.EndPort)
					}
					ranges = append(ranges, portRange{proto, from, to})
				}
			}
		}
	}
	return ranges, matched, named, nil
}

// 출처가 비면 모든 출처, 다른 네임스페이스를 고르는 출처는 게이트웨이(같은 네임스페이스)가 아님
func fromGateway(peers []networkingv1.NetworkPolicyPeer, gateway map[string]string) (bool, error) {
	if len(peers) == 0 {
		return true, nil
	}
	for _, peer := range peers {
		if peer.IPBlock != nil {
			continue
		}
		if ns := peer.NamespaceSelector; ns != nil && (len(ns.MatchLabels) > 0 || len(ns.MatchExpressions) > 0) {
			continue
		}
		if peer.PodSelector == nil {
			return true, nil
		}
		ok, err := selects(peer.PodSelector, gateway)
		if err != nil || ok {
			return ok, err
		}
	}
	return false, nil
}

// 선택자가 그 라벨을 고르는지
func selects(sel *metav1.LabelSelector, set map[string]string) (bool, error) {
	s, err := metav1.LabelSelectorAsSelector(sel)
	if err != nil {
		return false, err
	}
	return s.Matches(labels.Set(set)), nil
}

func covers(ranges []portRange, proto corev1.Protocol, port int) bool {
	for _, r := range ranges {
		if (r.proto == "" || r.proto == proto) && r.from <= port && port <= r.to {
			return true
		}
	}
	return false
}

// 비어 있으면 TCP
func protocolOf(p corev1.Protocol) corev1.Protocol {
	if p == "" {
		return corev1.ProtocolTCP
	}
	return p
}

func ptrValue(p *corev1.Protocol) corev1.Protocol {
	if p == nil {
		return ""
	}
	return *p
}
