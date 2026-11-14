// chat-coverage 는 coverage.py JSON 의 줄 · 분기 커버리지를 최소치와 비교하고 표를 잡 요약에 남긴다
//
//	go run ./cmd/chat-coverage <coverage.json> <줄 최소치> <분기 최소치>
package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"strconv"
	"strings"
)

// 표 한 줄(항목 · 커버리지 % · 최소치 %)
type row struct {
	name           string
	value, minimum float64
}

func main() {
	os.Exit(run(os.Args[1:], os.Getenv("GITHUB_STEP_SUMMARY"), os.Stdout, os.Stderr))
}

// 표를 stdout 과 잡 요약 파일(summary 가 비면 안 씀)에 쓰고 종료 코드를 돌려줌(0 통과 · 1 미달이나 오류)
func run(args []string, summary string, stdout, stderr io.Writer) int {
	if len(args) != 3 {
		fmt.Fprintln(stderr, "사용법: chat-coverage <coverage.json> <줄 최소치> <분기 최소치>")
		return 1
	}
	minLine, err := parseMinimum(args[1])
	if err != nil {
		fmt.Fprintln(stderr, "FAIL  줄 최소치:", err)
		return 1
	}
	minBranch, err := parseMinimum(args[2])
	if err != nil {
		fmt.Fprintln(stderr, "FAIL  분기 최소치:", err)
		return 1
	}
	line, branch, err := load(args[0])
	if err != nil {
		fmt.Fprintln(stderr, "FAIL  읽기 실패:", err)
		return 1
	}
	rows := []row{{"줄", line, minLine}, {"분기", branch, minBranch}}

	table := render(rows)
	fmt.Fprintln(stdout, table)
	if summary != "" {
		if err := appendFile(summary, "### chat 커버리지\n\n"+table); err != nil {
			fmt.Fprintln(stderr, "FAIL  잡 요약 쓰기 실패:", err)
			return 1
		}
	}
	var below []string
	for _, r := range rows {
		if r.value < r.minimum {
			below = append(below, r.name)
		}
	}
	if len(below) > 0 {
		fmt.Fprintln(stdout, "최소치 미달: "+strings.Join(below, ", "))
		return 1
	}
	return 0
}

// 10진 유한 실수만(16진 표기 · NaN · Inf 거부)
func parseMinimum(s string) (float64, error) {
	s = strings.TrimSpace(s)
	if strings.ContainsAny(s, "xX") {
		return 0, fmt.Errorf("10진 숫자가 아님: %q", s)
	}
	v, err := strconv.ParseFloat(s, 64)
	if err == nil && (math.IsNaN(v) || math.IsInf(v, 0)) {
		return 0, fmt.Errorf("유한한 숫자가 아님: %q", s)
	}
	return v, err
}

// totals 의 줄 · 분기 커버리지 %(칸 이름 대소문자 구분)
func load(path string) (line, branch float64, err error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return 0, 0, err
	}
	var doc map[string]json.RawMessage
	if err := json.Unmarshal(data, &doc); err != nil {
		return 0, 0, err
	}
	raw, ok := doc["totals"]
	if !ok {
		return 0, 0, errors.New("totals 없음")
	}
	var totals map[string]json.RawMessage
	if err := json.Unmarshal(raw, &totals); err != nil || totals == nil {
		return 0, 0, errors.New("totals 가 객체가 아님")
	}
	if line, err = percent(totals, "covered_lines", "num_statements"); err != nil {
		return 0, 0, err
	}
	if branch, err = percent(totals, "covered_branches", "num_branches"); err != nil {
		return 0, 0, err
	}
	return line, branch, nil
}

func percent(totals map[string]json.RawMessage, coveredKey, totalKey string) (float64, error) {
	covered, err := number(totals, coveredKey)
	if err != nil {
		return 0, err
	}
	total, err := number(totals, totalKey)
	if err != nil {
		return 0, err
	}
	if total == 0 {
		return 0, fmt.Errorf("totals 의 %s 가 0", totalKey)
	}
	return 100 * covered / total, nil
}

// totals 의 숫자 칸 하나(없거나 null · 숫자가 아니면 오류)
func number(totals map[string]json.RawMessage, key string) (float64, error) {
	raw, ok := totals[key]
	if !ok {
		return 0, fmt.Errorf("totals 에 %s 없음", key)
	}
	var v *float64
	if err := json.Unmarshal(raw, &v); err != nil || v == nil {
		return 0, fmt.Errorf("totals 의 %s 가 숫자가 아님", key)
	}
	return *v, nil
}

// 마크다운 표(값은 소수 둘째, 최소치는 유효 숫자 여섯 자리까지 짧게)
func render(rows []row) string {
	var b strings.Builder
	b.WriteString("| 항목 | 값 | 최소치 |\n|---|---|---|\n")
	for _, r := range rows {
		fmt.Fprintf(&b, "| %s | %.2f%% | %s%% |\n", r.name, r.value, strconv.FormatFloat(r.minimum, 'g', 6, 64))
	}
	return b.String()
}

func appendFile(path, text string) error {
	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		return err
	}
	if _, err := f.WriteString(text); err != nil {
		f.Close()
		return err
	}
	return f.Close()
}
