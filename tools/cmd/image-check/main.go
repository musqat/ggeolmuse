// image-check 는 렌더한 매니페스트의 이미지 태그가 Docker Hub 에 있는지 대조한다(다른 레지스트리 · digest 고정은 건너뜀)
package main

import (
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"regexp"
	"slices"
	"strings"
	"time"
)

// 매니페스트의 image 줄(목록 머리 · 따옴표 포함)
var imageLine = regexp.MustCompile(`(?m)^\s*-?\s*image:\s*["']?([^\s"']+)["']?\s*$`)

// Hub 요청 한 번의 제한 시간
var client = &http.Client{Timeout: 10 * time.Second}

// 다시 시도 사이 기다리기
var sleep = time.Sleep

// 200 · 404 밖의 응답 · 연결 실패 때 모두 몇 번까지 묻는지
const attempts = 3

func main() {
	hub := flag.String("hub", "https://hub.docker.com", "Docker Hub 주소")
	flag.Parse()
	if flag.NArg() != 1 {
		fmt.Fprintln(os.Stderr, "사용: image-check [-hub 주소] <렌더한 매니페스트>")
		os.Exit(2)
	}
	text, err := os.ReadFile(flag.Arg(0))
	if err != nil {
		fmt.Println("FAIL  읽기 실패:", err)
		os.Exit(1)
	}
	os.Exit(run(string(text), *hub, os.Stdout))
}

// run 은 이미지마다 결과 한 줄을 w 에 쓰고 종료 코드를 돌려줌(0 통과 · 1 실패)
func run(text, hub string, w io.Writer) int {
	list := images(text)
	if len(list) == 0 {
		fmt.Fprintln(w, "FAIL  매니페스트에서 이미지를 찾지 못함")
		return 1
	}
	code := 0
	for _, image := range list {
		repo, tag, onHub := parseRef(image)
		switch {
		case strings.Contains(image, "@"):
			fmt.Fprintf(w, "skip  %s · digest 고정\n", image)
		case !onHub:
			fmt.Fprintf(w, "skip  %s · Docker Hub 아님\n", image)
		default:
			found, status := lookup(hub, repo, tag)
			switch {
			case found:
				fmt.Fprintf(w, "ok    %s\n", image)
			case status == http.StatusNotFound:
				fmt.Fprintf(w, "FAIL  %s · Docker Hub 에 없음\n", image)
				code = 1
			default:
				fmt.Fprintf(w, "FAIL  %s · 확인 실패(응답 %d)\n", image, status)
				code = 1
			}
		}
	}
	return code
}

// images 는 매니페스트의 이미지(겹치지 않게, 이름순)
func images(text string) []string {
	var list []string
	for _, m := range imageLine.FindAllStringSubmatch(text, -1) {
		if !slices.Contains(list, m[1]) {
			list = append(list, m[1])
		}
	}
	slices.Sort(list)
	return list
}

// parseRef 는 Docker Hub 저장소 · 태그(공식 이미지는 library/, 태그가 없으면 latest), 다른 레지스트리 · digest 고정이면 false
func parseRef(image string) (string, string, bool) {
	if strings.Contains(image, "@") {
		return "", "", false
	}
	name := image
	if i := strings.Index(name, "/"); i > 0 {
		host := name[:i]
		switch {
		case host == "docker.io" || host == "index.docker.io":
			name = name[i+1:]
		case host == "localhost" || strings.ContainsAny(host, ".:"):
			return "", "", false
		}
	}
	tag := "latest"
	if i := strings.LastIndex(name, ":"); i > strings.LastIndex(name, "/") {
		name, tag = name[:i], name[i+1:]
	}
	if !strings.Contains(name, "/") {
		name = "library/" + name
	}
	return name, tag, true
}

// lookup 은 200 이면 있음 · 404 면 없음, 그 밖의 응답 · 연결 실패는 다시 물은 뒤 마지막 응답 코드(연결 실패는 0)
func lookup(hub, repo, tag string) (bool, int) {
	status := 0
	for i := range attempts {
		if i > 0 {
			sleep(time.Duration(i) * time.Second)
		}
		resp, err := client.Get(fmt.Sprintf("%s/v2/repositories/%s/tags/%s", hub, repo, tag))
		if err != nil {
			status = 0
			continue
		}
		resp.Body.Close()
		status = resp.StatusCode
		if status == http.StatusOK || status == http.StatusNotFound {
			return status == http.StatusOK, status
		}
	}
	return false, status
}
