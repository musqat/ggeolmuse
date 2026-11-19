package repocheck

import (
	"strings"
	"testing"
)

// dependabot.yml 에서 한 생태계 항목(다음 "- package-ecosystem:" 앞까지)
func ecosystem(t *testing.T, name string) string {
	t.Helper()
	config := readRepoFile(t, ".github/dependabot.yml")
	head := "- package-ecosystem: " + name + "\n"
	start := strings.Index(config, head)
	if start < 0 {
		t.Fatalf("dependabot.yml 에 %s 항목 없음", name)
	}
	rest := config[start+len(head):]
	if end := strings.Index(rest, "- package-ecosystem:"); end >= 0 {
		rest = rest[:end]
	}
	return rest
}

func TestDependabotBumpsOpenTelemetryTogether(t *testing.T) {
	pip := ecosystem(t, "pip")
	otel := strings.Index(pip, `patterns: ["opentelemetry-*"]`)
	group := strings.Index(pip, "minor-and-patch:")
	if otel < 0 || group < 0 || otel > group {
		t.Errorf("pip 묶음에 opentelemetry-* 묶음이 minor-and-patch 앞에 없음:\n%s", pip)
	}
}

func TestDependabotKeepsBuildImageJDK(t *testing.T) {
	mustContain(t, "dependabot.yml docker", ecosystem(t, "docker"),
		"- dependency-name: maven\n        update-types: [version-update:semver-patch]")
}
