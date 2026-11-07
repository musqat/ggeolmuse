package main

import (
	"strings"
	"testing"
)

// 게이트웨이 → business 정책(포트는 각 경우가 채움)
const policy = `
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: business-from-gateway
spec:
  podSelector:
    matchLabels:
      tier: business-service
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: gateway-server
    ports:
    - port: %s
`

// business 서비스 하나(포트는 각 경우가 채움)
const deployment = `
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
spec:
  template:
    metadata:
      labels:
        tier: business-service
    spec:
      containers:
      - name: app
        ports:
        - containerPort: %s
`

func doc(tmpl, port string) string { return strings.Replace(tmpl, "%s", port, 1) }

func TestRun(t *testing.T) {
	cases := []struct {
		name  string
		input string
		code  int
		want  string
	}{
		{"정책이 서비스 포트를 열면 통과", doc(policy, "8081") + "---" + doc(deployment, "8081"), 0, "ok    user-service 8081"},
		{"서비스 포트가 정책에 없으면 실패", doc(policy, "8081") + "---" + doc(deployment, "8082"), 1, "FAIL  user-service 8082 · 허용 목록에 없음"},
		{"게이트웨이 정책이 없으면 실패", doc(deployment, "8081"), 1, "FAIL  NetworkPolicy 허용 포트를 읽지 못함"},
		{"정책 포트가 이름이면 실패", doc(policy, "http") + "---" + doc(deployment, "8081"), 1, "FAIL  이름으로 적은 포트 [http]"},
		{"business 워크로드가 없으면 실패", doc(policy, "8081"), 1, "FAIL  business 워크로드를 찾지 못함"},
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
