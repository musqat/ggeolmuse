package main

import (
	"net/http"
	"net/http/httptest"
	"slices"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

const rendered = `
kind: Deployment
spec:
  template:
    spec:
      initContainers:
      - name: wait
        image: busybox:1.38.0
      containers:
      - name: app
        image: "muscathan/user-service:05a3c0e"
---
kind: Deployment
spec:
  template:
    spec:
      containers:
      - image: busybox:1.38.0
      - image: quay.io/keycloak/keycloak:26.8.1
`

func TestImages(t *testing.T) {
	got := images(rendered)
	want := []string{"busybox:1.38.0", "muscathan/user-service:05a3c0e", "quay.io/keycloak/keycloak:26.8.1"}
	if !slices.Equal(got, want) {
		t.Fatalf("이미지 %q, 기대 %q", got, want)
	}
}

func TestParseRef(t *testing.T) {
	cases := []struct {
		image, repo, tag string
		hub              bool
	}{
		{"busybox:1.38.0", "library/busybox", "1.38.0", true},
		{"muscathan/user-service:05a3c0e", "muscathan/user-service", "05a3c0e", true},
		{"docker.io/library/redis:7-alpine", "library/redis", "7-alpine", true},
		{"redis", "library/redis", "latest", true},
		{"quay.io/keycloak/keycloak:26.8.1", "", "", false},
		{"localhost:5000/app:1", "", "", false},
		{"muscathan/app@sha256:abc", "", "", false},
	}
	for _, c := range cases {
		repo, tag, hub := parseRef(c.image)
		if repo != c.repo || tag != c.tag || hub != c.hub {
			t.Errorf("%s: (%q, %q, %v), 기대 (%q, %q, %v)", c.image, repo, tag, hub, c.repo, c.tag, c.hub)
		}
	}
}

// 가짜 Hub: user-service 는 있음, busybox 는 첫 번째에 429 뒤 있음, 나머지는 404
func fakeHub(t *testing.T) *httptest.Server {
	var busybox atomic.Int32
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/v2/repositories/muscathan/user-service/tags/05a3c0e":
			w.WriteHeader(http.StatusOK)
		case "/v2/repositories/library/busybox/tags/1.38.0":
			if busybox.Add(1) == 1 {
				w.WriteHeader(http.StatusTooManyRequests)
				return
			}
			w.WriteHeader(http.StatusOK)
		case "/v2/repositories/library/broken/tags/1":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
}

func TestRun(t *testing.T) {
	sleep = func(time.Duration) {}
	hub := fakeHub(t)
	defer hub.Close()

	var out strings.Builder
	if code := run(rendered, hub.URL, &out); code != 0 {
		t.Fatalf("종료 코드 %d, 기대 0\n%s", code, out.String())
	}
	for _, want := range []string{
		"ok    busybox:1.38.0",
		"ok    muscathan/user-service:05a3c0e",
		"skip  quay.io/keycloak/keycloak:26.8.1 · Docker Hub 아님",
	} {
		if !strings.Contains(out.String(), want) {
			t.Fatalf("출력에 %q 없음\n%s", want, out.String())
		}
	}
}

func TestRunMissingOrBroken(t *testing.T) {
	sleep = func(time.Duration) {}
	hub := fakeHub(t)
	defer hub.Close()

	var out strings.Builder
	code := run("image: muscathan/user-service:typo0\nimage: broken:1\nimage: muscathan/user-service:05a3c0e\n", hub.URL, &out)
	if code != 1 {
		t.Fatalf("종료 코드 %d, 기대 1\n%s", code, out.String())
	}
	for _, want := range []string{
		"FAIL  muscathan/user-service:typo0 · Docker Hub 에 없음",
		"FAIL  broken:1 · 확인 실패(응답 500)",
		"ok    muscathan/user-service:05a3c0e",
	} {
		if !strings.Contains(out.String(), want) {
			t.Fatalf("출력에 %q 없음\n%s", want, out.String())
		}
	}
}

func TestRunNoImages(t *testing.T) {
	var out strings.Builder
	if code := run("kind: ConfigMap\n", "http://unused", &out); code != 1 {
		t.Fatalf("이미지가 없으면 실패, 종료 코드 %d", code)
	}
}
