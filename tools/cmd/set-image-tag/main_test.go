package main

import (
	"strings"
	"testing"
)

const valuesText = "frontend:\n  image:\n    tag: \"1.7.0\"\nservices:\n  userService:\n    image:\n      tag: \"aaaaaaa\"\n  backtestService:\n    image:\n      tag: \"1.7.0\"\n"

func TestApplyTags(t *testing.T) {
	text, subject, err := applyTags(valuesText, []string{"backtest-service", "frontend-web"},
		map[string]string{"backtest-service": "1.8.0", "frontend-web": "0123456", "user-service": "1.9.0"}, "0123456789abcdef")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"frontend:\n  image:\n    tag: \"0123456\"", "backtestService:\n    image:\n      tag: \"1.8.0\"", "userService:\n    image:\n      tag: \"aaaaaaa\""} {
		if !strings.Contains(text, want) {
			t.Fatalf("값 파일에 %q 없음:\n%s", want, text)
		}
	}
	if subject != "chore(release): backtest-service 1.8.0 · frontend-web (0123456)" {
		t.Fatalf("제목 %q", subject)
	}
}

// built 는 16진수 7 ~ 40자리 커밋만
func TestApplyTagsBadBuilt(t *testing.T) {
	tags := map[string]string{"frontend-web": "0123456"}
	for _, built := range []string{"", "012345", "origin/master", "0123456G"} {
		_, _, err := applyTags(valuesText, []string{"frontend-web"}, tags, built)
		if err == nil || !strings.Contains(err.Error(), "-built") {
			t.Fatalf("%q: 오류 %v, 기대에 -built", built, err)
		}
	}
}

func TestApplyTagsMissingTag(t *testing.T) {
	_, _, err := applyTags(valuesText, []string{"user-service"}, map[string]string{"frontend-web": "0123456"}, "0123456789")
	if err == nil || !strings.Contains(err.Error(), "user-service 태그 없음") {
		t.Fatalf("오류 %v", err)
	}
}
