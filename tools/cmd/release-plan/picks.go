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

// 앞자리 0 · v 없는 세 자리 버전
var versionPattern = regexp.MustCompile(`^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$`)

// 운영 태그 쪽 버전(앞자리 0 도 받음)
var deployedVersion = regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`)

// parsePicks 는 빌드할 서비스 칸에서 체크한 서비스와 버전(빈 값이면 SHA 7자리), 칸을 찾았는지
func parsePicks(body string) (map[string]string, bool, error) {
	picks := map[string]string{}
	found, inSection, inFence, inComment := false, false, false, false
	for _, raw := range strings.Split(strings.ReplaceAll(body, "\r\n", "\n"), "\n") {
		line := strings.TrimSpace(raw)
		if inComment {
			inComment = !strings.Contains(line, "-->")
			continue
		}
		if strings.HasPrefix(line, "```") || strings.HasPrefix(line, "~~~") {
			inFence = !inFence
			continue
		}
		if inFence {
			continue
		}
		// 줄 머리에서 여는 주석은 --> 가 있는 줄까지 숨김(GitHub 의 HTML 블록과 같음)
		if strings.HasPrefix(line, "<!--") {
			inComment = !strings.Contains(line[len("<!--"):], "-->")
			continue
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
	return picks, found, nil
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
