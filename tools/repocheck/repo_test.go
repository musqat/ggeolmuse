package repocheck

import (
	"io/fs"
	"os"
	"path/filepath"
	"testing"
)

// 커밋하지 않는 산출물 · 의존성 · 로컬 문서 폴더
var skipDirs = map[string]bool{".git": true, "node_modules": true, "target": true, ".venv": true, "dist": true, "coverage": true, "docs": true}

// 리포 루트(tools/repocheck 에서 두 단계 위)
func repoRoot(t *testing.T) string {
	t.Helper()
	root, err := filepath.Abs(filepath.Join("..", ".."))
	if err != nil {
		t.Fatal(err)
	}
	return root
}

// dir 아래에서 keep 이 참인 파일의 리포 루트 기준 경로
func repoFiles(t *testing.T, dir string, keep func(name string) bool) []string {
	t.Helper()
	root := repoRoot(t)
	var found []string
	err := filepath.WalkDir(filepath.Join(root, dir), func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			if skipDirs[d.Name()] {
				return filepath.SkipDir
			}
			return nil
		}
		if keep(d.Name()) {
			rel, err := filepath.Rel(root, path)
			if err != nil {
				return err
			}
			found = append(found, filepath.ToSlash(rel))
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(found) == 0 {
		t.Fatalf("%s 아래에 검사할 파일 없음", dir)
	}
	return found
}

// 리포 루트 기준 경로의 파일 내용
func readRepoFile(t *testing.T, rel string) string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(repoRoot(t), rel))
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}
