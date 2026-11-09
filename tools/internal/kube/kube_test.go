package kube

import (
	"os"
	"path/filepath"
	"testing"
)

const kubeconfig = `apiVersion: v1
kind: Config
clusters:
- name: k3s
  cluster:
    server: https://127.0.0.1:6443
contexts:
- name: k3s
  context:
    cluster: k3s
    user: admin
current-context: k3s
users:
- name: admin
  user:
    token: test-token
`

func TestClient(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config")
	if err := os.WriteFile(path, []byte(kubeconfig), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Client(path); err != nil {
		t.Fatalf("kubeconfig 로 클라이언트 만들기 실패: %v", err)
	}
}

func TestClientMissingFile(t *testing.T) {
	if _, err := Client(filepath.Join(t.TempDir(), "none")); err == nil {
		t.Fatal("없는 kubeconfig 는 오류")
	}
}
