package main

import (
	"maps"
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
		{"버전와 체크 안 한 줄", "## 요약\n - 고친다\n\n## 빌드할 서비스\n- [x] backtest-service 1.8.0\n- [ ] user-service\n",
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

func TestParsePicksErrors(t *testing.T) {
	cases := []struct {
		name string
		body string
		want string
	}{
		{"체크한 모르는 이름은 쓸 수 있는 이름과 함께", "- [x] backtest 1.8.0", "배포하는 서비스: config-server"},
		{"배포하지 않는 모듈", "- [x] ggeolmuse-common", "모르는 서비스"},
		{"버전 두 자리", "- [x] backtest-service 1.8", "버전 모양"},
		{"v 붙은 버전", "- [x] backtest-service v1.8.0", "버전 모양"},
		{"앞에 0 붙은 버전", "- [x] backtest-service 01.8.0", "버전 모양"},
		{"버전 뒤에 글", "- [x] backtest-service 1.8.0 (조건부)", "버전 모양"},
		{"체크 칸 글자가 x 가 아님", "- [o] backtest-service", "체크 칸"},
		{"같은 서비스 두 번", "- [x] user-service\n- [x] user-service 1.8.0", "두 번"},
		{"칸 제목 두 번", "- [x] user-service\n\n## 빌드할 서비스\n- [x] trade-service", "칸이 두 번"},
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
