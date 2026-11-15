package main

import (
	"context"
	"errors"
	"maps"
	"slices"
	"strings"
	"testing"
	"time"
)

func TestDumpDB(t *testing.T) {
	cases := []struct{ path, db, err string }{
		{"dumps/2026/11/ggeolmuse_trade-20261101T000012Z.dump", "ggeolmuse_trade", ""},
		{`C:\dumps\keycloak-20261101T000012Z.dump`, "keycloak", ""},
		{"ggeolmuse_user.dump", "ggeolmuse_user", ""},
		{"ggeolmuse_user-20261101T000012Z.sql", "", "덤프 파일이 아님(.dump): ggeolmuse_user-20261101T000012Z.sql"},
		{"-20261101T000012Z.dump", "", "DB 이름 없음: -20261101T000012Z.dump"},
	}
	for _, c := range cases {
		db, err := dumpDB(c.path)
		switch {
		case c.err != "":
			if err == nil || err.Error() != c.err {
				t.Errorf("%s: 오류 %v, 기대 %q", c.path, err, c.err)
			}
		case err != nil || db != c.db:
			t.Errorf("%s: %q %v, 기대 %q", c.path, db, err, c.db)
		}
	}
}

func TestParseCounts(t *testing.T) {
	got, err := parseCounts("public.trades 42\npublic.\"Order Line\" 0\r\n\n")
	want := map[string]int64{"public.trades": 42, `public."Order Line"`: 0}
	if err != nil || !maps.Equal(got, want) {
		t.Fatalf("%v %v, 기대 %v", got, err, want)
	}
	for _, out := range []string{"public.trades\n", "public.trades many\n", "public.trades -1\n", "public.trades 1\npublic.trades 2\n"} {
		if _, err := parseCounts(out); err == nil {
			t.Errorf("%q: 오류 아님", out)
		}
	}
}

func TestCompareCounts(t *testing.T) {
	dump := map[string]int64{"public.a": 3, "public.b": 2, "public.only_dump": 1}
	restored := map[string]int64{"public.a": 3, "public.b": 0, "public.only_db": 0}
	rows, bad := compareCounts(dump, restored)
	if bad != 3 {
		t.Errorf("불일치 테이블 %d, 기대 3", bad)
	}
	names := make([]string, len(rows))
	for i, r := range rows {
		names[i] = r.table
	}
	if !slices.Equal(names, []string{"public.a", "public.b", "public.only_db", "public.only_dump"}) {
		t.Errorf("테이블 순서 %v", names)
	}
	if rows[0].mismatch() || !rows[1].mismatch() || !rows[2].mismatch() || !rows[3].mismatch() {
		t.Errorf("다름 판정 %+v", rows)
	}
}

// 명령만 기록하고 정해 둔 출력을 돌려주는 docker
type fakeDocker struct {
	calls [][]string
	out   map[string]string
	fail  map[string]error
}

func (f *fakeDocker) run(_ context.Context, args ...string) (string, error) {
	f.calls = append(f.calls, args)
	key := strings.Join(args, " ")
	for prefix, err := range f.fail {
		if strings.HasPrefix(key, prefix) {
			return "", err
		}
	}
	for prefix, out := range f.out {
		if strings.HasPrefix(key, prefix) {
			return out, nil
		}
	}
	return "", nil
}

func (f *fakeDocker) called(prefix string) bool {
	return slices.ContainsFunc(f.calls, func(c []string) bool { return strings.HasPrefix(strings.Join(c, " "), prefix) })
}

func TestDockerScratch(t *testing.T) {
	waitEvery = time.Millisecond
	d := &fakeDocker{out: map[string]string{
		"exec rc sh -c":   "public.trades 42\n",
		"exec rc psql":    "public.trades 42\n",
		"exec rc pg_isre": "",
	}}
	s, err := startScratch(context.Background(), "rc", "postgres:15-alpine", d.run)
	if err != nil {
		t.Fatal(err)
	}
	if err := s.load(context.Background(), "ggeolmuse_trade", "dumps/ggeolmuse_trade-20261101T000012Z.dump"); err != nil {
		t.Fatal(err)
	}
	dumpOut, _ := s.dumpCounts(context.Background(), "dumps/ggeolmuse_trade-20261101T000012Z.dump")
	tableOut, _ := s.tableCounts(context.Background(), "ggeolmuse_trade")
	s.close()

	for _, want := range []string{
		"run -d --rm --network none --name rc -e POSTGRES_HOST_AUTH_METHOD=trust postgres:15-alpine",
		"exec rc pg_isready -h 127.0.0.1 -U postgres -q",
		"cp dumps/ggeolmuse_trade-20261101T000012Z.dump rc:/tmp/ggeolmuse_trade-20261101T000012Z.dump",
		"exec rc createdb -U postgres ggeolmuse_trade",
		"exec rc pg_restore -U postgres --no-owner --no-privileges --exit-on-error -d ggeolmuse_trade /tmp/ggeolmuse_trade-20261101T000012Z.dump",
		"exec rc psql -U postgres -d ggeolmuse_trade -X -A -t -F",
		"rm -f rc",
	} {
		if !d.called(want) {
			t.Errorf("부르지 않음: docker %s\n%v", want, d.calls)
		}
	}
	if dumpOut != "public.trades 42\n" || tableOut != "public.trades 42\n" {
		t.Errorf("출력 %q %q", dumpOut, tableOut)
	}
}

func TestDockerScratchNotReady(t *testing.T) {
	waitEvery = time.Millisecond
	d := &fakeDocker{fail: map[string]error{"exec rc pg_isready": errors.New("exit status 2")}}
	if _, err := startScratch(context.Background(), "rc", "postgres:15-alpine", d.run); err == nil || !strings.Contains(err.Error(), "임시 DB 가 준비되지 않음") {
		t.Fatalf("오류 %v", err)
	}
	if !d.called("rm -f rc") {
		t.Fatal("준비 실패 뒤 컨테이너를 지우지 않음")
	}
}

// 덤프마다 정해 둔 행 수를 돌려주는 임시 DB
type fakeScratch struct {
	dump, db map[string]string
	loadErr  map[string]error
	loaded   []string
	closed   bool
}

func (f *fakeScratch) load(_ context.Context, db, _ string) error {
	f.loaded = append(f.loaded, db)
	return f.loadErr[db]
}
func (f *fakeScratch) dumpCounts(_ context.Context, dump string) (string, error) {
	return f.dump[dump], nil
}
func (f *fakeScratch) tableCounts(_ context.Context, db string) (string, error) {
	return f.db[db], nil
}
func (f *fakeScratch) close() { f.closed = true }

func TestRunRestore(t *testing.T) {
	trade := "d/ggeolmuse_trade-20261101T000012Z.dump"
	user := "d/ggeolmuse_user-20261101T000012Z.dump"
	cases := []struct {
		name  string
		s     *fakeScratch
		code  int
		lines []string
	}{
		{"행 수가 같음", &fakeScratch{
			dump: map[string]string{trade: "public.trades 42\npublic.holdings 7\n", user: "public.users 3\n"},
			db:   map[string]string{"ggeolmuse_trade": "public.holdings 7\npublic.trades 42\n", "ggeolmuse_user": "public.users 3\n"},
		}, 0, []string{
			"ggeolmuse_trade-20261101T000012Z.dump → ggeolmuse_trade",
			"  ok    public.holdings  7\n",
			"  ok    public.trades  42\n",
			"  테이블 2 · 행 49 · 불일치 테이블 0\n",
			"합계 · 덤프 2 · 테이블 3 · 행 52 · 불일치 테이블 0",
		}},
		{"복원이 덜 됨", &fakeScratch{
			dump: map[string]string{trade: "public.trades 42\npublic.holdings 7\n", user: "public.users 3\n"},
			db:   map[string]string{"ggeolmuse_trade": "public.holdings 0\npublic.trades 42\n", "ggeolmuse_user": "public.users 3\n"},
		}, 1, []string{
			"  FAIL  public.holdings  덤프 7 · 복원 0\n",
			"  테이블 2 · 행 42 · 불일치 테이블 1\n",
			"합계 · 덤프 2 · 테이블 3 · 행 45 · 불일치 테이블 1",
		}},
		{"복원 실패", &fakeScratch{
			dump:    map[string]string{trade: "public.trades 42\n", user: "public.users 3\n"},
			db:      map[string]string{"ggeolmuse_user": "public.users 3\n"},
			loadErr: map[string]error{"ggeolmuse_trade": errors.New("pg_restore: error: could not execute query")},
		}, 1, []string{
			"FAIL  ggeolmuse_trade 복원 실패: pg_restore: error: could not execute query\n",
			"합계 · 덤프 2 · 테이블 1 · 행 3 · 불일치 테이블 0 · 복원 실패 1",
		}},
	}
	for _, c := range cases {
		var out strings.Builder
		code := runRestore(context.Background(), c.s, []string{trade, user}, &out)
		if code != c.code {
			t.Errorf("%s: 종료 코드 %d, 기대 %d\n%s", c.name, code, c.code, out.String())
		}
		for _, line := range c.lines {
			if !strings.Contains(out.String(), line) {
				t.Errorf("%s: 출력에 %q 없음\n%s", c.name, line, out.String())
			}
		}
		if !slices.Equal(c.s.loaded, []string{"ggeolmuse_trade", "ggeolmuse_user"}) {
			t.Errorf("%s: 복원 순서 %v", c.name, c.s.loaded)
		}
	}
}

func TestRunRestoreSameDB(t *testing.T) {
	s := &fakeScratch{}
	var out strings.Builder
	code := runRestore(context.Background(), s, []string{"a/ggeolmuse_trade-20261001T000012Z.dump", "b/ggeolmuse_trade-20261101T000012Z.dump"}, &out)
	if code != 2 || !strings.Contains(out.String(), "같은 DB 의 덤프가 둘: ggeolmuse_trade") || len(s.loaded) != 0 {
		t.Fatalf("종료 코드 %d, 복원 %v\n%s", code, s.loaded, out.String())
	}
}
