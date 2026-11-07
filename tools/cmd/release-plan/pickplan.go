package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"regexp"
	"slices"
	"strings"

	"github.com/musqat/ggeolmuse/tools/internal/release"
	"github.com/musqat/ggeolmuse/tools/internal/values"
)

// 커밋 SHA(7 ~ 40자리)
var commitSHA = regexp.MustCompile(`^[0-9a-f]{7,40}$`)

// releasePlan 은 낼 서비스 · 서비스별 태그 · 바뀌었지만 체크하지 않은 서비스 · 체크했지만 바뀐 것 없는 서비스
type releasePlan struct {
	services  []string
	tags      map[string]string
	held      []string
	unchanged []string
}

// planPicks 는 운영 태그부터 바뀐 서비스 가운데 체크한 것만 내고, 버전이 운영 이력의 가장 높은 버전 이하면 오류
func planPicks(entries map[string]values.Entry, head string, r repo, picks map[string]string) (releasePlan, error) {
	if !commitSHA.MatchString(head) {
		return releasePlan{}, fmt.Errorf("-head 는 커밋 SHA(받은 값 %q)", head)
	}
	changed, err := plan(entries, head, r, nil)
	if err != nil {
		return releasePlan{}, err
	}
	p := releasePlan{services: []string{}, tags: map[string]string{}, held: []string{}, unchanged: []string{}}
	for _, svc := range services {
		_, picked := picks[svc]
		switch {
		case picked && slices.Contains(changed, svc):
			p.services = append(p.services, svc)
		case slices.Contains(changed, svc):
			p.held = append(p.held, svc)
		case picked:
			p.unchanged = append(p.unchanged, svc)
		}
	}
	for _, svc := range p.services {
		if version := picks[svc]; version != "" {
			p.tags[svc] = version
		} else {
			p.tags[svc] = head[:min(7, len(head))]
		}
	}
	if err := checkFloors(r, p.services, p.tags); err != nil {
		return releasePlan{}, err
	}
	return p, nil
}

// checkFloors 는 버전 태그가 운영 값 파일 이력의 가장 높은 버전 이하면 오류(커밋 태그는 안 봄)
func checkFloors(r repo, svcs []string, tags map[string]string) error {
	var highest map[string]string
	for _, svc := range svcs {
		version := tags[svc]
		if !deployedVersion.MatchString(version) {
			continue
		}
		if highest == nil {
			var err error
			if highest, err = r.highestVersions(); err != nil {
				return err
			}
		}
		if floor, ok := highest[svc]; ok {
			if cmp, ok := compareVersions(version, floor); ok && cmp <= 0 {
				return fmt.Errorf("%s 버전 %s: 운영 값 파일 이력의 가장 높은 버전 %s 이하라 낼 수 없음", svc, version, floor)
			}
		}
	}
	return nil
}

// planPicksFromGit 은 머지한 PR 본문 파일의 빌드할 서비스 칸으로 고른 릴리스 계획
func planPicksFromGit(head, rev, bodyPath string, notes io.Writer) (releasePlan, error) {
	raw, err := os.ReadFile(bodyPath)
	if err != nil {
		return releasePlan{}, fmt.Errorf("PR 본문 파일 못 읽음: %w", err)
	}
	picks, found, err := parsePicks(string(raw))
	if err != nil {
		return releasePlan{}, err
	}
	if !found {
		fmt.Fprintln(notes, "빌드할 서비스 칸 없음: 내는 서비스 없음")
	}
	r, entries, err := openRepo(rev)
	if err != nil {
		return releasePlan{}, err
	}
	commit, err := r.commitOf(head)
	if err != nil {
		return releasePlan{}, err
	}
	return planPicks(entries, commit, r, picks)
}

// 브랜치 · 태그 이름도 받는 커밋 SHA(40자리)
func (g gitRepo) commitOf(ref string) (string, error) {
	out, err := g.git("rev-parse", "--verify", "--quiet", ref+"^{commit}")
	if err != nil || out == "" {
		return "", fmt.Errorf("커밋 아님: %q", ref)
	}
	return out, nil
}

// writePlan 은 services · tags · held · unchanged 를 한 줄씩 출력(값은 JSON)
func writePlan(w io.Writer, p releasePlan) {
	for _, kv := range []struct {
		key string
		val any
	}{{"services", p.services}, {"tags", p.tags}, {"held", p.held}, {"unchanged", p.unchanged}} {
		b, _ := json.Marshal(kv.val)
		fmt.Fprintf(w, "%s=%s\n", kv.key, b)
	}
}

// 값 파일 이력의 한 커밋과 그때의 서비스별 태그
type valuesAt struct {
	commit string
	tags   map[string]string
}

// 한 번 읽은 값 파일 이력
type historyMemo struct {
	read bool
	hist []valuesAt
}

// 운영 값 파일 이력(오래된 것부터, 못 읽는 커밋은 뺌)
func (g gitRepo) history() ([]valuesAt, error) {
	if g.memo != nil && g.memo.read {
		return g.memo.hist, nil
	}
	out, err := g.git("log", "--reverse", "--format=%H", g.rev, "--", valuesPath)
	if err != nil {
		return nil, fmt.Errorf("%s 이력 못 읽음: %w", valuesPath, err)
	}
	hist := []valuesAt{}
	for _, commit := range strings.Fields(out) {
		text, err := g.git("show", commit+":"+valuesPath)
		if err != nil {
			continue
		}
		entries, err := values.Find(text)
		if err != nil {
			continue
		}
		tags := make(map[string]string, len(entries))
		for svc, e := range entries {
			tags[svc] = e.Tag
		}
		hist = append(hist, valuesAt{commit: commit, tags: tags})
	}
	if g.memo != nil {
		g.memo.read, g.memo.hist = true, hist
	}
	return hist, nil
}

// 운영 값 파일 이력에서 서비스마다 가장 높은 버전(커밋 태그는 뺌)
func (g gitRepo) highestVersions() (map[string]string, error) {
	hist, err := g.history()
	if err != nil {
		return nil, err
	}
	highest := map[string]string{}
	for _, h := range hist {
		for svc, tag := range h.tags {
			if !deployedVersion.MatchString(tag) {
				continue
			}
			if cur, ok := highest[svc]; !ok {
				highest[svc] = tag
			} else if cmp, _ := compareVersions(tag, cur); cmp > 0 {
				highest[svc] = tag
			}
		}
	}
	return highest, nil
}

// 봇 커밋이면 제목 끝 괄호의 빌드한 커밋, 아니면 그 커밋
func (g gitRepo) builtCommit(commit string) string {
	subject, err := g.git("log", "-1", "--format=%s", commit)
	if err != nil {
		return commit
	}
	short, ok := release.BuiltCommit(subject)
	if !ok {
		return commit
	}
	built, err := g.git("rev-parse", "--verify", "--quiet", short+"^{commit}")
	if err != nil || built == "" {
		return commit
	}
	return built
}
