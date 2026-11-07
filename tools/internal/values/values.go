// Package values 는 운영 값 파일의 이미지 태그 줄을 찾고 바꾼다
package values

import (
	"fmt"
	"regexp"
	"strings"
)

// Keys 는 서비스 → 값 파일 키 경로
var Keys = map[string][]string{
	"config-server":       {"configServer"},
	"gateway-server":      {"gatewayServer"},
	"user-service":        {"services", "userService"},
	"trade-service":       {"services", "tradeService"},
	"market-data-service": {"services", "marketDataService"},
	"backtest-service":    {"services", "backtestService"},
	"chat-service":        {"services", "chatService"},
	"frontend-web":        {"frontend"},
}

// TagPattern 은 커밋 SHA 7자리 또는 버전
var TagPattern = regexp.MustCompile(`^([0-9a-f]{7}|[0-9]+\.[0-9]+\.[0-9]+)$`)

var tagLine = regexp.MustCompile(`^(\s+)tag:\s*(\S.*)$`)

// Entry 는 지금 태그와 줄 번호(1부터)
type Entry struct {
	Tag  string
	Line int
}

// Find 는 서비스별 지금 태그(값 파일에 없는 서비스는 뺌)
func Find(text string) (map[string]Entry, error) {
	lines := strings.Split(text, "\n")
	entries := map[string]Entry{}
	for svc, path := range Keys {
		i, err := tagIndex(lines, path)
		if err != nil {
			continue
		}
		m := tagLine.FindStringSubmatch(strings.TrimRight(lines[i], "\r"))
		entries[svc] = Entry{Tag: strings.Trim(strings.TrimSpace(m[2]), `"'`), Line: i + 1}
	}
	if len(entries) == 0 {
		return nil, fmt.Errorf("값 파일에 서비스 태그 없음")
	}
	return entries, nil
}

// SetTags 는 서비스들의 태그 줄만 바꾼 값 파일
func SetTags(text, tag string, services []string) (string, error) {
	if !TagPattern.MatchString(tag) {
		return "", fmt.Errorf("태그 모양 아님: %q", tag)
	}
	lines := strings.Split(text, "\n")
	for _, svc := range services {
		path, ok := Keys[svc]
		if !ok {
			return "", fmt.Errorf("모르는 서비스: %s", svc)
		}
		i, err := tagIndex(lines, path)
		if err != nil {
			return "", fmt.Errorf("%s: %w", svc, err)
		}
		m := tagLine.FindStringSubmatch(lines[i])
		cr := ""
		if strings.HasSuffix(lines[i], "\r") {
			cr = "\r"
		}
		lines[i] = m[1] + `tag: "` + tag + `"` + cr
	}
	return strings.Join(lines, "\n"), nil
}

// 키 경로 아래 image.tag 줄 번호
func tagIndex(lines []string, path []string) (int, error) {
	start, indent := 0, 0
	end := len(lines)
	for _, key := range path {
		found := -1
		for i := start; i < end; i++ {
			if strings.TrimRight(lines[i], "\r") == strings.Repeat(" ", indent)+key+":" {
				found = i
				break
			}
		}
		if found < 0 {
			return 0, fmt.Errorf("키 %s 없음", strings.Join(path, "."))
		}
		start, end = found+1, blockEnd(lines, found, indent)
		indent += 2
	}
	image := -1
	for i := start; i < end; i++ {
		if strings.TrimRight(lines[i], "\r") == strings.Repeat(" ", indent)+"image:" {
			image = i
			break
		}
	}
	if image < 0 {
		return 0, fmt.Errorf("%s.image 없음", strings.Join(path, "."))
	}
	for i := image + 1; i < blockEnd(lines, image, indent); i++ {
		if strings.HasPrefix(lines[i], strings.Repeat(" ", indent+2)+"tag:") && tagLine.MatchString(strings.TrimRight(lines[i], "\r")) {
			return i, nil
		}
	}
	return 0, fmt.Errorf("%s.image.tag 없음", strings.Join(path, "."))
}

// 블록이 끝나는 줄(빈 줄 · 주석은 건너뜀)
func blockEnd(lines []string, at, indent int) int {
	for i := at + 1; i < len(lines); i++ {
		l := strings.TrimRight(lines[i], "\r")
		t := strings.TrimLeft(l, " ")
		if t == "" || strings.HasPrefix(t, "#") {
			continue
		}
		if len(l)-len(t) <= indent {
			return i
		}
	}
	return len(lines)
}
