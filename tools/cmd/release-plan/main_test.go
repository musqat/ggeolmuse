package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"testing"

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
		{"공용 라이브러리는 자바 여섯", []string{"backend/messaging/src/main/java/E.java"}, javaSix},
		{"마이그레이션도 그 서비스로", []string{"backend/user-service/src/main/resources/db/migration/V10__x.sql", "frontend-web/src/App.tsx"}, []string{"user-service", "frontend-web"}},
		{"테스트 · 문서만이면 없음", []string{"backend/user-service/src/test/java/AT.java", "frontend-web/src/App.test.tsx", "backend/chat-service/tests/test_x.py", "README.md"}, []string{}},
		{"CI · 차트만이면 없음", []string{".github/workflows/release.yml", "helm/ggeolmuse/values-prod.yaml"}, []string{}},
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
}

func (f fakeRepo) deployedCommit(e values.Entry) (string, bool) {
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

// 임시 저장소: 버전 · 커밋 태그 · 옮긴 파일 · 한글 경로
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
	release := git("rev-parse", "HEAD")
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
	if got, ok := r.deployedCommit(entries["user-service"]); !ok || got != release {
		t.Fatalf("버전 태그 커밋 %q, 기대 %q", got, release)
	}
	if got, ok := r.deployedCommit(values.Entry{Tag: head[:7]}); !ok || got != head {
		t.Fatalf("커밋 태그 %q, 기대 %q", got, head)
	}
	if !r.isAncestor(release, head) || r.isAncestor(head, release) {
		t.Fatal("조상 판단 틀림")
	}
	files, err := r.changed(release, head)
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"backend/user-service/A.java", "backend/trade-service/A.java", "frontend-web/public/로고.png"} {
		if !slices.Contains(files, want) {
			t.Fatalf("바뀐 파일 %q 에 %q 없음", files, want)
		}
	}
}
