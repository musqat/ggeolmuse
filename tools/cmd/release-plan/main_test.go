package main

import (
	"maps"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/musqat/ggeolmuse/tools/internal/release"
	"github.com/musqat/ggeolmuse/tools/internal/values"
)

var javaSix = services[:6]

func TestDecide(t *testing.T) {
	cases := []struct {
		name  string
		files []string
		want  []string
	}{
		{"서비스 하나의 코드", []string{"backend/user-service/src/main/java/A.java"}, []string{"user-service"}},
		{"공용 라이브러리: 자바 여섯", []string{"backend/messaging/src/main/java/E.java"}, javaSix},
		{"lombok 설정: 자바 여섯", []string{"backend/lombok.config"}, javaSix},
		{"user-service 마이그레이션: user-service", []string{"backend/user-service/src/main/resources/db/migration/V10__x.sql", "frontend-web/src/App.tsx"}, []string{"user-service", "frontend-web"}},
		{"테스트 · 문서만: 없음", []string{"backend/user-service/src/test/java/AT.java", "frontend-web/src/App.test.tsx", "backend/chat-service/tests/test_x.py", "README.md"}, []string{}},
		{"CI · 차트만: 없음", []string{".github/workflows/release.yml", "helm/ggeolmuse/values-prod.yaml"}, []string{}},
		{"chat 과 프론트", []string{"backend/chat-service/app/main.py", "frontend-web/package.json"}, []string{"chat-service", "frontend-web"}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := decide(c.files); !slices.Equal(got, c.want) {
				t.Fatalf("고른 서비스 %v, 기대 %v", got, c.want)
			}
		})
	}
}

func TestWrite(t *testing.T) {
	var out strings.Builder
	write(&out, []string{})
	write(&out, []string{"user-service", "chat-service"})
	want := "services=[]\nservices=[\"user-service\",\"chat-service\"]\n"
	if out.String() != want {
		t.Fatalf("출력 %q, 기대 %q", out.String(), want)
	}
}

// 가짜 저장소
type fakeRepo struct {
	commits  map[string]string
	ancestor map[string]bool
	files    map[string][]string
	highest  map[string]string
}

func (f fakeRepo) highestVersions() (map[string]string, error) { return f.highest, nil }

func (f fakeRepo) deployedCommit(_ string, e values.Entry) (string, bool) {
	c, ok := f.commits[e.Tag]
	return c, ok
}

func (f fakeRepo) isAncestor(base, _ string) bool { return f.ancestor[base] }

func (f fakeRepo) changed(base, _ string) ([]string, error) { return f.files[base], nil }

// user · trade · frontend 는 커밋 태그, 나머지는 1.7.0
func planFixture() (map[string]values.Entry, fakeRepo) {
	entries := map[string]values.Entry{}
	for _, s := range services {
		entries[s] = values.Entry{Tag: "1.7.0", Line: 1}
	}
	entries["user-service"] = values.Entry{Tag: "aaaaaaa", Line: 2}
	entries["trade-service"] = values.Entry{Tag: "bbbbbbb", Line: 3}
	entries["frontend-web"] = values.Entry{Tag: "ccccccc", Line: 4}
	return entries, fakeRepo{
		commits:  map[string]string{"1.7.0": "rel", "aaaaaaa": "a", "bbbbbbb": "b", "ccccccc": "c"},
		ancestor: map[string]bool{"rel": true, "a": true, "b": false, "c": true},
		files: map[string][]string{
			"rel": {"backend/chat-service/app/main.py"},
			"a":   {"backend/user-service/src/main/java/A.java", "backend/chat-service/app/main.py"},
			"c":   {"README.md"},
		},
		highest: map[string]string{"user-service": "1.7.0", "chat-service": "1.7.0", "frontend-web": "1.7.0"},
	}
}

// 바뀐 서비스는 user · chat(planFixture), 커밋 태그는 head 앞 7자리
func TestPlanPicks(t *testing.T) {
	entries, repo := planFixture()
	head := "0123456789abcdef"
	cases := []struct {
		name  string
		picks map[string]string
		want  releasePlan
	}{
		{"user-service 체크: user-service 배포 · chat-service 보류", map[string]string{"user-service": ""},
			releasePlan{services: []string{"user-service"}, tags: map[string]string{"user-service": "0123456"},
				held: []string{"chat-service"}, unchanged: []string{}}},
		{"체크 없음: 배포 없음", map[string]string{},
			releasePlan{services: []string{}, tags: map[string]string{}, held: []string{"user-service", "chat-service"}, unchanged: []string{}}},
		{"안 바뀐 서비스 체크: 건너뜀(낮은 버전도 오류 없음)", map[string]string{"frontend-web": "1.0.0", "chat-service": "1.8.0"},
			releasePlan{services: []string{"chat-service"}, tags: map[string]string{"chat-service": "1.8.0"},
				held: []string{"user-service"}, unchanged: []string{"frontend-web"}}},
		{"운영 커밋 태그 · 이력 최고 버전 초과: 통과", map[string]string{"user-service": "1.7.1"},
			releasePlan{services: []string{"user-service"}, tags: map[string]string{"user-service": "1.7.1"},
				held: []string{"chat-service"}, unchanged: []string{}}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, err := planPicks(entries, head, repo, c.picks)
			if err != nil {
				t.Fatal(err)
			}
			if !slices.Equal(got.services, c.want.services) || !maps.Equal(got.tags, c.want.tags) ||
				!slices.Equal(got.held, c.want.held) || !slices.Equal(got.unchanged, c.want.unchanged) {
				t.Fatalf("계획 %+v, 기대 %+v", got, c.want)
			}
		})
	}
}

func TestPlanPicksVersionFloor(t *testing.T) {
	entries, repo := planFixture()
	for _, version := range []string{"1.7.0", "1.6.9"} {
		_, err := planPicks(entries, "0123456789abcdef", repo, map[string]string{"user-service": version})
		if err == nil || !strings.Contains(err.Error(), "이전 최고 버전 1.7.0") {
			t.Fatalf("%s: 오류 %v, 기대 이전 최고 버전 1.7.0", version, err)
		}
	}
	noHistory, _ := planFixture()
	repo.highest = map[string]string{}
	if _, err := planPicks(noHistory, "0123456789abcdef", repo, map[string]string{"user-service": "0.1.0"}); err != nil {
		t.Fatalf("이력에 버전이 없으면 하한 없음: %v", err)
	}
}

// 배포 직전 다시 볼 때의 버전 하한(커밋 태그 · 태그 없는 서비스는 안 봄)
func TestCheckFloors(t *testing.T) {
	_, repo := planFixture()
	tags := map[string]string{"user-service": "1.7.0", "chat-service": "0123456"}
	err := checkFloors(repo, []string{"user-service", "chat-service"}, tags)
	if err == nil || !strings.Contains(err.Error(), "user-service 버전 1.7.0") {
		t.Fatalf("오류 %v, 기대에 user-service 버전 1.7.0", err)
	}
	if err := checkFloors(repo, []string{"chat-service", "frontend-web"}, tags); err != nil {
		t.Fatalf("커밋 태그 · 태그 없는 서비스는 하한 없음: %v", err)
	}
	if err := checkFloors(repo, []string{"user-service"}, map[string]string{"user-service": "1.7.1"}); err != nil {
		t.Fatalf("가장 높은 버전보다 높으면 됨: %v", err)
	}
}

// 다시 고른 서비스만 하한을 봄(안 바뀐 frontend 는 버전이 낮아도 통과)
func TestPlanStill(t *testing.T) {
	entries, repo := planFixture()
	only := []string{"user-service", "frontend-web"}
	if _, err := planStill(entries, "head", repo, only, map[string]string{"user-service": "1.7.0"}); err == nil {
		t.Fatal("가장 높은 버전 이하인데 오류 없음")
	}
	got, err := planStill(entries, "head", repo, only, map[string]string{"user-service": "1.8.0", "frontend-web": "1.0.0"})
	if err != nil || !slices.Equal(got, []string{"user-service"}) {
		t.Fatalf("고른 서비스 %v · 오류 %v, 기대 [user-service]", got, err)
	}
	if got, err := planStill(entries, "head", repo, only, nil); err != nil || !slices.Equal(got, []string{"user-service"}) {
		t.Fatalf("태그 없이 고른 서비스 %v · 오류 %v", got, err)
	}
}

func TestPlanPicksHeadMustBeCommit(t *testing.T) {
	entries, repo := planFixture()
	_, err := planPicks(entries, "origin/master", repo, map[string]string{"user-service": ""})
	if err == nil || !strings.Contains(err.Error(), "커밋 SHA") {
		t.Fatalf("오류 %v, 기대에 커밋 SHA", err)
	}
}

func TestWritePlanEmpty(t *testing.T) {
	entries, repo := planFixture()
	p, err := planPicks(entries, "0123456789abcdef", repo, map[string]string{})
	if err != nil {
		t.Fatal(err)
	}
	var out strings.Builder
	writePlan(&out, p)
	want := "services=[]\ntags={}\nheld=[\"user-service\",\"chat-service\"]\nunchanged=[]\n"
	if out.String() != want {
		t.Fatalf("출력 %q, 기대 %q", out.String(), want)
	}
}

func TestWritePlan(t *testing.T) {
	var out strings.Builder
	writePlan(&out, releasePlan{
		services: []string{"user-service", "frontend-web"}, tags: map[string]string{"user-service": "1.8.0", "frontend-web": "0123456"},
		held: []string{"chat-service"}, unchanged: []string{},
	})
	want := "services=[\"user-service\",\"frontend-web\"]\n" +
		"tags={\"frontend-web\":\"0123456\",\"user-service\":\"1.8.0\"}\n" +
		"held=[\"chat-service\"]\n" +
		"unchanged=[]\n"
	if out.String() != want {
		t.Fatalf("출력 %q, 기대 %q", out.String(), want)
	}
}

func TestPlan(t *testing.T) {
	entries, repo := planFixture()
	unknown, _ := planFixture()
	unknown["gateway-server"] = values.Entry{Tag: "9.9.9", Line: 5}
	cases := []struct {
		name    string
		entries map[string]values.Entry
		only    []string
		want    []string
	}{
		// trade 는 더 새 커밋이 나감, frontend 는 문서만
		{"운영 태그부터 바뀐 것만", entries, nil, []string{"user-service", "chat-service"}},
		{"only 로 좁힘", entries, []string{"user-service", "trade-service"}, []string{"user-service"}},
		{"모르는 태그는 다시 빌드", unknown, []string{"gateway-server"}, []string{"gateway-server"}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, err := plan(c.entries, "head", repo, c.only)
			if err != nil {
				t.Fatal(err)
			}
			if !slices.Equal(got, c.want) {
				t.Fatalf("고른 서비스 %v, 기대 %v", got, c.want)
			}
		})
	}
}

// 임시 저장소: 버전 · 커밋 태그 · 옮긴 파일 · 한글 경로 · 이력의 가장 높은 버전 · 봇 커밋 괄호 · 되돌린 버전 · 본문 파일로 고른 계획
func TestGitRepo(t *testing.T) {
	if _, err := exec.LookPath("git"); err != nil {
		t.Skip("git 없음")
	}
	dir := t.TempDir()
	git := func(args ...string) string {
		out, err := exec.Command("git", append([]string{"-C", dir}, args...)...).CombinedOutput()
		if err != nil {
			t.Fatalf("git %v: %v\n%s", args, err, out)
		}
		return strings.TrimSpace(string(out))
	}
	put := func(path, text string) {
		full := filepath.Join(dir, filepath.FromSlash(path))
		if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(full, []byte(text), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	git("init", "-q")
	git("config", "user.name", "t")
	git("config", "user.email", "t@example.com")
	put(valuesPath, "services:\n  userService:\n    image:\n      tag: \"1.6.3\"\n")
	put("backend/user-service/A.java", "a")
	git("add", "-A")
	git("commit", "-qm", "old")
	put(valuesPath, "services:\n  userService:\n    image:\n      tag: \"1.7.0\"\n")
	git("commit", "-qam", "release")
	released := git("rev-parse", "HEAD")
	if err := os.MkdirAll(filepath.Join(dir, "backend", "trade-service"), 0o755); err != nil {
		t.Fatal(err)
	}
	git("mv", "backend/user-service/A.java", "backend/trade-service/A.java")
	put("frontend-web/public/로고.png", "png")
	git("add", "-A")
	git("commit", "-qm", "move")
	head := git("rev-parse", "HEAD")

	r := gitRepo{dir: dir, rev: "HEAD"}
	text, err := r.show(valuesPath)
	if err != nil {
		t.Fatal(err)
	}
	entries, err := values.Find(text)
	if err != nil {
		t.Fatal(err)
	}
	if got, ok := r.deployedCommit("user-service", entries["user-service"]); !ok || got != released {
		t.Fatalf("버전 태그 커밋 %q, 기대 %q", got, released)
	}
	if got, ok := r.deployedCommit("user-service", values.Entry{Tag: head[:7]}); !ok || got != head {
		t.Fatalf("커밋 태그 %q, 기대 %q", got, head)
	}
	if !r.isAncestor(released, head) || r.isAncestor(head, released) {
		t.Fatal("조상 판단 틀림")
	}
	files, err := r.changed(released, head)
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"backend/user-service/A.java", "backend/trade-service/A.java", "frontend-web/public/로고.png"} {
		if !slices.Contains(files, want) {
			t.Fatalf("바뀐 파일 %q 에 %q 없음", files, want)
		}
	}
	if got, err := r.highestVersions(); err != nil || got["user-service"] != "1.7.0" {
		t.Fatalf("이력의 가장 높은 버전 %v, %v, 기대 1.7.0", got, err)
	}

	// 봇 커밋이 뒤에 머지된 커밋 위에 올라가도 제목 끝 (sha7) 의 커밋이 운영 커밋
	built := head
	put("backend/user-service/B.java", "b")
	git("add", "-A")
	git("commit", "-qm", "merged later")
	put(valuesPath, "services:\n  userService:\n    image:\n      tag: \"1.8.0\"\n")
	git("commit", "-qam", release.Subject([]string{"user-service"}, map[string]string{"user-service": "1.8.0"}, built))
	text, err = r.show(valuesPath)
	if err != nil {
		t.Fatal(err)
	}
	entries, err = values.Find(text)
	if err != nil {
		t.Fatal(err)
	}
	if got, ok := r.deployedCommit("user-service", entries["user-service"]); !ok || got != built {
		t.Fatalf("봇 커밋이 가리키는 커밋 %q, 기대 %q", got, built)
	}
	if got, err := r.highestVersions(); err != nil || got["user-service"] != "1.8.0" {
		t.Fatalf("이력의 가장 높은 버전 %v, %v, 기대 1.8.0", got, err)
	}

	// 봇 커밋을 되돌리면 앞 버전을 처음 낸 커밋, 되돌림을 다시 되돌리면 첫 봇 커밋에 적힌 빌드 커밋
	userEntry := func() values.Entry {
		text, err := r.show(valuesPath)
		if err != nil {
			t.Fatal(err)
		}
		entries, err := values.Find(text)
		if err != nil {
			t.Fatal(err)
		}
		return entries["user-service"]
	}
	git("revert", "--no-edit", "HEAD")
	if got, ok := r.deployedCommit("user-service", userEntry()); !ok || got != released {
		t.Fatalf("되돌린 뒤 운영 커밋 %q, 기대 %q", got, released)
	}
	git("revert", "--no-edit", "HEAD")
	if got, ok := r.deployedCommit("user-service", userEntry()); !ok || got != built {
		t.Fatalf("되돌림을 되돌린 뒤 운영 커밋 %q, 기대 %q", got, built)
	}
	if got, err := r.commitOf("HEAD"); err != nil || got != git("rev-parse", "HEAD") {
		t.Fatalf("HEAD 의 커밋 %q, %v", got, err)
	}

	// 본문 파일로 고른 계획: 운영 커밋(built) 뒤의 user-service 변경 · 칸 없는 본문은 안내만
	t.Chdir(dir)
	pickBody := filepath.Join(t.TempDir(), "pick.md")
	if err := os.WriteFile(pickBody, []byte("## 빌드할 서비스\n- [x] user-service\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	var notes strings.Builder
	picks, err := readBody(pickBody, &notes)
	if err != nil {
		t.Fatal(err)
	}
	p, err := planPicksFromGit("HEAD", "HEAD", picks)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(p.services, []string{"user-service"}) || p.tags["user-service"] != git("rev-parse", "--short=7", "HEAD") {
		t.Fatalf("본문으로 고른 계획 %+v", p)
	}
	oldBody := filepath.Join(t.TempDir(), "old.md")
	if err := os.WriteFile(oldBody, []byte("## 요약\n - 옛 PR\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	picks, err = readBody(oldBody, &notes)
	if err != nil || len(picks) != 0 || !strings.Contains(notes.String(), "빌드할 서비스 칸 없음") {
		t.Fatalf("칸 없는 본문 %v, %v, 안내 %q", picks, err, notes.String())
	}
	// 수동 실행 목록: PR 칸과 같은 계획
	picks, err = parseList("user-service")
	if err != nil {
		t.Fatal(err)
	}
	if p, err = planPicksFromGit("HEAD", "HEAD", picks); err != nil || !slices.Equal(p.services, []string{"user-service"}) {
		t.Fatalf("목록으로 고른 계획 %+v, %v", p, err)
	}

	// 이력의 가장 높은 버전은 지금 태그가 SHA 로 바뀌어도 그대로
	put(valuesPath, "services:\n  userService:\n    image:\n      tag: \""+built[:7]+"\"\n")
	git("commit", "-qam", "chore(release): user-service 를 "+built[:7]+" 로")
	if got, err := r.highestVersions(); err != nil || got["user-service"] != "1.8.0" {
		t.Fatalf("지금 태그가 SHA 일 때 이력의 가장 높은 버전 %v, %v, 기대 1.8.0", got, err)
	}

	// 봇 커밋이 아닌 제목 끝 괄호는 따라가지 않음
	put(valuesPath, "services:\n  userService:\n    image:\n      tag: \"1.9.0\"\n")
	git("commit", "-qam", "docs: 값 정리 ("+built[:7]+")")
	plain := git("rev-parse", "HEAD")
	text, err = r.show(valuesPath)
	if err != nil {
		t.Fatal(err)
	}
	entries, err = values.Find(text)
	if err != nil {
		t.Fatal(err)
	}
	if got, ok := r.deployedCommit("user-service", entries["user-service"]); !ok || got != plain {
		t.Fatalf("봇 커밋 아닌 제목의 운영 커밋 %q, 기대 %q", got, plain)
	}

	// 봇 커밋 위에 올린 PR 커밋의 날짜가 옛날이어도 처음 2.0.0 이 된 커밋은 봇 커밋(이력은 조상부터)
	tagged := func(tag, a, b string) string {
		return "services:\n  userService:\n    image:\n      tag: \"" + tag + "\"\n# " + a + "\n#\n#\n#\n# " + b + "\n"
	}
	put(valuesPath, tagged("2.0.0", "a", "b"))
	git("commit", "-qam", release.Subject([]string{"user-service"}, map[string]string{"user-service": "2.0.0"}, built))
	git("checkout", "-q", "-b", "side")
	put(valuesPath, tagged("2.0.0", "a2", "b"))
	old := exec.Command("git", "-C", dir, "commit", "-qam", "old dated PR")
	old.Env = append(os.Environ(), "GIT_AUTHOR_DATE=2001-01-01T00:00:00", "GIT_COMMITTER_DATE=2001-01-01T00:00:00")
	if out, err := old.CombinedOutput(); err != nil {
		t.Fatalf("옛 날짜 커밋: %v\n%s", err, out)
	}
	git("checkout", "-q", "-")
	put(valuesPath, tagged("2.0.0", "a", "b2"))
	git("commit", "-qam", "values b")
	git("merge", "-q", "--no-ff", "-m", "merge side", "side")
	if got, ok := r.deployedCommit("user-service", userEntry()); !ok || got != built {
		t.Fatalf("옛 날짜 커밋이 섞인 이력의 운영 커밋 %q, 기대 %q", got, built)
	}
}
