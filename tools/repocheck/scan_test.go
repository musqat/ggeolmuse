package repocheck

import (
	"strings"
	"testing"
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
