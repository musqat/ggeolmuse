package repocheck

import (
	"fmt"
	"strings"
	"testing"

	"sigs.k8s.io/yaml"
)

// 스캔이 고친 버전 없는 취약점을 빼고, 판정은 남은 CRITICAL 이 있으면 Trivy 종료 코드로 실패
func TestImageScanFailsOnFixableCritical(t *testing.T) {
	action := readRepoFile(t, ".github/actions/trivy-image-scan/action.yml")
	for _, want := range []string{"ignore-unfixed: true", "trivy convert --format table --severity CRITICAL --exit-code 1"} {
		if !strings.Contains(action, want) {
			t.Errorf("이미지 스캔 action 에 %q 없음", want)
		}
	}
	if strings.Contains(action, "python3") {
		t.Error("이미지 스캔 판정이 파이썬 스크립트를 부름")
	}
}

// 스캔 결과를 Security 탭에 올리는 잡은 security-events 쓰기 권한이 있고, 권한이 없는 잡은 올리기를 끔
func TestScanUploadsNeedSecurityEventsWrite(t *testing.T) {
	mustContain(t, "trivy-image-scan", readRepoFile(t, ".github/actions/trivy-image-scan/action.yml"),
		"  upload:\n", "if: always() && inputs.upload == 'true'")
	scans := 0
	for _, path := range repoFiles(t, ".github/workflows", func(name string) bool { return strings.HasSuffix(name, ".yml") }) {
		var wf struct {
			Permissions map[string]string `json:"permissions"`
			Jobs        map[string]struct {
				Permissions map[string]string `json:"permissions"`
				Steps       []struct {
					Uses string         `json:"uses"`
					With map[string]any `json:"with"`
				} `json:"steps"`
			} `json:"jobs"`
		}
		if err := yaml.Unmarshal([]byte(readRepoFile(t, path)), &wf); err != nil {
			t.Fatalf("%s: %v", path, err)
		}
		for name, j := range wf.Jobs {
			perms := j.Permissions
			if perms == nil {
				perms = wf.Permissions
			}
			for _, step := range j.Steps {
				if step.Uses != "./.github/actions/trivy-image-scan" {
					continue
				}
				scans++
				if fmt.Sprint(step.With["upload"]) != "false" && perms["security-events"] != "write" {
					t.Errorf("%s %s: 스캔 결과를 올리는데 security-events 쓰기 권한 없음", path, name)
				}
			}
		}
	}
	if scans == 0 {
		t.Fatal("이미지 스캔 액션을 쓰는 잡 없음")
	}
}

// 운영 이미지 스캔의 Security 탭 범주는 태그 없이 서비스마다 하나
func TestWeeklyScanReportsOneCategoryPerService(t *testing.T) {
	mustContain(t, "security.yml prod-images-scan", job(t, workflow(t, "security.yml"), "prod-images-scan"),
		`name=prod-$(sed -E 's#^muscathan/##; s#[:@].*##' <<<"$IMAGE")`)
}
