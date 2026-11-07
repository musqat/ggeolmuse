// release-plan 은 다시 빌드 · 배포할 서비스를 고른다
//
//	go run ./cmd/release-plan -head "$SHA" -body pr.md     # 운영 태그부터 바뀐 서비스 가운데 PR 에서 체크한 것 · 태그
//	go run ./cmd/release-plan -head "$SHA"                 # 운영 태그부터 바뀐 서비스
//	git diff --name-only A B | go run ./cmd/release-plan   # 파일 목록으로
package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"os/exec"
	"slices"
	"strings"

	"github.com/musqat/ggeolmuse/tools/internal/values"
)

// 운영 값 파일
const valuesPath = "helm/ggeolmuse/values-prod.yaml"

// 운영 서비스(값 파일 순서)
var services = []string{"config-server", "gateway-server", "user-service", "trade-service", "market-data-service", "backtest-service", "chat-service", "frontend-web"}

// 바뀌면 자바 서비스 전부 다시 빌드
var sharedJava = []string{"backend/ggeolmuse-bom/", "backend/ggeolmuse-common/", "backend/messaging/", "backend/pom.xml"}

func main() {
	head := flag.String("head", "", "릴리스할 커밋")
	rev := flag.String("rev", "origin/master", "값 파일 리비전")
	only := flag.String("only", "", "다시 볼 서비스(JSON 배열)")
	body := flag.String("body", "", "머지한 PR 본문 파일(빌드할 서비스 칸)")
	flag.Parse()
	if *head == "" {
		if *body != "" {
			fail(fmt.Errorf("-body 는 -head 와 같이"))
		}
		write(os.Stdout, decide(readLines(os.Stdin)))
		return
	}
	if *body != "" {
		p, err := planPicksFromGit(*head, *rev, *body, os.Stderr)
		if err != nil {
			fail(err)
		}
		writePlan(os.Stdout, p)
		return
	}
	picked, err := planFromGit(*head, *rev, *only)
	if err != nil {
		fail(err)
	}
	write(os.Stdout, picked)
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, "FAIL ", err)
	os.Exit(1)
}

// 저장소 맨 위 · 값 파일 리비전과 그 리비전의 서비스별 태그
func openRepo(rev string) (gitRepo, map[string]values.Entry, error) {
	top, err := exec.Command("git", "rev-parse", "--show-toplevel").Output()
	if err != nil {
		return gitRepo{}, nil, fmt.Errorf("git 저장소 아님: %w", err)
	}
	r := gitRepo{dir: strings.TrimSpace(string(top)), rev: rev}
	text, err := r.show(valuesPath)
	if err != nil {
		return gitRepo{}, nil, err
	}
	entries, err := values.Find(text)
	if err != nil {
		return gitRepo{}, nil, err
	}
	return r, entries, nil
}

func planFromGit(head, rev, only string) ([]string, error) {
	r, entries, err := openRepo(rev)
	if err != nil {
		return nil, err
	}
	var onlyList []string
	if only != "" {
		if err := json.Unmarshal([]byte(only), &onlyList); err != nil {
			return nil, fmt.Errorf("-only 는 JSON 배열: %w", err)
		}
	}
	return plan(entries, head, r, onlyList)
}

// 운영 커밋 · 조상 · 바뀐 파일 · 이력의 가장 높은 버전
type repo interface {
	deployedCommit(e values.Entry) (string, bool)
	isAncestor(base, head string) bool
	changed(base, head string) ([]string, error)
	highestVersions() (map[string]string, error)
}

// 운영 태그부터 바뀐 서비스(모르는 태그는 다시 빌드, 더 새 커밋이 나갔으면 건너뜀)
func plan(entries map[string]values.Entry, head string, r repo, only []string) ([]string, error) {
	picked := []string{}
	for _, svc := range services {
		if len(only) > 0 && !slices.Contains(only, svc) {
			continue
		}
		e, ok := entries[svc]
		if !ok {
			continue
		}
		base, known := r.deployedCommit(e)
		if !known {
			picked = append(picked, svc)
			continue
		}
		if !r.isAncestor(base, head) {
			continue
		}
		files, err := r.changed(base, head)
		if err != nil {
			return nil, err
		}
		if slices.Contains(decide(files), svc) {
			picked = append(picked, svc)
		}
	}
	return picked, nil
}

// 저장소 경로와 값 파일 리비전
type gitRepo struct {
	dir string
	rev string
}

func (g gitRepo) git(args ...string) (string, error) {
	out, err := exec.Command("git", append([]string{"-C", g.dir}, args...)...).Output()
	return strings.TrimSpace(string(out)), err
}

func (g gitRepo) show(path string) (string, error) {
	out, err := g.git("show", g.rev+":"+path)
	if err != nil {
		return "", fmt.Errorf("%s:%s 못 읽음: %w", g.rev, path, err)
	}
	return out, nil
}

// 커밋 태그는 그 커밋, 버전은 그 줄을 바꾼 커밋(봇 커밋이면 제목 끝 괄호의 빌드한 커밋)
func (g gitRepo) deployedCommit(e values.Entry) (string, bool) {
	if values.TagPattern.MatchString(e.Tag) && !strings.Contains(e.Tag, ".") {
		out, err := g.git("rev-parse", "--verify", "--quiet", e.Tag+"^{commit}")
		return out, err == nil && out != ""
	}
	if e.Line <= 0 {
		return "", false
	}
	out, err := g.git("blame", "--porcelain", "-L", fmt.Sprintf("%d,%d", e.Line, e.Line), g.rev, "--", valuesPath)
	if err != nil || out == "" {
		return "", false
	}
	return g.builtCommit(strings.Fields(out)[0]), true
}

func (g gitRepo) isAncestor(base, head string) bool {
	_, err := g.git("merge-base", "--is-ancestor", base, head)
	return err == nil
}

// 옮긴 파일은 두 경로 다, 한글 경로는 그대로
func (g gitRepo) changed(base, head string) ([]string, error) {
	out, err := g.git("-c", "core.quotePath=false", "diff", "--no-renames", "--name-only", base, head)
	if err != nil {
		return nil, fmt.Errorf("git diff %s %s: %w", base, head, err)
	}
	return readLines(strings.NewReader(out)), nil
}

func readLines(r io.Reader) []string {
	var out []string
	s := bufio.NewScanner(r)
	for s.Scan() {
		if line := strings.TrimSpace(s.Text()); line != "" {
			out = append(out, line)
		}
	}
	return out
}

// 테스트 · 문서
func ignored(f string) bool {
	return strings.Contains(f, "/src/test/") || strings.Contains(f, "/tests/") ||
		strings.Contains(f, ".test.") || strings.HasSuffix(f, ".md")
}

// 자바 서비스
func java(svc string) bool { return svc != "chat-service" && svc != "frontend-web" }

func folder(svc string) string {
	if svc == "frontend-web" {
		return "frontend-web/"
	}
	return "backend/" + svc + "/"
}

func decide(files []string) []string {
	picked := []string{}
	for _, svc := range services {
		if slices.ContainsFunc(files, func(f string) bool {
			if ignored(f) {
				return false
			}
			return strings.HasPrefix(f, folder(svc)) ||
				(java(svc) && slices.ContainsFunc(sharedJava, func(p string) bool { return strings.HasPrefix(f, p) }))
		}) {
			picked = append(picked, svc)
		}
	}
	return picked
}

// services=JSON 배열
func write(w io.Writer, picked []string) {
	b, _ := json.Marshal(picked)
	fmt.Fprintf(w, "services=%s\n", b)
}
