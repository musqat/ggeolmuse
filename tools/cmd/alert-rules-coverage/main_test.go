package main

import (
	"slices"
	"strings"
	"testing"
)

const rules = `
groups:
  - name: availability
    rules:
      - alert: ServiceDown
        expr: up == 0
        annotations:
          summary: "다운"
          runbook_url: "https://example.test/RUNBOOK.md#servicedown"
      - record: slo:errors:ratio_rate5m
        expr: vector(0)
      - alert: TradeBuysPending
        expr: vector(1)
        annotations:
          runbook_url: "https://example.test/RUNBOOK.md#tradebuyspending"
`

const tests = `
tests:
  - interval: 1m
    alert_rule_test:
      - eval_time: 5m
        alertname: ServiceDown
        exp_alerts:
          - exp_labels:
              severity: critical
      - eval_time: 5m
        alertname: TradeBuysPending
        exp_alerts:
          - exp_labels:
              severity: warning
`

func TestProblems(t *testing.T) {
	cases := []struct {
		name   string
		rules  string
		tests  string
		want   []string
		alerts int
	}{
		{"알림마다 대응 문서 주소 · 울리는 테스트가 있으면 통과", rules, tests, nil, 2},
		{"runbook_url 이 없는 알림",
			strings.Replace(rules, "          runbook_url: \"https://example.test/RUNBOOK.md#servicedown\"\n", "", 1), tests,
			[]string{"ServiceDown: runbook_url 없음"}, 2},
		{"대응 문서 주소는 알림 이름 소문자 앵커로 끝남",
			strings.Replace(rules, "#tradebuyspending", "#servicedown", 1), tests,
			[]string{"TradeBuysPending: runbook_url 이 #tradebuyspending 로 끝나지 않음(https://example.test/RUNBOOK.md#servicedown)"}, 2},
		{"기대 알림이 빈 테스트만 있으면 울리는 테스트 없음", rules,
			strings.Replace(tests, "        alertname: TradeBuysPending\n        exp_alerts:\n          - exp_labels:\n              severity: warning\n",
				"        alertname: TradeBuysPending\n        exp_alerts: []\n", 1),
			[]string{"TradeBuysPending: 울리는 promtool 테스트 없음"}, 2},
		{"기록 규칙은 보지 않음",
			strings.Replace(rules, "expr: vector(0)", "expr: vector(0)\n        labels:\n          team: none", 1), tests, nil, 2},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, alerts, err := problems([]byte(c.rules), []byte(c.tests))
			if err != nil {
				t.Fatal(err)
			}
			if !slices.Equal(got, c.want) || alerts != c.alerts {
				t.Errorf("problems = %q · 알림 %d, 기대 %q · 알림 %d", got, alerts, c.want, c.alerts)
			}
		})
	}
}

func TestProblemsRejectsBrokenYAML(t *testing.T) {
	if _, _, err := problems([]byte("groups: ["), []byte(tests)); err == nil {
		t.Error("깨진 규칙 파일인데 오류 없음")
	}
}
