package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"slices"
	"strings"
	"time"
)

// offHoursText 는 운영 시간 밖 Cloudflare 안내 페이지(cloudflare/offline-notice)의 제목
const offHoursText = "지금은 운영 시간이 아닙니다"

// smokePath 는 로그인 없이 게이트웨이 · market-data · DB 를 거치는 공개 경로
const smokePath = "/api/market/stocks?page=0&size=1"

// public 은 ArgoCD API · 공개 주소 확인 설정
type public struct {
	argocd string
	app    string
	token  string
	// Cloudflare Access 서비스 토큰(비우면 안 실음)
	accessID     string
	accessSecret string
	site         string
	wait         time.Duration
	interval     time.Duration
	client       *http.Client
	// 지금 master 값 파일의 서비스별 태그(못 읽으면 nil, 비우면 안 봄)
	latest func(context.Context) map[string]string
}

// fatalError 는 다시 물어도 같은 ArgoCD 응답(Access 로그인으로 이동 · JSON 아님)
type fatalError struct{ msg string }

func (e fatalError) Error() string { return e.msg }

// appStatus 는 ArgoCD 애플리케이션의 동기화 · 상태와 배포된 이미지
type appStatus struct {
	Status struct {
		Sync struct {
			Status string `json:"status"`
		} `json:"sync"`
		Health struct {
			Status string `json:"status"`
		} `json:"health"`
		Summary struct {
			Images []string `json:"images"`
		} `json:"summary"`
	} `json:"status"`
}

// runPublic 은 기대 태그 반영 · 정상 상태를 기다린 뒤 스모크 요청 한 번(종료 코드 0 통과 · 건너뜀, 1 실패)
func runPublic(ctx context.Context, p public, want map[string]string, w io.Writer) int {
	if len(want) == 0 {
		fmt.Fprintln(w, "FAIL  대조할 태그 없음(-argocd 는 -values 와 같이)")
		return 1
	}
	deadline := time.Now().Add(p.wait)
	for {
		if p.offHours(ctx) {
			fmt.Fprintf(w, "SKIP  %s 운영 시간 밖 · 롤아웃 · 스모크 확인 건너뜀\n", p.site)
			return 0
		}
		status, err := p.status(ctx)
		var stop fatalError
		if errors.As(err, &stop) {
			fmt.Fprintln(w, "FAIL  ArgoCD:", err)
			return 1
		}
		lines, ok := compare(status, err, want, nil)
		if !ok && err == nil && p.latest != nil && tagDiffers(status, want) {
			lines, ok = compare(status, err, want, p.latest(ctx))
		}
		if ok {
			for _, line := range lines {
				fmt.Fprintln(w, "ok   ", line)
			}
			return p.smoke(ctx, w)
		}
		if !time.Now().Before(deadline) {
			for _, line := range lines {
				fmt.Fprintln(w, "FAIL ", line)
			}
			fmt.Fprintf(w, "FAIL  %s 안에 반영되지 않음\n", p.wait)
			return 1
		}
		select {
		case <-ctx.Done():
			fmt.Fprintln(w, "FAIL ", ctx.Err())
			return 1
		case <-time.After(p.interval):
		}
	}
}

// compare 는 서비스별 결과 줄과 전부 반영 · 정상 여부(뒤 릴리스가 바꾼 master 값 파일 태그가 배포됐으면 통과)
func compare(status appStatus, err error, want, latest map[string]string) ([]string, bool) {
	if err != nil {
		return []string{"ArgoCD: " + err.Error()}, false
	}
	sync, health := status.Status.Sync.Status, status.Status.Health.Status
	ok := sync == "Synced" && health == "Healthy"
	lines := []string{fmt.Sprintf("ArgoCD · %s · %s", sync, health)}
	services := make([]string, 0, len(want))
	for svc := range want {
		services = append(services, svc)
	}
	slices.Sort(services)
	for _, svc := range services {
		tag, found := deployedTag(status.Status.Summary.Images, svc)
		switch {
		case !found:
			lines, ok = append(lines, svc+" · 이미지 없음"), false
		case tag != want[svc] && latest[svc] != "" && latest[svc] != want[svc] && tag == latest[svc]:
			lines = append(lines, fmt.Sprintf("%s · %s · 뒤 릴리스가 덮음(기대 %s)", svc, tag, want[svc]))
		case tag != want[svc]:
			lines, ok = append(lines, fmt.Sprintf("%s · %s · 기대 %s", svc, tag, want[svc])), false
		default:
			lines = append(lines, svc+" · "+tag)
		}
	}
	return lines, ok
}

// tagDiffers 는 배포된 태그가 기대와 다른 서비스가 있는지
func tagDiffers(status appStatus, want map[string]string) bool {
	for svc, tag := range want {
		if deployed, found := deployedTag(status.Status.Summary.Images, svc); found && deployed != tag {
			return true
		}
	}
	return false
}

// deployedTag 는 ArgoCD 이미지 목록에서 서비스 이미지 저장소의 태그
func deployedTag(images []string, svc string) (string, bool) {
	for _, image := range images {
		repo, tag := splitImage(image)
		if repo == svc || strings.HasSuffix(repo, "/"+svc) {
			return tag, true
		}
	}
	return "", false
}

// status 는 ArgoCD 애플리케이션 조회(읽기 전용 계정 토큰, 리다이렉트 안 따라감)
func (p public) status(ctx context.Context) (appStatus, error) {
	var status appStatus
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, p.argocd+"/api/v1/applications/"+p.app, nil)
	if err != nil {
		return status, err
	}
	req.Header.Set("Authorization", "Bearer "+p.token)
	if p.accessID != "" {
		req.Header.Set("CF-Access-Client-Id", p.accessID)
		req.Header.Set("CF-Access-Client-Secret", p.accessSecret)
	}
	client := *p.client
	client.CheckRedirect = func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }
	res, err := client.Do(req)
	if err != nil {
		return status, err
	}
	defer res.Body.Close()
	switch {
	case res.StatusCode >= 300 && res.StatusCode < 400:
		return status, fatalError{fmt.Sprintf("Access 로그인으로 넘어감(응답 %d)", res.StatusCode)}
	case res.StatusCode != http.StatusOK:
		return status, fmt.Errorf("응답 %d", res.StatusCode)
	}
	if err := json.NewDecoder(res.Body).Decode(&status); err != nil {
		return status, fatalError{"JSON 아닌 응답(" + res.Header.Get("Content-Type") + ")"}
	}
	return status, nil
}

// offHours 는 공개 주소가 운영 시간 밖 안내를 주는지(못 닿으면 운영 시간 안 장애로 봄)
func (p public) offHours(ctx context.Context) bool {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, p.site+"/", nil)
	if err != nil {
		return false
	}
	res, err := p.client.Do(req)
	if err != nil {
		return false
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusServiceUnavailable {
		return false
	}
	body, _ := io.ReadAll(io.LimitReader(res.Body, 64<<10))
	return strings.Contains(string(body), offHoursText)
}

// smoke 는 공개 종목 목록이 200 과 한 줄 이상을 주는지
func (p public) smoke(ctx context.Context, w io.Writer) int {
	url := p.site + smokePath
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		fmt.Fprintln(w, "FAIL  스모크:", err)
		return 1
	}
	res, err := p.client.Do(req)
	if err != nil {
		fmt.Fprintln(w, "FAIL  스모크:", err)
		return 1
	}
	defer res.Body.Close()
	var page struct {
		Content []json.RawMessage `json:"content"`
	}
	if res.StatusCode != http.StatusOK {
		fmt.Fprintf(w, "FAIL  스모크 %s · 응답 %d\n", url, res.StatusCode)
		return 1
	}
	if err := json.NewDecoder(res.Body).Decode(&page); err != nil || len(page.Content) == 0 {
		fmt.Fprintf(w, "FAIL  스모크 %s · 종목 없음\n", url)
		return 1
	}
	fmt.Fprintf(w, "ok    스모크 %s · 200\n", url)
	return 0
}
