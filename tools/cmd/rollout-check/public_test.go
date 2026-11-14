package main

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

// ArgoCD 애플리케이션 응답(호출마다 다음 응답, 마지막 응답은 이후 계속)
func argocd(t *testing.T, calls *atomic.Int32, bodies ...string) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/applications/ggeolmuse" || r.Header.Get("Authorization") != "Bearer token" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		n := int(calls.Add(1))
		fmt.Fprint(w, bodies[min(n, len(bodies))-1])
	}))
	t.Cleanup(server.Close)
	return server
}

// 공개 주소(첫 화면 응답 · 스모크 응답)
func site(t *testing.T, rootStatus int, rootBody string, smokeStatus int, smokeBody string) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.RequestURI() {
		case "/":
			w.WriteHeader(rootStatus)
			fmt.Fprint(w, rootBody)
		case "/api/market/stocks?page=0&size=1":
			w.WriteHeader(smokeStatus)
			fmt.Fprint(w, smokeBody)
		default:
			http.NotFound(w, r)
		}
	}))
	t.Cleanup(server.Close)
	return server
}

func app(sync, health string, images ...string) string {
	return fmt.Sprintf(`{"status":{"sync":{"status":%q},"health":{"status":%q},"summary":{"images":["%s"]}}}`,
		sync, health, strings.Join(images, `","`))
}

const stocks = `{"content":[{"symbol":"AAPL"}],"last":false}`

func check(argo, pub *httptest.Server, wait time.Duration, want map[string]string) (int, string) {
	p := public{argocd: argo.URL, app: "ggeolmuse", token: "token", site: pub.URL, wait: wait,
		interval: 5 * time.Millisecond, client: http.DefaultClient}
	var out strings.Builder
	code := runPublic(context.Background(), p, want, &out)
	return code, out.String()
}

func expectLines(t *testing.T, out string, lines ...string) {
	t.Helper()
	for _, line := range lines {
		if !strings.Contains(out, line+"\n") {
			t.Errorf("출력에 %q 없음\n%s", line, out)
		}
	}
}

func TestRunPublicRolledOut(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Healthy", "muscathan/backtest-service:1.8.11", "muscathan/user-service:1.8.0", "redis:7-alpine"))
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)

	code, out := check(argo, pub, time.Second, map[string]string{"backtest-service": "1.8.11", "user-service": "1.8.0"})

	if code != 0 {
		t.Fatalf("종료 코드 %d, 기대 0\n%s", code, out)
	}
	expectLines(t, out,
		"ok    ArgoCD · Synced · Healthy",
		"ok    backtest-service · 1.8.11",
		"ok    user-service · 1.8.0",
		"ok    스모크 "+pub.URL+"/api/market/stocks?page=0&size=1 · 200",
	)
}

func TestRunPublicWaitsUntilNewTagHealthy(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls,
		app("OutOfSync", "Healthy", "muscathan/backtest-service:1.8.10"),
		app("Synced", "Progressing", "muscathan/backtest-service:1.8.11"),
		app("Synced", "Healthy", "muscathan/backtest-service:1.8.11"),
	)
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)

	code, out := check(argo, pub, time.Second, map[string]string{"backtest-service": "1.8.11"})

	if code != 0 || calls.Load() != 3 {
		t.Fatalf("종료 코드 %d · 조회 %d번, 기대 0 · 3번\n%s", code, calls.Load(), out)
	}
	expectLines(t, out, "ok    backtest-service · 1.8.11")
}

func TestRunPublicTimesOut(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Degraded", "muscathan/backtest-service:1.8.10"))
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)

	code, out := check(argo, pub, 30*time.Millisecond, map[string]string{"backtest-service": "1.8.11", "chat-service": "1.7.0"})

	if code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out)
	}
	expectLines(t, out,
		"FAIL  ArgoCD · Synced · Degraded",
		"FAIL  backtest-service · 1.8.10 · 기대 1.8.11",
		"FAIL  chat-service · 이미지 없음",
		"FAIL  30ms 안에 반영되지 않음",
	)
	if strings.Contains(out, "스모크") {
		t.Errorf("반영 전에 스모크 요청을 보냈다\n%s", out)
	}
}

func TestRunPublicSkipsOffHours(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Healthy", "muscathan/backtest-service:1.8.11"))
	pub := site(t, http.StatusServiceUnavailable, "<h1>지금은 운영 시간이 아닙니다</h1>", http.StatusServiceUnavailable, "")

	code, out := check(argo, pub, time.Second, map[string]string{"backtest-service": "1.8.11"})

	if code != 0 || calls.Load() != 0 {
		t.Fatalf("종료 코드 %d · 조회 %d번, 기대 0 · 0번\n%s", code, calls.Load(), out)
	}
	expectLines(t, out, "SKIP  "+pub.URL+" 운영 시간 밖 · 롤아웃 · 스모크 확인 건너뜀")
}

func TestRunPublicOutageInHoursIsNotSkipped(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, `{}`)
	pub := site(t, http.StatusServiceUnavailable, "<h1>일시적인 장애로 접속할 수 없습니다</h1>", http.StatusServiceUnavailable, "")

	code, out := check(argo, pub, 20*time.Millisecond, map[string]string{"backtest-service": "1.8.11"})

	if code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out)
	}
	expectLines(t, out, "FAIL  backtest-service · 이미지 없음")
}

func TestRunPublicSmokeFails(t *testing.T) {
	for _, tc := range []struct {
		name   string
		status int
		body   string
		line   string
	}{
		{"5xx", http.StatusBadGateway, "", "응답 502"},
		{"빈 목록", http.StatusOK, `{"content":[]}`, "종목 없음"},
		{"JSON 아님", http.StatusOK, "<html>", "종목 없음"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var calls atomic.Int32
			argo := argocd(t, &calls, app("Synced", "Healthy", "muscathan/backtest-service:1.8.11"))
			pub := site(t, http.StatusOK, "<html></html>", tc.status, tc.body)

			code, out := check(argo, pub, time.Second, map[string]string{"backtest-service": "1.8.11"})

			if code != 1 {
				t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out)
			}
			expectLines(t, out, "FAIL  스모크 "+pub.URL+"/api/market/stocks?page=0&size=1 · "+tc.line)
		})
	}
}

func TestRunPublicRejectedToken(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Healthy", "muscathan/backtest-service:1.8.11"))
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)
	p := public{argocd: argo.URL, app: "ggeolmuse", token: "wrong", site: pub.URL, wait: 10 * time.Millisecond,
		interval: 5 * time.Millisecond, client: http.DefaultClient}

	var out strings.Builder
	code := runPublic(context.Background(), p, map[string]string{"backtest-service": "1.8.11"}, &out)

	if code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out.String())
	}
	expectLines(t, out.String(), "FAIL  ArgoCD: 응답 401")
}

func TestRunPublicStopsAtAccessLogin(t *testing.T) {
	var calls atomic.Int32
	argo := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		http.Redirect(w, r, "https://team.cloudflareaccess.com/cdn-cgi/access/login", http.StatusFound)
	}))
	t.Cleanup(argo.Close)
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)

	code, out := check(argo, pub, time.Hour, map[string]string{"backtest-service": "1.8.11"})

	if code != 1 || calls.Load() != 1 {
		t.Fatalf("종료 코드 %d · 조회 %d번, 기대 1 · 1번\n%s", code, calls.Load(), out)
	}
	expectLines(t, out, "FAIL  ArgoCD: Access 로그인으로 넘어감(응답 302)")
}

func TestRunPublicStopsAtHTML(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, "<!doctype html><title>Sign in</title>")
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)

	code, out := check(argo, pub, time.Hour, map[string]string{"backtest-service": "1.8.11"})

	if code != 1 || calls.Load() != 1 {
		t.Fatalf("종료 코드 %d · 조회 %d번, 기대 1 · 1번\n%s", code, calls.Load(), out)
	}
	if !strings.Contains(out, "FAIL  ArgoCD: JSON 아닌 응답(text/html") {
		t.Errorf("JSON 아닌 응답 줄 없음\n%s", out)
	}
}

func TestRunPublicSendsAccessServiceToken(t *testing.T) {
	argo := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("CF-Access-Client-Id") != "id" || r.Header.Get("CF-Access-Client-Secret") != "secret" {
			http.Redirect(w, r, "https://team.cloudflareaccess.com/cdn-cgi/access/login", http.StatusFound)
			return
		}
		fmt.Fprint(w, app("Synced", "Healthy", "muscathan/backtest-service:1.8.11"))
	}))
	t.Cleanup(argo.Close)
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)
	p := public{argocd: argo.URL, app: "ggeolmuse", token: "token", accessID: "id", accessSecret: "secret",
		site: pub.URL, wait: time.Second, interval: 5 * time.Millisecond, client: http.DefaultClient}

	var out strings.Builder
	code := runPublic(context.Background(), p, map[string]string{"backtest-service": "1.8.11"}, &out)

	if code != 0 {
		t.Fatalf("종료 코드 %d, 기대 0\n%s", code, out.String())
	}
}

func TestRunPublicPassesWhenLaterReleaseCovers(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Healthy", "muscathan/backtest-service:1.8.12", "muscathan/user-service:1.8.0"))
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)
	p := public{argocd: argo.URL, app: "ggeolmuse", token: "token", site: pub.URL, wait: time.Second,
		interval: 5 * time.Millisecond, client: http.DefaultClient,
		latest: func(context.Context) map[string]string {
			return map[string]string{"backtest-service": "1.8.12", "user-service": "1.8.0"}
		}}

	var out strings.Builder
	code := runPublic(context.Background(), p, map[string]string{"backtest-service": "1.8.11", "user-service": "1.8.0"}, &out)

	if code != 0 {
		t.Fatalf("종료 코드 %d, 기대 0\n%s", code, out.String())
	}
	expectLines(t, out.String(), "ok    backtest-service · 1.8.12 · 뒤 릴리스가 덮음(기대 1.8.11)", "ok    user-service · 1.8.0")
}

func TestRunPublicWaitsWhenMasterStillExpects(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Healthy", "muscathan/backtest-service:1.8.10"))
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)
	p := public{argocd: argo.URL, app: "ggeolmuse", token: "token", site: pub.URL, wait: 30 * time.Millisecond,
		interval: 5 * time.Millisecond, client: http.DefaultClient,
		latest: func(context.Context) map[string]string { return map[string]string{"backtest-service": "1.8.11"} }}

	var out strings.Builder
	code := runPublic(context.Background(), p, map[string]string{"backtest-service": "1.8.11"}, &out)

	if code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out.String())
	}
	expectLines(t, out.String(), "FAIL  backtest-service · 1.8.10 · 기대 1.8.11")
}

func TestRunPublicNeedsExpectedTags(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Healthy", "muscathan/backtest-service:1.8.11"))
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)

	code, out := check(argo, pub, time.Second, nil)

	if code != 1 || calls.Load() != 0 {
		t.Fatalf("종료 코드 %d · 조회 %d번, 기대 1 · 0번\n%s", code, calls.Load(), out)
	}
	expectLines(t, out, "FAIL  대조할 태그 없음(-argocd 는 -values 와 같이)")
}

func TestRunPublicStopsAtContextDeadline(t *testing.T) {
	var calls atomic.Int32
	argo := argocd(t, &calls, app("Synced", "Progressing", "muscathan/backtest-service:1.8.10"))
	pub := site(t, http.StatusOK, "<html></html>", http.StatusOK, stocks)
	p := public{argocd: argo.URL, app: "ggeolmuse", token: "token", site: pub.URL, wait: time.Hour,
		interval: 5 * time.Millisecond, client: http.DefaultClient}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Millisecond)
	defer cancel()

	var out strings.Builder
	code := runPublic(ctx, p, map[string]string{"backtest-service": "1.8.11"}, &out)

	if code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out.String())
	}
	expectLines(t, out.String(), "FAIL  context deadline exceeded")
}

func TestOnly(t *testing.T) {
	want := map[string]string{"backtest-service": "1.8.11", "user-service": "1.8.0", "trade-service": "1.8.1"}

	got, err := only(want, "user-service, backtest-service")

	if err != nil || len(got) != 2 || got["user-service"] != "1.8.0" || got["backtest-service"] != "1.8.11" {
		t.Fatalf("got %v · %v", got, err)
	}
	if all, err := only(want, ""); err != nil || len(all) != 3 {
		t.Fatalf("빈 목록인데 전부가 아니다: %v · %v", all, err)
	}
	if _, err := only(want, "chat-service"); err == nil {
		t.Fatal("값 파일에 없는 서비스인데 오류가 없다")
	}
}
