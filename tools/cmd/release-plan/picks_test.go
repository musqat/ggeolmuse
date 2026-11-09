package main

import (
	"maps"
	"os"
	"slices"
	"strings"
	"testing"
)

const affectedSection = "## 영향받는 서비스\n- [x] user-service\n- [x] ggeolmuse-common\n"

func TestParsePicks(t *testing.T) {
	cases := []struct {
		name      string
		body      string
		want      map[string]string
		wantFound bool
	}{
		{"버전과 체크 안 한 줄", "## 요약\n - 고친다\n\n## 빌드할 서비스\n- [x] backtest-service 1.8.0\n- [ ] user-service\n",
			map[string]string{"backtest-service": "1.8.0"}, true},
		{"대문자 X · 버전 없음은 SHA", "## 빌드할 서비스\n- [X] frontend-web\n- [x] user-service\n",
			map[string]string{"frontend-web": "", "user-service": ""}, true},
		{"칸 없는 옛 PR", "## 요약\n - 고친다\n\n" + affectedSection, map[string]string{}, false},
		{"영향받는 서비스 칸의 체크는 보지 않음", affectedSection + "\n## 빌드할 서비스\n- [ ] user-service\n", map[string]string{}, true},
		{"다음 제목에서 칸이 끝남", "## 빌드할 서비스\n- [x] trade-service\n\n## 참고\n- [x] user-service\n",
			map[string]string{"trade-service": ""}, true},
		{"CRLF 본문", "## 빌드할 서비스\r\n- [x] trade-service 1.8.0\r\n- [ ] user-service\r\n",
			map[string]string{"trade-service": "1.8.0"}, true},
		{"체크 안 한 모르는 이름은 보지 않음", "## 빌드할 서비스\n- [ ] ggeolmuse-common\n- [x] chat-service\n",
			map[string]string{"chat-service": ""}, true},
		{"빈 본문", "", map[string]string{}, false},
		{"제목 뒤 공백 · 닫는 # · 세 단계 제목", "### 빌드할   서비스 ##  \n- [x] config-server\n", map[string]string{"config-server": ""}, true},
		{"여러 줄 HTML 주석 안의 예시는 보지 않음",
			"## 빌드할 서비스\n<!--\n체크하면 그 서비스만 나간다. 예:\n- [x] backtest-service 1.8.0\n-->\n- [ ] backtest-service\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"줄 끝 HTML 주석은 버전에 넣지 않음", "## 빌드할 서비스\n- [x] backtest-service 1.8.0 <!-- 버전, 비우면 SHA -->\n",
			map[string]string{"backtest-service": "1.8.0"}, true},
		{"코드 블록 안의 칸 예시는 보지 않음",
			"## 요약\n```\n## 빌드할 서비스\n- [x] backtest-service 1.8.0\n```\n\n## 빌드할 서비스\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"요약 인라인 코드의 닫히지 않은 <!-- 는 칸을 지우지 않음",
			"## 요약\n - 줄 끝 `<!--` 는 버전 아님\n\n## 빌드할 서비스\n<!-- 안내 -->\n- [x] backtest-service 1.8.0\n",
			map[string]string{"backtest-service": "1.8.0"}, true},
		{"코드 블록 안의 닫히지 않은 <!-- 도",
			"## 요약\n```\n<!--\n```\n\n## 빌드할 서비스\n<!-- 안내 -->\n- [x] frontend-web\n",
			map[string]string{"frontend-web": ""}, true},
		{"닫히지 않은 주석 블록 뒤는 보지 않음", "## 빌드할 서비스\n- [x] user-service\n<!--\n- [x] trade-service\n",
			map[string]string{"user-service": ""}, true},
		{"빈 주석 <!--> 은 그 줄에서 닫힘", "## 빌드할 서비스\n<!-->\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"빈 주석 <!---> 도", "## 빌드할 서비스\n<!--->\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"목록 표지 뒤 한 줄 주석은 그 줄만 숨김", "## 빌드할 서비스\n- <!-- [x] trade-service -->\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"네 개짜리 펜스 안의 ``` 는 닫지 않음", "## 요약\n````md\n```\n## 빌드할 서비스\n- [x] user-service\n```\n````\n",
			map[string]string{}, false},
		{"펜스 안의 ```go 줄은 닫지 않음", "## 요약\n```\n```go\n## 빌드할 서비스\n- [x] user-service\n```\n```\n",
			map[string]string{}, false},
		{"~~~ 펜스는 ~~~ 로만 닫힘", "## 요약\n~~~\n```\n~~~\n\n## 빌드할 서비스\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"줄 머리 인라인 코드 ``` 는 펜스가 아님", "## 요약\n```make test``` 로 돌렸다\n\n## 빌드할 서비스\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"항목 안에서 닫는 들여 쓴 주석은 그 줄들만 숨김", "## 빌드할 서비스\n- [x] user-service\n  <!--\n  메모\n\n  -->\n- [x] trade-service\n",
			map[string]string{"user-service": "", "trade-service": ""}, true},
		{"목록 표지 뒤에서 열고 항목 안에서 닫는 주석", "## 빌드할 서비스\n- [x] user-service\n- <!-- 보류\n  - [x] trade-service\n  -->\n",
			map[string]string{"user-service": ""}, true},
		{"1 ~ 3칸 들여 쓴 최상위 주석", "## 빌드할 서비스\n   <!--\n   - [x] trade-service\n   -->\n- [x] user-service\n",
			map[string]string{"user-service": ""}, true},
		{"칸 뒤의 닫히지 않은 코드 블록", "## 빌드할 서비스\n- [x] user-service\n\n```\nlog\n",
			map[string]string{"user-service": ""}, true},
		{"* · + · 숫자 글머리", "## 빌드할 서비스\n* [x] trade-service\n+ [x] user-service\n1. [x] frontend-web 1.8.0\n",
			map[string]string{"trade-service": "", "user-service": "", "frontend-web": "1.8.0"}, true},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, found, err := parsePicks(c.body)
			if err != nil {
				t.Fatalf("오류 %v", err)
			}
			if !maps.Equal(got, c.want) || found != c.wantFound {
				t.Fatalf("고른 서비스 %v · 칸 %v, 기대 %v · %v", got, found, c.want, c.wantFound)
			}
		})
	}
}

// 목록 항목 안에서 연 펜스가 닫히지 않아 칸 제목까지 코드로 보이면 조용히 보류하지 않고 오류
func TestParsePicksOpenFence(t *testing.T) {
	body := "## 요약\n- 예\n  ```\n  code\n- 다음\n\n## 빌드할 서비스\n- [x] user-service\n"
	if _, _, err := parsePicks(body); err == nil || !strings.Contains(err.Error(), "닫히지 않은 코드 블록") {
		t.Fatalf("오류 %v, 기대에 닫히지 않은 코드 블록", err)
	}
}

// 수동 실행의 쉼표 목록(칸과 같은 규칙)
func TestParseList(t *testing.T) {
	got, err := parseList(" backtest-service 1.8.0, frontend-web ,, ")
	if err != nil || !maps.Equal(got, map[string]string{"backtest-service": "1.8.0", "frontend-web": ""}) {
		t.Fatalf("고른 서비스 %v · 오류 %v", got, err)
	}
	for _, c := range []struct{ list, want string }{
		{"", "비어 있음"},
		{" , ", "비어 있음"},
		{"messaging", "모르는 서비스"},
		{"user-service v1.8.0", "버전 모양"},
		{"user-service, user-service 1.8.0", "두 번"},
	} {
		if _, err := parseList(c.list); err == nil || !strings.Contains(err.Error(), c.want) {
			t.Fatalf("%q: 오류 %v, 기대에 %q", c.list, err, c.want)
		}
	}
}

// PR 템플릿의 빌드할 서비스 칸(기본은 모두 빈 칸, 배포하는 서비스 전부)
func TestTemplate(t *testing.T) {
	raw, err := os.ReadFile("../../../.github/pull_request_template.md")
	if err != nil {
		t.Fatal(err)
	}
	picks, found, err := parsePicks(string(raw))
	if err != nil || !found || len(picks) != 0 {
		t.Fatalf("템플릿 그대로: 고른 서비스 %v · 칸 %v · 오류 %v, 기대는 빈 칸", picks, found, err)
	}
	// 요약에 닫히지 않은 <!-- 를 적어도 칸 주석의 --> 와 이어지지 않음
	edited := strings.Replace(strings.ReplaceAll(string(raw), "\r\n", "\n"), "## 요약\n", "## 요약\n - 줄 끝 `<!--` 는 버전 아님\n", 1)
	all, _, err := parsePicks(strings.ReplaceAll(edited, "- [ ] ", "- [x] "))
	if err != nil {
		t.Fatalf("모두 체크: %v", err)
	}
	if got := slices.Sorted(maps.Keys(all)); !slices.Equal(got, slices.Sorted(slices.Values(services))) {
		t.Fatalf("칸의 서비스 %v, 기대 %v", got, services)
	}
}

func TestParsePicksErrors(t *testing.T) {
	cases := []struct {
		name string
		body string
		want string
	}{
		{"체크한 모르는 이름은 쓸 수 있는 이름과 함께", "- [x] backtest 1.8.0", "쓸 수 있는 서비스: config-server"},
		{"배포하지 않는 모듈", "- [x] ggeolmuse-common", "모르는 서비스"},
		{"버전 두 자리", "- [x] backtest-service 1.8", "버전 모양"},
		{"v 붙은 버전", "- [x] backtest-service v1.8.0", "버전 모양"},
		{"앞에 0 붙은 버전", "- [x] backtest-service 01.8.0", "버전 모양"},
		{"버전 뒤에 글", "- [x] backtest-service 1.8.0 (조건부)", "버전 모양"},
		{"체크 칸 글자가 x 가 아님", "- [o] backtest-service", "체크 칸"},
		{"같은 서비스 두 번", "- [x] user-service\n- [x] user-service 1.8.0", "두 번"},
		{"칸 제목 두 번", "- [x] user-service\n\n## 빌드할 서비스\n- [x] trade-service", "칸이 두 번"},
		// 목록 · 인용 안에서 연 주석이 닫히기 전에 항목이 끝나면 GitHub 화면에서 뒤가 다 숨겨져 줄 단위로 따라갈 수 없음
		{"인용 안에서 열고 인용 밖으로 나간 주석", "> <!-- 메모\n- [x] user-service", "항목 밖까지"},
		{"항목 안에서 열고 다음 항목으로 넘어간 주석", "- [x] user-service\n  <!-- 보류\n- [x] trade-service\n  -->\n- [x] chat-service", "항목 밖까지"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			_, _, err := parsePicks("## 빌드할 서비스\n" + c.body + "\n")
			if err == nil || !strings.Contains(err.Error(), c.want) {
				t.Fatalf("오류 %v, 기대에 %q", err, c.want)
			}
		})
	}
}

func TestCompareVersions(t *testing.T) {
	cases := []struct {
		a, b   string
		want   int
		wantOK bool
	}{
		{"1.8.0", "1.7.0", 1, true},
		{"1.7.0", "1.7.0", 0, true},
		{"1.9.0", "1.10.0", -1, true},
		{"2.0.0", "1.99.99", 1, true},
		{"1.7.1", "1.7.0", 1, true},
		{"1.8.0", "01.7.0", 1, true},
		{"0.1.0", "908331f", 0, false},
		{"1.8.0", "1234567", 0, false},
	}
	for _, c := range cases {
		got, ok := compareVersions(c.a, c.b)
		if got != c.want || ok != c.wantOK {
			t.Fatalf("compareVersions(%s, %s) = %d, %v, 기대 %d, %v", c.a, c.b, got, ok, c.want, c.wantOK)
		}
	}
}
