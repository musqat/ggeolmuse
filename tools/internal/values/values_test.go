package values

import (
	"strings"
	"testing"
)

const sample = `# 운영 덮어쓰기
configServer:
  image:
    tag: "1.7.0"
  env:
    tag: keep

# 비즈니스 서비스
services:
  userService:
    image:
      tag: "1.7.0"
    resources:
      limits:
        memory: 512Mi
  tradeService:
    # 주석
    image:
      tag: "1.7.0"

frontend:
  image:
    tag: "1.7.0"
`

func TestSetTags(t *testing.T) {
	out, err := SetTags(sample, "7c93f2b", []string{"user-service", "frontend-web"})
	if err != nil {
		t.Fatal(err)
	}
	want := strings.Replace(sample, "  userService:\n    image:\n      tag: \"1.7.0\"", "  userService:\n    image:\n      tag: \"7c93f2b\"", 1)
	want = strings.Replace(want, "frontend:\n  image:\n    tag: \"1.7.0\"", "frontend:\n  image:\n    tag: \"7c93f2b\"", 1)
	if out != want {
		t.Fatalf("결과가 다름\n%s", out)
	}
}

func TestSetTagsKeepsOtherBlocks(t *testing.T) {
	out, err := SetTags(sample, "7c93f2b", []string{"trade-service"})
	if err != nil {
		t.Fatal(err)
	}
	if strings.Count(out, `tag: "7c93f2b"`) != 1 || !strings.Contains(out, "    # 주석\n    image:\n      tag: \"7c93f2b\"") || !strings.Contains(out, "tag: keep") {
		t.Fatalf("기대: trade-service 태그만 바뀜\n%s", out)
	}
}

func TestSetTagsErrors(t *testing.T) {
	cases := []struct {
		name, tag string
		services  []string
		want      string
	}{
		{"모르는 서비스", "7c93f2b", []string{"admin-service"}, "모르는 서비스"},
		{"태그 모양", "latest", []string{"user-service"}, "태그 모양 아님"},
		{"값 파일에 없는 서비스", "7c93f2b", []string{"chat-service"}, "키 services.chatService 없음"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			_, err := SetTags(sample, c.tag, c.services)
			if err == nil || !strings.Contains(err.Error(), c.want) {
				t.Fatalf("오류 %v, 기대 %q", err, c.want)
			}
		})
	}
}

func TestFind(t *testing.T) {
	entries, err := Find(sample)
	if err != nil {
		t.Fatal(err)
	}
	if got := entries["trade-service"]; got.Tag != "1.7.0" || got.Line != 19 {
		t.Fatalf("trade-service 태그 · 줄 %+v, 기대 1.7.0 · 19", got)
	}
	if got := entries["frontend-web"]; got.Tag != "1.7.0" || got.Line != 23 {
		t.Fatalf("frontend-web 태그 · 줄 %+v, 기대 1.7.0 · 23", got)
	}
	if _, ok := entries["chat-service"]; ok {
		t.Fatal("값 파일에 없는 서비스가 들어옴")
	}
}
