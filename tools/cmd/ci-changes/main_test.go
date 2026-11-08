package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

func TestPick(t *testing.T) {
	cases := []struct {
		name  string
		push  bool
		files []string
		want  changes
	}{
		{"push 는 전부", true, nil, changes{javaServices, true, true, true}},
		{"서비스 한 곳만 바뀐 PR", false, []string{"backend/user-service/src/main/java/A.java"},
			changes{[]string{"user-service"}, false, false, false}},
		{"테스트 파일도 그 서비스를 빌드(CI 는 테스트를 돌림)", false, []string{"backend/trade-service/src/test/java/T.java"},
			changes{[]string{"trade-service"}, false, false, false}},
		{"공용 모듈이면 자바 서비스 전부", false, []string{"backend/ggeolmuse-common/src/main/java/C.java"},
			changes{javaServices, false, false, false}},
		{"BOM · messaging · backend/pom.xml 도", false, []string{"backend/ggeolmuse-bom/pom.xml", "backend/messaging/x", "backend/pom.xml"},
			changes{javaServices, false, false, false}},
		{"서비스 pom 은 그 서비스만", false, []string{"backend/user-service/pom.xml"}, changes{[]string{"user-service"}, false, false, false}},
		{"CI 설정이면 전부", false, []string{".github/workflows/ci.yml"}, changes{javaServices, true, true, true}},
		{"공용 액션이면 전부", false, []string{".github/actions/trivy-image-scan/action.yml"}, changes{javaServices, true, true, true}},
		{"메이븐 설정이면 전부", false, []string{".mvn/ci-settings.xml"}, changes{javaServices, true, true, true}},
		{"이 명령이 바뀌면 전부", false, []string{"tools/cmd/ci-changes/main.go"}, changes{javaServices, true, true, true}},
		{"다른 워크플로는 전부가 아님", false, []string{".github/workflows/release.yml"}, changes{[]string{}, false, false, false}},
		{"프론트만", false, []string{"frontend-web/src/App.tsx"}, changes{[]string{}, true, false, false}},
		{"채팅만", false, []string{"backend/chat-service/app/main.py"}, changes{[]string{}, false, true, false}},
		{"문서만", false, []string{"docs/DEPLOY.md", "README.md"}, changes{[]string{}, false, false, false}},
		{"PR 에 바뀐 파일이 없으면 아무것도", false, nil, changes{[]string{}, false, false, false}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := pick(c.push, c.files)
			if !slices.Equal(got.services, c.want.services) || got.frontend != c.want.frontend ||
				got.chat != c.want.chat || got.all != c.want.all {
				t.Fatalf("고른 것 %+v, 기대 %+v", got, c.want)
			}
		})
	}
}

// GITHUB_OUTPUT 에 쓰는 세 줄(서비스는 JSON 배열, 빈 배열도 [])
func TestWrite(t *testing.T) {
	var out strings.Builder
	write(&out, changes{services: []string{"config-server", "user-service"}, frontend: true})
	if want := "services=[\"config-server\",\"user-service\"]\nfrontend=true\nchat=false\n"; out.String() != want {
		t.Fatalf("출력 %q, 기대 %q", out.String(), want)
	}
	out.Reset()
	write(&out, pick(false, nil))
	if want := "services=[]\nfrontend=false\nchat=false\n"; out.String() != want {
		t.Fatalf("빈 출력 %q, 기대 %q", out.String(), want)
	}
}

// 임시 저장소: 갈라진 뒤 PR 쪽에서 바뀐 파일만(master 쪽 변경은 빼고), 한글 경로는 따옴표 없이
func TestChangedFiles(t *testing.T) {
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
	put := func(path string) {
		full := filepath.Join(dir, filepath.FromSlash(path))
		if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(full, []byte(path), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	git("init", "-q")
	git("config", "user.name", "t")
	git("config", "user.email", "t@example.com")
	put("README.md")
	git("add", "-A")
	git("commit", "-qm", "base")
	base := git("rev-parse", "HEAD")
	git("checkout", "-q", "-b", "pr")
	put("frontend-web/public/로고.png")
	put("backend/user-service/A.java")
	git("add", "-A")
	git("commit", "-qm", "pr")
	head := git("rev-parse", "HEAD")
	git("checkout", "-q", base)
	put("backend/trade-service/T.java")
	git("add", "-A")
	git("commit", "-qm", "master moved")
	master := git("rev-parse", "HEAD")

	files, err := changedFiles(dir, master, head)
	if err != nil {
		t.Fatal(err)
	}
	slices.Sort(files)
	if want := []string{"backend/user-service/A.java", "frontend-web/public/로고.png"}; !slices.Equal(files, want) {
		t.Fatalf("바뀐 파일 %q, 기대 %q", files, want)
	}
	if got := pick(false, files); !got.frontend || !slices.Equal(got.services, []string{"user-service"}) {
		t.Fatalf("고른 것 %+v", got)
	}
}
