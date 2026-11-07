package release

import "testing"

func TestSubject(t *testing.T) {
	cases := []struct {
		name     string
		services []string
		tags     map[string]string
		want     string
	}{
		{"버전 태그는 서비스 뒤에 버전", []string{"backtest-service", "frontend-web"},
			map[string]string{"backtest-service": "1.8.0", "frontend-web": "8fbaacf"},
			"chore(release): backtest-service 1.8.0 · frontend-web (8fbaacf)"},
		{"SHA 태그만이면 이름만", []string{"user-service"}, map[string]string{"user-service": "8fbaacf"},
			"chore(release): user-service (8fbaacf)"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := Subject(c.services, c.tags, "8fbaacf1234567890"); got != c.want {
				t.Fatalf("제목 %q, 기대 %q", got, c.want)
			}
		})
	}
}

func TestBuiltCommit(t *testing.T) {
	built, ok := BuiltCommit(Subject([]string{"user-service"}, map[string]string{"user-service": "1.8.0"}, "0123456789"))
	if !ok || built != "0123456" {
		t.Fatalf("만든 제목에서 읽은 커밋 %q, %v", built, ok)
	}
	for _, subject := range []string{
		"docs: 값 정리 (0123456)",
		"chore(release): backtest-service 를 0111dae 로",
		"Merge pull request #373 from musqat/feat/idempotency-key",
	} {
		if got, ok := BuiltCommit(subject); ok {
			t.Fatalf("%q 에서 커밋 %q 를 읽음", subject, got)
		}
	}
}
