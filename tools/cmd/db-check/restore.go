package main

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os/exec"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"
)

// 덤프 파일 이름(<DB>-<UTC 시각>.dump)
var dumpName = regexp.MustCompile(`^(.*?)(-[0-9]{8}T[0-9]{6}Z)?\.dump$`)

// 임시 DB 가 준비됐는지 다시 묻는 간격
var waitEvery = time.Second

// 덤프의 테이블별 행 수(COPY 블록마다 "테이블 행수" 한 줄)
const dumpCountScript = `pg_restore -a -f - "$1" | awk '
/^COPY / { t = substr($0, 6); sub(/ \(.*$/, "", t); sub(/ FROM stdin;$/, "", t); n = 0; on = 1; next }
on && $0 == "\\." { print t, n; on = 0; next }
on { n++ }
'`

// 복원한 DB 의 테이블별 행 수(사용자 스키마의 일반 테이블마다 count(*))
const tableCountQuery = `SELECT format('%I.%I', n.nspname, c.relname),
  (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', n.nspname, c.relname), false, true, '')))[1]::text
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'r' AND n.nspname NOT IN ('pg_catalog', 'information_schema') AND n.nspname NOT LIKE 'pg\_toast%'
ORDER BY 1`

// scratch 는 덤프를 복원해 보는 임시 Postgres
type scratch interface {
	load(ctx context.Context, db, dump string) error
	dumpCounts(ctx context.Context, dump string) (string, error)
	tableCounts(ctx context.Context, db string) (string, error)
	close()
}

// dockerScratch 는 네트워크 없는 컨테이너 하나(덤프는 docker cp, 명령은 docker exec)
type dockerScratch struct {
	name   string
	docker func(ctx context.Context, args ...string) (string, error)
}

// startScratch 는 컨테이너를 띄우고 TCP 로 접속될 때까지 기다림(초기화용 임시 서버는 소켓만 받음)
func startScratch(ctx context.Context, name, image string, docker func(context.Context, ...string) (string, error)) (*dockerScratch, error) {
	s := &dockerScratch{name: name, docker: docker}
	if _, err := docker(ctx, "run", "-d", "--rm", "--network", "none", "--name", name, "-e", "POSTGRES_HOST_AUTH_METHOD=trust", image); err != nil {
		return nil, fmt.Errorf("임시 DB 띄우기 실패: %w", err)
	}
	for range 120 {
		if _, err := docker(ctx, "exec", name, "pg_isready", "-h", "127.0.0.1", "-U", "postgres", "-q"); err == nil {
			return s, nil
		}
		select {
		case <-ctx.Done():
			s.close()
			return nil, ctx.Err()
		case <-time.After(waitEvery):
		}
	}
	s.close()
	return nil, errors.New("임시 DB 가 준비되지 않음")
}

func (s *dockerScratch) load(ctx context.Context, db, dump string) error {
	inside := "/tmp/" + filepath.Base(dump)
	if _, err := s.docker(ctx, "cp", dump, s.name+":"+inside); err != nil {
		return err
	}
	if _, err := s.docker(ctx, "exec", s.name, "createdb", "-U", "postgres", db); err != nil {
		return err
	}
	_, err := s.docker(ctx, "exec", s.name, "pg_restore", "-U", "postgres", "--no-owner", "--no-privileges", "--exit-on-error", "-d", db, inside)
	return err
}

func (s *dockerScratch) dumpCounts(ctx context.Context, dump string) (string, error) {
	return s.docker(ctx, "exec", s.name, "sh", "-c", dumpCountScript, "sh", "/tmp/"+filepath.Base(dump))
}

func (s *dockerScratch) tableCounts(ctx context.Context, db string) (string, error) {
	return s.docker(ctx, "exec", s.name, "psql", "-U", "postgres", "-d", db, "-X", "-A", "-t", "-F", " ", "-v", "ON_ERROR_STOP=1", "-c", tableCountQuery)
}

// close 는 컨테이너 지우기(--rm 이라 볼륨도 같이)
func (s *dockerScratch) close() {
	cleanup, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	_, _ = s.docker(cleanup, "rm", "-f", s.name)
}

// runDocker 는 docker 명령의 표준 출력(실패하면 표준 오류 첫 줄만, 데이터가 섞일 수 있는 나머지는 버림)
func runDocker(ctx context.Context, args ...string) (string, error) {
	var stdout, stderr bytes.Buffer
	cmd := exec.CommandContext(ctx, "docker", args...)
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	if err := cmd.Run(); err != nil {
		first, _, _ := strings.Cut(strings.TrimSpace(stderr.String()), "\n")
		if r := []rune(first); len(r) > 200 {
			first = string(r[:200])
		}
		if first == "" {
			return "", err
		}
		return "", errors.New(first)
	}
	return stdout.String(), nil
}

// dumpDB 는 덤프 파일 이름의 DB 이름
func dumpDB(path string) (string, error) {
	base := filepath.Base(strings.ReplaceAll(path, `\`, "/"))
	m := dumpName.FindStringSubmatch(base)
	switch {
	case m == nil:
		return "", fmt.Errorf("덤프 파일이 아님(.dump): %s", base)
	case m[1] == "":
		return "", fmt.Errorf("DB 이름 없음: %s", base)
	}
	return m[1], nil
}

// parseCounts 는 "테이블 행수" 줄(테이블 이름에 공백이 있어도 마지막 칸이 행 수)
func parseCounts(out string) (map[string]int64, error) {
	counts := map[string]int64{}
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimRight(line, "\r")
		if line == "" {
			continue
		}
		i := strings.LastIndex(line, " ")
		if i <= 0 {
			return nil, fmt.Errorf("행 수 없는 줄: %q", line)
		}
		n, err := strconv.ParseInt(line[i+1:], 10, 64)
		if err != nil || n < 0 {
			return nil, fmt.Errorf("행 수가 아님: %q", line)
		}
		if _, dup := counts[line[:i]]; dup {
			return nil, fmt.Errorf("같은 테이블이 두 번: %s", line[:i])
		}
		counts[line[:i]] = n
	}
	return counts, nil
}

// tableCount 는 테이블 하나의 덤프 · 복원 행 수(-1 은 덤프나 복원 DB 에 테이블이 없음)
type tableCount struct {
	table          string
	dump, restored int64
}

func (c tableCount) mismatch() bool { return c.dump != c.restored }

// compareCounts 는 테이블 이름 순 대조와 행 수가 불일치한 테이블 수
func compareCounts(dump, restored map[string]int64) ([]tableCount, int) {
	var rows []tableCount
	bad := 0
	for table := range joinKeys(dump, restored) {
		c := tableCount{table: table, dump: -1, restored: -1}
		if n, ok := dump[table]; ok {
			c.dump = n
		}
		if n, ok := restored[table]; ok {
			c.restored = n
		}
		if c.mismatch() {
			bad++
		}
		rows = append(rows, c)
	}
	slices.SortFunc(rows, func(a, b tableCount) int { return strings.Compare(a.table, b.table) })
	return rows, bad
}

func joinKeys(a, b map[string]int64) map[string]struct{} {
	keys := map[string]struct{}{}
	for k := range a {
		keys[k] = struct{}{}
	}
	for k := range b {
		keys[k] = struct{}{}
	}
	return keys
}

func countText(n int64) string {
	if n < 0 {
		return "없음"
	}
	return strconv.FormatInt(n, 10)
}

// planRestore 는 덤프마다 DB 이름(같은 DB 가 둘이면 오류)
func planRestore(dumps []string) ([]string, error) {
	var dbs []string
	for _, d := range dumps {
		db, err := dumpDB(d)
		if err != nil {
			return nil, err
		}
		if slices.Contains(dbs, db) {
			return nil, fmt.Errorf("같은 DB 의 덤프가 둘: %s", db)
		}
		dbs = append(dbs, db)
	}
	return dbs, nil
}

// runRestore 는 덤프를 차례로 복원해 테이블마다 행 수를 대조하고 종료 코드(0 같음 · 1 불일치나 실패 · 2 덤프 목록 오류)
func runRestore(ctx context.Context, s scratch, dumps []string, w io.Writer) int {
	dbs, err := planRestore(dumps)
	if err != nil {
		fmt.Fprintln(w, "FAIL ", err)
		return 2
	}
	var tables, bad, failed int
	var rowsTotal int64
	var spent time.Duration
	for i, dump := range dumps {
		db := dbs[i]
		start := time.Now()
		if err := s.load(ctx, db, dump); err != nil {
			fmt.Fprintf(w, "FAIL  %s 복원 실패: %v\n", db, err)
			failed++
			continue
		}
		took := time.Since(start)
		spent += took
		dumpOut, err := s.dumpCounts(ctx, dump)
		if err != nil {
			fmt.Fprintf(w, "FAIL  %s 덤프 행 수 읽기 실패: %v\n", db, err)
			failed++
			continue
		}
		dbOut, err := s.tableCounts(ctx, db)
		if err != nil {
			fmt.Fprintf(w, "FAIL  %s 복원 행 수 읽기 실패: %v\n", db, err)
			failed++
			continue
		}
		fromDump, err1 := parseCounts(dumpOut)
		fromDB, err2 := parseCounts(dbOut)
		if err := errors.Join(err1, err2); err != nil {
			fmt.Fprintf(w, "FAIL  %s %v\n", db, err)
			failed++
			continue
		}
		rows, mismatched := compareCounts(fromDump, fromDB)
		var dbRows int64
		fmt.Fprintf(w, "%s → %s · 복원 %.1f초\n", filepath.Base(strings.ReplaceAll(dump, `\`, "/")), db, took.Seconds())
		for _, r := range rows {
			if r.mismatch() {
				fmt.Fprintf(w, "  FAIL  %s  덤프 %s · 복원 %s\n", r.table, countText(r.dump), countText(r.restored))
			} else {
				fmt.Fprintf(w, "  ok    %s  %d\n", r.table, r.restored)
			}
			dbRows += max(r.restored, 0)
		}
		fmt.Fprintf(w, "  테이블 %d · 행 %d · 불일치 테이블 %d\n", len(rows), dbRows, mismatched)
		tables += len(rows)
		rowsTotal += dbRows
		bad += mismatched
	}
	summary := fmt.Sprintf("합계 · 덤프 %d · 테이블 %d · 행 %d · 불일치 테이블 %d", len(dumps), tables, rowsTotal, bad)
	if failed > 0 {
		summary += fmt.Sprintf(" · 복원 실패 %d", failed)
	}
	fmt.Fprintf(w, "%s · 복원 %.1f초\n", summary, spent.Seconds())
	if bad > 0 || failed > 0 {
		return 1
	}
	return 0
}
