package repocheck

import (
	"fmt"
	"regexp"
	"strings"
	"testing"
)

// 저장소 메서드의 JPQL · 네이티브 쿼리 어노테이션
var queryAnnotation = regexp.MustCompile(`(?m)^\s*@Query\b`)

// 네이티브로 두는 쿼리 수(Postgres 어드바이저리 잠금)
var allowedQueries = map[string]int{
	"backend/trade-service/src/main/java/com/muscat/trade/domain/repository/AccountProfitDailyRepository.java": 1,
}

func TestRepositoriesQueryWithQueryDSL(t *testing.T) {
	var loose []string
	seen := map[string]int{}
	for _, path := range repoFiles(t, "backend", func(name string) bool { return strings.HasSuffix(name, ".java") }) {
		if !strings.Contains(path, "/src/main/") {
			continue
		}
		n := len(queryAnnotation.FindAllStringIndex(readRepoFile(t, path), -1))
		seen[path] = n
		if n > allowedQueries[path] {
			loose = append(loose, fmt.Sprintf("%s: @Query %d", path, n))
		}
	}
	for path, want := range allowedQueries {
		if seen[path] != want {
			loose = append(loose, fmt.Sprintf("%s: 허용 %d, 지금 %d(허용 목록을 맞춤)", path, want, seen[path]))
		}
	}
	if len(loose) > 0 {
		t.Errorf("QueryDSL 대신 @Query 로 쓴 저장소 쿼리:\n%s", strings.Join(loose, "\n"))
	}
}
