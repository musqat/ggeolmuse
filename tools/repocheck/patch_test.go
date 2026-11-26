package repocheck

import (
	"regexp"
	"strings"
	"testing"
)

var (
	stageHead   = regexp.MustCompile(`(?m)^FROM\s`)
	fullUpgrade = regexp.MustCompile(`(?m)^RUN (?:apk upgrade|apt-get update \\\n\s+&& apt-get upgrade -y)`)
	buildStep   = regexp.MustCompile(`(?m)^ +(?:- )?uses: docker/build-push-action@`)
	stepHead    = regexp.MustCompile(`(?m)^ {6}- `)
)

const patchDateArg = "PATCH_DATE=${{ steps.patch-date.outputs.date }}"

// Dockerfile 의 마지막 스테이지(런타임)
func runtimeStage(dockerfile string) string {
	heads := stageHead.FindAllStringIndex(dockerfile, -1)
	if len(heads) == 0 {
		return ""
	}
	return dockerfile[heads[len(heads)-1][0]:]
}

func TestRuntimeStagesUpgradeAllPackagesAfterPatchDate(t *testing.T) {
	for _, path := range repoFiles(t, ".", func(name string) bool { return name == "Dockerfile" }) {
		stage := runtimeStage(readRepoFile(t, path))
		arg := strings.Index(stage, "\nARG PATCH_DATE\n")
		upgrade := fullUpgrade.FindStringIndex(stage)
		switch {
		case upgrade == nil:
			t.Errorf("%s: 런타임 스테이지에 패키지 전체 올리기 없음", path)
		case arg < 0 || arg > upgrade[0]:
			t.Errorf("%s: 패키지 올리기 앞에 ARG PATCH_DATE 없음(빌드 캐시가 옛 패치 레이어를 씀)", path)
		}
	}
}

func TestImageBuildsPassPatchDate(t *testing.T) {
	for file, jobs := range map[string][]string{
		"ci.yml":      {"build-services", "build-frontend", "scan-images"},
		"release.yml": {"build"},
	} {
		wf := workflow(t, file)
		for _, name := range jobs {
			body := job(t, wf, name)
			steps := buildStep.FindAllStringIndex(body, -1)
			if len(steps) == 0 {
				t.Errorf("%s %s: 이미지 빌드 단계 없음", file, name)
			}
			mustContain(t, file+" "+name, body, "id: patch-date\n")
			for _, at := range steps {
				step := body[at[0]:]
				if next := stepHead.FindStringIndex(step[1:]); next != nil {
					step = step[:next[0]+1]
				}
				if !strings.Contains(step, patchDateArg) {
					t.Errorf("%s %s: 이미지 빌드가 %q 를 넘기지 않음", file, name, patchDateArg)
				}
			}
		}
	}
}
