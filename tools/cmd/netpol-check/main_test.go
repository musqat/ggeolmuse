package main

import (
	"strings"
	"testing"
)

// 정책(파드 선택자 · 출처 · 포트는 YAML 흐름 표기)
func np(selector, from, ports string) string {
	return "apiVersion: networking.k8s.io/v1\nkind: NetworkPolicy\nmetadata:\n  name: p\nspec:\n  podSelector:\n    matchLabels: " + selector +
		"\n  ingress:\n  - from: " + from + "\n    ports: " + ports + "\n---\n"
}

// Deployment(파드 라벨 · 컨테이너 · initContainer 는 YAML 흐름 표기)
func deploy(name, labels, containers, inits string) string {
	return "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: " + name + "\nspec:\n  template:\n    metadata:\n      labels: " + labels +
		"\n    spec:\n      containers: " + containers + "\n      initContainers: " + inits + "\n---\n"
}

const (
	biz    = "{tier: business-service}"
	fromGw = "[{podSelector: {matchLabels: {app: gateway-server}}}]"
)

var (
	gateway = deploy("gateway-server", "{app: gateway-server}", "[{name: app, ports: [{containerPort: 8080}]}]", "[]")
	user    = deploy("user-service", "{tier: business-service, app: user-service}", "[{name: app, ports: [{containerPort: 8081}]}]", "[]")
)

func TestRun(t *testing.T) {
	cases := []struct {
		name  string
		input string
		code  int
		want  string
	}{
		{"서비스 포트를 연 정책: 통과", np(biz, fromGw, "[{port: 8081}]") + gateway + user, 0, "ok    user-service 8081"},
		{"정책에 없는 서비스 포트: 실패", np(biz, fromGw, "[{port: 8082}]") + gateway + user, 1, "FAIL  user-service 8081 · 허용 목록에 없음"},
		{"게이트웨이 정책 없음: 실패", gateway + user, 1, "FAIL  게이트웨이에서 오는 트래픽을 여는 business NetworkPolicy 없음"},
		{"이름으로 적은 정책 포트: 실패", np(biz, fromGw, "[{port: http}]") + gateway + user, 1, "FAIL  이름으로 적은 포트 [http]"},
		{"business 워크로드 없음: 실패", np(biz, fromGw, "[{port: 8081}]") + gateway, 1, "FAIL  business 워크로드를 찾지 못함"},
		{"정책과 다른 게이트웨이 파드 라벨: 실패",
			np(biz, fromGw, "[{port: 8081}]") + deploy("edge", "{app: edge}", "[{name: app}]", "[]") + user, 1, "FAIL  게이트웨이 워크로드를 찾지 못함"},
		{"게이트웨이에 없는 출처 선택자 라벨: 막힘",
			np(biz, "[{podSelector: {matchLabels: {app: gateway-server, track: canary}}}]", "[{port: 8081}]") + gateway + user, 1, "여는 business NetworkPolicy 없음"},
		{"다른 네임스페이스 선택자와 묶인 출처: 막힘",
			np(biz, "[{namespaceSelector: {matchLabels: {name: edge}}, podSelector: {matchLabels: {app: gateway-server}}}]", "[{port: 8081}]") + gateway + user, 1, "여는 business NetworkPolicy 없음"},
		{"빈 네임스페이스 선택자: 모든 네임스페이스",
			np(biz, "[{namespaceSelector: {}, podSelector: {matchLabels: {app: gateway-server}}}]", "[{port: 8081}]") + gateway + user, 0, "ok    user-service 8081"},
		{"trade-service 를 고른 정책의 포트: 세지 않음",
			np("{tier: business-service, app: trade-service}", fromGw, "[{port: 8081}]") + np(biz, fromGw, "[{port: 8082}]") + gateway + user, 1, "FAIL  user-service 8081 · 허용 목록에 없음"},
		{"UDP 만 연 정책: TCP 포트 막힘", np(biz, fromGw, "[{port: 8081, protocol: UDP}]") + gateway + user, 1, "허용 목록에 없음"},
		{"endPort 범위 안: 통과", np(biz, fromGw, "[{port: 8000, endPort: 8100}]") + gateway + user, 0, "ok    user-service 8081"},
		{"빈 포트: 모든 포트", np(biz, fromGw, "[]") + gateway + user, 0, "ok    user-service 8081"},
		{"빈 출처: 모든 출처", np(biz, "[]", "[{port: 8081}]") + gateway + user, 0, "ok    user-service 8081"},
		{"네이티브 사이드카 포트도 대조",
			np(biz, fromGw, "[{port: 8081}]") + gateway +
				deploy("user-service", biz, "[{name: app, ports: [{containerPort: 8081}]}]", "[{name: proxy, restartPolicy: Always, ports: [{containerPort: 15001}]}]"),
			1, "FAIL  user-service 15001 · 허용 목록에 없음"},
		{"끝나는 initContainer 포트는 보지 않음",
			np(biz, fromGw, "[{port: 8081}]") + gateway +
				deploy("user-service", biz, "[{name: app, ports: [{containerPort: 8081}]}]", "[{name: migrate, ports: [{containerPort: 9000}]}]"),
			0, "ok    user-service 8081"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			var out strings.Builder
			code := run(strings.NewReader(c.input), &out)
			if code != c.code {
				t.Fatalf("종료 코드 %d, 기대 %d\n%s", code, c.code, out.String())
			}
			if !strings.Contains(out.String(), c.want) {
				t.Fatalf("출력에 %q 없음\n%s", c.want, out.String())
			}
		})
	}
}
