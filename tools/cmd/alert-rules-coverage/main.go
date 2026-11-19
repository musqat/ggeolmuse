// alert-rules-coverage 는 렌더한 알림 규칙마다 대응 문서 주소와 울리는 promtool 테스트가 있는지 보고, 빠진 게 있으면 종료 코드 1 로 끝낸다.
package main

import (
	"fmt"
	"os"
	"strings"

	"sigs.k8s.io/yaml"
)

// 렌더한 PrometheusRule 의 spec(groups 아래 규칙)
type ruleFile struct {
	Groups []struct {
		Rules []struct {
			Alert       string            `json:"alert"`
			Annotations map[string]string `json:"annotations"`
		} `json:"rules"`
	} `json:"groups"`
}

// promtool 단위 테스트 파일의 알림 기대값
type testFile struct {
	Tests []struct {
		AlertRuleTest []struct {
			AlertName string `json:"alertname"`
			ExpAlerts []any  `json:"exp_alerts"`
		} `json:"alert_rule_test"`
	} `json:"tests"`
}

func main() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "사용: alert-rules-coverage <규칙 파일> <테스트 파일>")
		os.Exit(2)
	}
	rulesYAML, err := os.ReadFile(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	testsYAML, err := os.ReadFile(os.Args[2])
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	found, alerts, err := problems(rulesYAML, testsYAML)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	for _, p := range found {
		fmt.Printf("::error::%s\n", p)
	}
	if len(found) > 0 {
		os.Exit(1)
	}
	fmt.Printf("알림 %d개 모두 대응 문서 주소 · 테스트 있음\n", alerts)
}

// 규칙 파일 순서대로 알림마다 빠진 대응 문서 주소 · 울리는 테스트와 알림 수
func problems(rulesYAML, testsYAML []byte) ([]string, int, error) {
	var rf ruleFile
	if err := yaml.Unmarshal(rulesYAML, &rf); err != nil {
		return nil, 0, fmt.Errorf("규칙 파일: %w", err)
	}
	var tf testFile
	if err := yaml.Unmarshal(testsYAML, &tf); err != nil {
		return nil, 0, fmt.Errorf("테스트 파일: %w", err)
	}
	fired := map[string]bool{}
	for _, t := range tf.Tests {
		for _, c := range t.AlertRuleTest {
			if len(c.ExpAlerts) > 0 {
				fired[c.AlertName] = true
			}
		}
	}
	var found []string
	alerts := 0
	for _, g := range rf.Groups {
		for _, r := range g.Rules {
			if r.Alert == "" {
				continue
			}
			alerts++
			url := r.Annotations["runbook_url"]
			anchor := "#" + strings.ToLower(r.Alert)
			if url == "" {
				found = append(found, r.Alert+": runbook_url 없음")
			} else if !strings.HasSuffix(url, anchor) {
				found = append(found, fmt.Sprintf("%s: runbook_url 이 %s 로 끝나지 않음(%s)", r.Alert, anchor, url))
			}
			if !fired[r.Alert] {
				found = append(found, r.Alert+": 울리는 promtool 테스트 없음")
			}
		}
	}
	return found, alerts, nil
}
