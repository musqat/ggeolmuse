package main

import (
	"fmt"
	"regexp"
	"slices"
	"strconv"
	"strings"
)

// PR 본문에서 릴리스할 서비스를 고르는 칸 제목(앞뒤 # · 공백을 걷은 글)
const picksTitle = "빌드할 서비스"

// ATX 제목 줄(# 하나 ~ 여섯, 닫는 # 포함)
var headingLine = regexp.MustCompile(`^(#{1,6})\s+(.*?)\s*#*\s*$`)

// 체크 칸 줄(- · * · + · 숫자. 글머리, 칸 안 한 글자, 서비스, 뒤는 버전 자리)
var pickLine = regexp.MustCompile(`^(?:[-*+]|[0-9]+\.)\s*\[(.)\]\s*(\S+)\s*(.*)$`)

// 한 줄 안의 HTML 주석(화면에 보이지 않는 글)
var htmlComment = regexp.MustCompile(`<!--.*?-->`)

// 코드 블록 펜스(``` · ~~~ 셋 이상, 뒤는 info)
var fenceLine = regexp.MustCompile("^(`{3,}|~{3,})(.*)$")

// 줄 머리의 인용 · 목록 표지
var containerMarks = regexp.MustCompile(`^(?:>\s*|(?:[-*+]|[0-9]+[.)])\s+)+`)

// 앞자리 0 · v 없는 세 자리 버전
var versionPattern = regexp.MustCompile(`^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$`)

// 운영 태그 쪽 버전(앞자리 0 도 받음)
var deployedVersion = regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`)

// parsePicks 는 빌드할 서비스 칸에서 체크한 서비스와 버전(빈 값이면 SHA 7자리), 칸을 찾았는지
func parsePicks(body string) (map[string]string, bool, error) {
	picks := map[string]string{}
	found, inSection, inComment, inList, commentQuote, fenceTitle := false, false, false, false, false, false
	fence, commentCol := "", 0
	for _, raw := range strings.Split(strings.ReplaceAll(body, "\r\n", "\n"), "\n") {
		line := strings.TrimSpace(raw)
		// 목록 · 인용 안에서 연 주석은 닫히기 전에 덜 들여 쓴 줄이 오면 항목이 끝나 GitHub 화면에서 뒤가 다 숨겨짐
		if inComment {
			if line != "" && indentOf(raw) < commentCol && !(commentQuote && strings.HasPrefix(line, ">")) {
				return nil, false, fmt.Errorf("목록 · 인용 안에서 연 여러 줄 주석이 항목 밖까지 이어짐(항목 안에서 닫음): %q", line)
			}
			inComment = !strings.Contains(line, "-->")
			continue
		}
		// 펜스는 같은 글자 · 같거나 긴 길이 · 뒤에 글 없는 줄에서만 닫힘, 백틱 info 에 백틱이 있으면 펜스 아님
		if f := fenceLine.FindStringSubmatch(line); fence != "" {
			if f != nil && f[1][0] == fence[0] && len(f[1]) >= len(fence) && strings.TrimSpace(f[2]) == "" {
				fence, fenceTitle = "", false
			} else if h := headingLine.FindStringSubmatch(line); h != nil && strings.Join(strings.Fields(h[2]), " ") == picksTitle {
				fenceTitle = true
			}
			continue
		} else if f != nil && !(f[1][0] == '`' && strings.Contains(f[2], "`")) {
			fence = f[1]
			continue
		}
		// 줄 머리에서 여는 주석은 --> 가 있는 줄까지 숨김(GitHub 의 HTML 블록과 같음), 목록 · 인용 안이면 내용 열을 기억
		if rest := containerMarks.ReplaceAllString(line, ""); strings.HasPrefix(rest, "<!--") {
			inComment = !strings.Contains(rest, "-->")
			commentCol, commentQuote = 0, strings.HasPrefix(line, ">")
			if rest != line || (indentOf(raw) > 0 && inList) {
				commentCol = strings.Index(raw, "<!--")
			}
			continue
		}
		if line != "" {
			inList = containerMarks.MatchString(line) || (indentOf(raw) > 0 && inList)
		}
		line = strings.TrimSpace(htmlComment.ReplaceAllString(line, ""))
		if h := headingLine.FindStringSubmatch(line); h != nil {
			inSection = len(h[1]) >= 2 && strings.Join(strings.Fields(h[2]), " ") == picksTitle
			if inSection && found {
				return nil, false, fmt.Errorf("빌드할 서비스 칸이 두 번")
			}
			found = found || inSection
			continue
		}
		if !inSection {
			continue
		}
		m := pickLine.FindStringSubmatch(line)
		if m == nil || m[1] == " " {
			continue
		}
		if m[1] != "x" && m[1] != "X" {
			return nil, false, fmt.Errorf("빌드할 서비스 칸의 체크 칸은 [x] 나 [ ]: %q", line)
		}
		svc, version := m[2], strings.TrimSpace(m[3])
		if !slices.Contains(services, svc) {
			return nil, false, fmt.Errorf("빌드할 서비스 칸에 모르는 서비스 %q(배포하는 서비스: %s)", svc, strings.Join(services, " · "))
		}
		if version != "" && !versionPattern.MatchString(version) {
			return nil, false, fmt.Errorf("%s 의 버전 모양이 아님(1.8.0 처럼): %q", svc, version)
		}
		if _, dup := picks[svc]; dup {
			return nil, false, fmt.Errorf("빌드할 서비스 칸에 %s 가 두 번", svc)
		}
		picks[svc] = version
	}
	if fence != "" && fenceTitle {
		return nil, false, fmt.Errorf("닫히지 않은 코드 블록 안에 빌드할 서비스 칸 제목이 있음(코드 블록을 닫음)")
	}
	return picks, found, nil
}

// 줄 머리 공백 수
func indentOf(s string) int {
	return len(s) - len(strings.TrimLeft(s, " \t"))
}

// parseList 는 손 릴리스의 쉼표 목록(서비스, 뒤에 버전)을 빌드할 서비스 칸과 같은 검사로 읽음
func parseList(list string) (map[string]string, error) {
	if strings.ContainsAny(list, "\r\n") {
		return nil, fmt.Errorf("-picks 는 한 줄(쉼표로 나눔)")
	}
	var body strings.Builder
	body.WriteString("## " + picksTitle + "\n")
	n := 0
	for _, item := range strings.Split(list, ",") {
		if item = strings.TrimSpace(item); item != "" {
			body.WriteString("- [x] " + item + "\n")
			n++
		}
	}
	if n == 0 {
		return nil, fmt.Errorf("-picks 가 비어 있음")
	}
	picks, _, err := parsePicks(body.String())
	return picks, err
}

// compareVersions 는 a 가 b 보다 높으면 1, 같으면 0, 낮으면 -1(둘 중 하나라도 버전이 아니면 false)
func compareVersions(a, b string) (int, bool) {
	if !deployedVersion.MatchString(a) || !deployedVersion.MatchString(b) {
		return 0, false
	}
	pa, pb := strings.Split(a, "."), strings.Split(b, ".")
	for i := range 3 {
		x, _ := strconv.Atoi(pa[i])
		y, _ := strconv.Atoi(pb[i])
		if x != y {
			if x > y {
				return 1, true
			}
			return -1, true
		}
	}
	return 0, true
}
