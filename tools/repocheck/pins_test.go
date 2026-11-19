package repocheck

import (
	"regexp"
	"strings"
	"testing"
)

var (
	usesRef      = regexp.MustCompile(`(?m)^\s*-?\s*uses:\s*(\S+)`)
	baseImage    = regexp.MustCompile(`(?m)^FROM\s+(\S+)`)
	pinnedAction = regexp.MustCompile(`^[\w.-]+/[\w./-]+@[0-9a-f]{40}$`)
)

func TestExternalActionsArePinnedToCommit(t *testing.T) {
	var loose []string
	for _, path := range repoFiles(t, ".github", func(name string) bool { return strings.HasSuffix(name, ".yml") }) {
		for _, m := range usesRef.FindAllStringSubmatch(readRepoFile(t, path), -1) {
			ref := m[1]
			if strings.HasPrefix(ref, "./") || strings.HasPrefix(ref, "docker://") {
				continue
			}
			if !pinnedAction.MatchString(ref) {
				loose = append(loose, path+": "+ref)
			}
		}
	}
	if len(loose) > 0 {
		t.Errorf("커밋 SHA 로 고정 안 된 액션:\n%s", strings.Join(loose, "\n"))
	}
}

func TestBaseImagesArePinnedToDigest(t *testing.T) {
	var loose []string
	for _, path := range repoFiles(t, ".", func(name string) bool { return strings.Contains(name, "Dockerfile") }) {
		for _, m := range baseImage.FindAllStringSubmatch(readRepoFile(t, path), -1) {
			if !strings.Contains(m[1], "@sha256:") {
				loose = append(loose, path+": "+m[1])
			}
		}
	}
	if len(loose) > 0 {
		t.Errorf("digest 로 고정 안 된 베이스 이미지:\n%s", strings.Join(loose, "\n"))
	}
}
