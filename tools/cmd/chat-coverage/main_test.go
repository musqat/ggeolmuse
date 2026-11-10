package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// coverage.py JSON 파일(totals 의 줄 · 분기 칸)
func coverageFile(t *testing.T, body string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "coverage.json")
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	return path
}

func totals(coveredLines, statements, coveredBranches, branches int) string {
	return fmt.Sprintf(`{"meta": {"format": 3}, "files": {}, "totals": {"covered_lines": %d, "num_statements": %d, "percent_covered": 0, "covered_branches": %d, "num_branches": %d}}`,
		coveredLines, statements, coveredBranches, branches)
}

const head = "| 항목 | 값 | 최소치 |\n|---|---|---|\n"

func TestRun(t *testing.T) {
	cases := []struct {
		name     string
		body     string
		minimums []string
		code     int
		stdout   string
	}{
		{"둘 다 최소치 이상이면 표만 내고 통과", totals(24, 26, 9, 10), []string{"90", "80"}, 0,
			head + "| 줄 | 92.31% | 90% |\n| 분기 | 90.00% | 80% |\n\n"},
		{"최소치와 같으면 통과", totals(9, 10, 8, 10), []string{"90", "80"}, 0,
			head + "| 줄 | 90.00% | 90% |\n| 분기 | 80.00% | 80% |\n\n"},
		{"줄이 최소치 아래면 실패", totals(89, 100, 8, 10), []string{"90", "80"}, 1,
			head + "| 줄 | 89.00% | 90% |\n| 분기 | 80.00% | 80% |\n\n최소치 미달: 줄\n"},
		{"분기만 최소치 아래면 분기만 미달", totals(9, 10, 79, 100), []string{"90", "80"}, 1,
			head + "| 줄 | 90.00% | 90% |\n| 분기 | 79.00% | 80% |\n\n최소치 미달: 분기\n"},
		{"둘 다 최소치 아래면 줄 · 분기 차례로 미달", totals(1, 2, 1, 4), []string{"90", "80"}, 1,
			head + "| 줄 | 50.00% | 90% |\n| 분기 | 25.00% | 80% |\n\n최소치 미달: 줄, 분기\n"},
		{"값은 소수 둘째 반올림", totals(2, 3, 1, 3), []string{"0", "0"}, 0,
			head + "| 줄 | 66.67% | 0% |\n| 분기 | 33.33% | 0% |\n\n"},
		{"최소치는 유효 숫자 여섯 자리까지 짧게", totals(1, 1, 1, 1), []string{"90.50", "1e-05"}, 0,
			head + "| 줄 | 100.00% | 90.5% |\n| 분기 | 100.00% | 1e-05% |\n\n"},
		{"큰 최소치는 지수 표기", totals(1, 1, 1, 1), []string{"1234567", "80"}, 1,
			head + "| 줄 | 100.00% | 1.23457e+06% |\n| 분기 | 100.00% | 80% |\n\n최소치 미달: 줄\n"},
		{"최소치 앞뒤 공백은 무시", totals(9, 10, 8, 10), []string{" 90 ", "80\n"}, 0,
			head + "| 줄 | 90.00% | 90% |\n| 분기 | 80.00% | 80% |\n\n"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			var stdout, stderr strings.Builder
			args := append([]string{coverageFile(t, c.body)}, c.minimums...)
			code := run(args, "", &stdout, &stderr)
			if code != c.code {
				t.Fatalf("종료 코드 %d, 기대 %d\n%s%s", code, c.code, stdout.String(), stderr.String())
			}
			if stdout.String() != c.stdout {
				t.Fatalf("출력\n%q\n기대\n%q", stdout.String(), c.stdout)
			}
		})
	}
}

func TestRunSummary(t *testing.T) {
	summary := filepath.Join(t.TempDir(), "summary.md")
	if err := os.WriteFile(summary, []byte("### 앞 단계\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	var stdout, stderr strings.Builder
	code := run([]string{coverageFile(t, totals(24, 26, 9, 10)), "90", "80"}, summary, &stdout, &stderr)
	if code != 0 {
		t.Fatalf("종료 코드 %d\n%s", code, stderr.String())
	}
	got, err := os.ReadFile(summary)
	if err != nil {
		t.Fatal(err)
	}
	want := "### 앞 단계\n### chat 커버리지\n\n" + head + "| 줄 | 92.31% | 90% |\n| 분기 | 90.00% | 80% |\n"
	if string(got) != want {
		t.Fatalf("잡 요약\n%q\n기대\n%q", got, want)
	}
}

func TestRunSummaryCreatesFile(t *testing.T) {
	summary := filepath.Join(t.TempDir(), "summary.md")
	var stdout, stderr strings.Builder
	if code := run([]string{coverageFile(t, totals(1, 2, 1, 4)), "90", "80"}, summary, &stdout, &stderr); code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1", code)
	}
	got, err := os.ReadFile(summary)
	if err != nil {
		t.Fatal(err)
	}
	if want := "### chat 커버리지\n\n" + head + "| 줄 | 50.00% | 90% |\n| 분기 | 25.00% | 80% |\n"; string(got) != want {
		t.Fatalf("잡 요약\n%q\n기대\n%q", got, want)
	}
}

func TestRunSummaryWriteFailure(t *testing.T) {
	summary := filepath.Join(t.TempDir(), "없는 폴더", "summary.md")
	var stdout, stderr strings.Builder
	code := run([]string{coverageFile(t, totals(24, 26, 9, 10)), "90", "80"}, summary, &stdout, &stderr)
	if code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1", code)
	}
	if !strings.HasPrefix(stdout.String(), head) || stderr.Len() == 0 {
		t.Fatalf("표는 출력하고 오류는 stderr 로\n%q\n%q", stdout.String(), stderr.String())
	}
}

func TestRunFailures(t *testing.T) {
	missing := filepath.Join(t.TempDir(), "없음.json")
	cases := []struct {
		name string
		args func(t *testing.T) []string
	}{
		{"인자가 셋이 아니면 실패", func(t *testing.T) []string { return []string{coverageFile(t, totals(1, 1, 1, 1)), "90"} }},
		{"최소치가 숫자가 아니면 실패", func(t *testing.T) []string { return []string{coverageFile(t, totals(1, 1, 1, 1)), "90", "많이"} }},
		{"최소치가 16진 실수면 실패", func(t *testing.T) []string { return []string{coverageFile(t, totals(1, 1, 1, 1)), "0x1p3", "80"} }},
		{"totals 이름의 대소문자가 다르면 실패", func(t *testing.T) []string {
			return []string{coverageFile(t, `{"TOTALS": {"covered_lines": 1, "num_statements": 1, "covered_branches": 1, "num_branches": 1}}`), "90", "80"}
		}},
		{"칸 이름의 대소문자가 다르면 실패", func(t *testing.T) []string {
			return []string{coverageFile(t, `{"totals": {"COVERED_LINES": 1, "num_statements": 1, "covered_branches": 1, "num_branches": 1}}`), "90", "80"}
		}},
		{"칸이 null 이면 실패", func(t *testing.T) []string {
			return []string{coverageFile(t, `{"totals": {"covered_lines": null, "num_statements": 1, "covered_branches": 1, "num_branches": 1}}`), "90", "80"}
		}},
		{"파일이 없으면 실패", func(t *testing.T) []string { return []string{missing, "90", "80"} }},
		{"JSON 이 아니면 실패", func(t *testing.T) []string { return []string{coverageFile(t, "<html>"), "90", "80"} }},
		{"totals 가 없으면 실패", func(t *testing.T) []string { return []string{coverageFile(t, `{"files": {}}`), "90", "80"} }},
		{"분기 칸이 없으면 실패", func(t *testing.T) []string {
			return []string{coverageFile(t, `{"totals": {"covered_lines": 1, "num_statements": 1}}`), "90", "80"}
		}},
		{"칸이 숫자가 아니면 실패", func(t *testing.T) []string {
			return []string{coverageFile(t, `{"totals": {"covered_lines": "1", "num_statements": 1, "covered_branches": 1, "num_branches": 1}}`), "90", "80"}
		}},
		{"문장 수가 0 이면 실패", func(t *testing.T) []string { return []string{coverageFile(t, totals(0, 0, 1, 1)), "90", "80"} }},
		{"분기 수가 0 이면 표 없이 실패", func(t *testing.T) []string { return []string{coverageFile(t, totals(1, 1, 0, 0)), "90", "80"} }},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			var stdout, stderr strings.Builder
			code := run(c.args(t), "", &stdout, &stderr)
			if code != 1 {
				t.Fatalf("종료 코드 %d, 기대 1\n%s", code, stdout.String())
			}
			if stdout.Len() != 0 || stderr.Len() == 0 {
				t.Fatalf("표 없이 오류만 stderr 로\n%q\n%q", stdout.String(), stderr.String())
			}
		})
	}
}
