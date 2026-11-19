package repocheck

import (
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"sort"
	"strings"
	"testing"

	"sigs.k8s.io/yaml"
)

var (
	cacheSave = regexp.MustCompile(`- name: [^\n]*\n(?:\s+id: [^\n]*\n)?\s+if: ([^\n]*)\n\s+uses: actions/cache/save@`)
	cacheTo   = regexp.MustCompile(`cache-to: ([^\n]*)`)
	trigger   = regexp.MustCompile(`(?m)^  (\w+):`)
)

// 워크플로 파일 내용
func workflow(t *testing.T, name string) string {
	t.Helper()
	return readRepoFile(t, filepath.Join(".github", "workflows", name))
}

// 들여쓰기 두 칸 뒤 잡 이름 · 주석 · 목록이 오는 줄(다음 잡의 시작)
func isJobHead(line string) bool {
	if len(line) < 3 || !strings.HasPrefix(line, "  ") {
		return false
	}
	c := line[2]
	return c == '#' || c == '-' || c == '_' || ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9')
}

// 잡 하나의 줄(다음 잡 · 주석 머리 전까지)
func job(t *testing.T, wf, name string) string {
	t.Helper()
	lines := strings.SplitAfter(wf, "\n")
	start := slices.Index(lines, "  "+name+":\n")
	if start < 0 {
		t.Fatalf("잡 %s 없음", name)
	}
	var b strings.Builder
	for _, line := range lines[start+1:] {
		if isJobHead(line) {
			break
		}
		b.WriteString(line)
	}
	return b.String()
}

func mustContain(t *testing.T, where, text string, wants ...string) {
	t.Helper()
	for _, want := range wants {
		if !strings.Contains(text, want) {
			t.Errorf("%s 에 %q 없음", where, want)
		}
	}
}

func TestE2EIsACIJobWithTheRequiredCheckName(t *testing.T) {
	if _, err := os.Stat(filepath.Join(repoRoot(t), ".github", "workflows", "e2e.yml")); err == nil {
		t.Error("e2e.yml 이 따로 있음")
	}
	mustContain(t, "e2e 잡", job(t, workflow(t, "ci.yml"), "e2e"), "    name: E2E\n")
}

func TestE2EFollowsTheChangesPick(t *testing.T) {
	ci := workflow(t, "ci.yml")
	mustContain(t, "e2e 잡", job(t, ci, "e2e"), "    needs: changes\n", "needs.changes.outputs.e2e != 'false'")
	mustContain(t, "changes 잡", job(t, ci, "changes"), "e2e: ${{ steps.pick.outputs.e2e }}")
	if strings.Contains(ci, "e2e-changes:") {
		t.Error("e2e-changes 잡이 남아 있음")
	}
}

func TestCICallsReleaseAfterChecksAndE2EOnPush(t *testing.T) {
	mustContain(t, "release 잡", job(t, workflow(t, "ci.yml"), "release"),
		"uses: ./.github/workflows/release.yml", "needs: [ci-result, e2e, release-check]", "github.event_name == 'push'", "secrets: inherit")
}

func TestReleaseOnlyWhenTheMergedPRCheckedAService(t *testing.T) {
	ci := workflow(t, "ci.yml")
	mustContain(t, "release-check 잡", job(t, ci, "release-check"), "go run ./cmd/release-plan -checked")
	mustContain(t, "release 잡", job(t, ci, "release"),
		"needs.release-check.outputs.release == 'true'", "pr: ${{ needs.release-check.outputs.pr }}")
	mustContain(t, "release.yml", workflow(t, "release.yml"), "    inputs:\n      pr:")
}

func TestPRRunsOnlyReadCaches(t *testing.T) {
	ci := workflow(t, "ci.yml")
	if strings.Contains(ci, "uses: actions/cache@") {
		t.Error("읽기 · 저장을 같이 하는 actions/cache 를 씀")
	}
	saves := cacheSave.FindAllStringSubmatch(ci, -1)
	if restores := strings.Count(ci, "uses: actions/cache/restore@"); len(saves) != restores {
		t.Errorf("캐시 저장 %d · 읽기 %d", len(saves), restores)
	}
	for _, s := range saves {
		if !strings.Contains(s[1], "github.event_name == 'push'") {
			t.Errorf("push 가 아니어도 캐시를 저장함: %s", s[1])
		}
	}
	tos := cacheTo.FindAllStringSubmatch(ci, -1)
	if len(tos) == 0 {
		t.Error("빌드 캐시 저장(cache-to) 없음")
	}
	for _, to := range tos {
		if !strings.Contains(to[1], "github.event_name == 'push'") {
			t.Errorf("push 가 아니어도 빌드 캐시를 저장함: %s", to[1])
		}
	}
}

// compose 로 빌드하는 서비스마다 그 서비스 빌드 잡의 캐시를 읽고, 캐시 빌드가 실패하면 compose 빌드로 다시
func TestE2EBuildReadsEachServiceBuildCache(t *testing.T) {
	var compose struct {
		Services map[string]struct {
			Build any `json:"build"`
		} `json:"services"`
	}
	if err := yaml.Unmarshal([]byte(readRepoFile(t, "docker-compose/docker-compose.yml")), &compose); err != nil {
		t.Fatal(err)
	}
	var built []string
	for name, s := range compose.Services {
		if s.Build != nil {
			built = append(built, name)
		}
	}
	sort.Strings(built)
	if len(built) == 0 {
		t.Fatal("compose 에 빌드하는 서비스 없음")
	}
	e2e := job(t, workflow(t, "ci.yml"), "e2e")
	mustContain(t, "e2e 잡", e2e, "uses: docker/bake-action@", "if: steps.bake.outcome == 'failure'", "docker compose build && exit 0")
	for _, name := range built {
		mustContain(t, "e2e 잡", e2e, name+".cache-from=type=gha,scope="+name+"\n")
	}
}

// 리포 검사가 읽는 파일은 tools 모듈 밖이라 Go 테스트 결과 캐시가 바뀐 것을 모름
func TestToolsJobRunsTestsWithoutResultCache(t *testing.T) {
	mustContain(t, "tools 잡", job(t, workflow(t, "ci.yml"), "tools"), "go test -count=1 ./...")
}

func TestE2ERetriesStackStart(t *testing.T) {
	mustContain(t, "e2e 잡", job(t, workflow(t, "ci.yml"), "e2e"), "docker compose up -d && exit 0")
}

func TestRolloutFailsWithoutToken(t *testing.T) {
	rollout := job(t, workflow(t, "release.yml"), "rollout")
	mustContain(t, "rollout 잡", rollout, `echo "::error::환경 release 에 ARGOCD_TOKEN 없음`)
	if strings.Contains(rollout, "확인 건너뜀") {
		t.Error("토큰이 없으면 확인을 건너뜀")
	}
}

func TestReleaseStartsOnlyFromCIOrByHand(t *testing.T) {
	lines := strings.SplitAfter(workflow(t, "release.yml"), "\n")
	start := slices.Index(lines, "on:\n")
	if start < 0 {
		t.Fatal("release.yml 에 on: 없음")
	}
	var on strings.Builder
	for _, line := range lines[start+1:] {
		if line != "" && line[0] != ' ' && line[0] != '\n' {
			break
		}
		on.WriteString(line)
	}
	var triggers []string
	for _, m := range trigger.FindAllStringSubmatch(on.String(), -1) {
		triggers = append(triggers, m[1])
	}
	if want := []string{"workflow_call", "workflow_dispatch"}; !slices.Equal(triggers, want) {
		t.Errorf("release.yml 시작 조건 %v, 기대 %v", triggers, want)
	}
}
