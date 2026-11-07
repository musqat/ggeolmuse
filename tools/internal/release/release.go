// Package release 는 릴리스 봇 커밋 제목을 만들고 읽는다
package release

import (
	"regexp"
	"strings"
)

// 봇 커밋 제목 끝의 빌드한 커밋(chore(release): … (0123456))
var builtSuffix = regexp.MustCompile(`^chore\(release\):.*\(([0-9a-f]{7,40})\)\s*$`)

// Subject 는 봇 커밋 제목(버전 태그면 서비스 뒤에 버전, 끝 괄호에 빌드한 커밋 7자리)
func Subject(services []string, tags map[string]string, built string) string {
	names := make([]string, 0, len(services))
	for _, svc := range services {
		if tag := tags[svc]; strings.Contains(tag, ".") {
			names = append(names, svc+" "+tag)
		} else {
			names = append(names, svc)
		}
	}
	return "chore(release): " + strings.Join(names, " · ") + " (" + built[:min(7, len(built))] + ")"
}

// BuiltCommit 은 봇 커밋 제목 끝 괄호의 빌드한 커밋(봇 커밋 제목이 아니면 false)
func BuiltCommit(subject string) (string, bool) {
	m := builtSuffix.FindStringSubmatch(subject)
	if m == nil {
		return "", false
	}
	return m[1], true
}
