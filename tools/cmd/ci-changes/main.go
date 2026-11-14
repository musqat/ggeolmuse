// ci-changes 는 CI 가 빌드 · 테스트할 대상을 고른다(master push 는 전부, PR 은 바뀐 폴더만, 베이스 이미지 받기의 Docker Hub 횟수 제한)
//
//	go run ./cmd/ci-changes -event pull_request -base "$BASE" >> "$GITHUB_OUTPUT"
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"os/exec"
	"slices"
	"strings"
)

// CI 이미지 빌드 대상인 자바 서비스(채팅 · 프론트는 따로)
var javaServices = []string{"config-server", "gateway-server", "user-service", "trade-service", "market-data-service", "backtest-service"}

// 바뀌면 PR 도 전부 빌드(모든 잡에 영향: CI 설정 · 공용 액션 · 이 명령 · 메이븐 설정)
var everything = []string{".github/workflows/ci.yml", ".github/actions/", "tools/cmd/ci-changes/", ".mvn/"}

// 바뀌면 자바 서비스 전부
var sharedJava = []string{"backend/ggeolmuse-bom/", "backend/ggeolmuse-common/", "backend/messaging/", "backend/pom.xml", "backend/lombok.config"}

// 바뀌면 채팅 테스트 · 이미지(채팅 서비스와 그 커버리지 기준 명령)
var chatPaths = []string{"backend/chat-service/", "tools/cmd/chat-coverage/"}

// changes 는 빌드할 자바 서비스 · 프론트 · 채팅과 전부 빌드하는지
type changes struct {
	services []string
	frontend bool
	chat     bool
	all      bool
}

func main() {
	event := flag.String("event", "", "GitHub 이벤트 이름(pull_request 면 바뀐 폴더만)")
	base := flag.String("base", "", "PR 의 베이스 커밋")
	head := flag.String("head", "HEAD", "PR 쪽 커밋")
	flag.Parse()

	var files []string
	if *event == "pull_request" {
		var err error
		if files, err = changedFiles(".", *base, *head); err != nil {
			fmt.Fprintln(os.Stderr, "FAIL ", err)
			os.Exit(1)
		}
	}
	c := pick(*event != "pull_request", files)
	write(os.Stdout, c)
	fmt.Fprintf(os.Stderr, "event=%s all=%t services=%v frontend=%t chat=%t\n", *event, c.all, c.services, c.frontend, c.chat)
}

// pick 은 push 면 전부, PR 이면 바뀐 파일로 고른 빌드 대상
func pick(push bool, files []string) changes {
	all := push || slices.ContainsFunc(files, func(f string) bool { return under(f, everything) })
	c := changes{services: []string{}, all: all}
	shared := slices.ContainsFunc(files, func(f string) bool { return under(f, sharedJava) })
	for _, svc := range javaServices {
		if all || shared || slices.ContainsFunc(files, func(f string) bool { return strings.HasPrefix(f, "backend/"+svc+"/") }) {
			c.services = append(c.services, svc)
		}
	}
	c.frontend = all || slices.ContainsFunc(files, func(f string) bool { return strings.HasPrefix(f, "frontend-web/") })
	c.chat = all || slices.ContainsFunc(files, func(f string) bool { return under(f, chatPaths) })
	return c
}

// 폴더(끝이 /)면 그 아래, 파일이면 같은 경로
func under(file string, paths []string) bool {
	return slices.ContainsFunc(paths, func(p string) bool {
		if strings.HasSuffix(p, "/") {
			return strings.HasPrefix(file, p)
		}
		return file == p
	})
}

// write 는 GITHUB_OUTPUT 에 services(JSON 배열) · frontend · chat 세 줄
func write(w io.Writer, c changes) {
	services, _ := json.Marshal(c.services)
	fmt.Fprintf(w, "services=%s\nfrontend=%t\nchat=%t\n", services, c.frontend, c.chat)
}

// changedFiles 는 base 에서 갈라진 뒤 head 쪽에서 바뀐 파일(옮긴 파일은 두 경로, 한글 경로는 따옴표 없이)
func changedFiles(dir, base, head string) ([]string, error) {
	out, err := exec.Command("git", "-C", dir, "-c", "core.quotePath=false",
		"diff", "--no-renames", "--name-only", base+"..."+head).Output()
	if err != nil {
		return nil, fmt.Errorf("git diff %s...%s: %w", base, head, err)
	}
	var files []string
	for _, line := range strings.Split(string(out), "\n") {
		if line = strings.TrimSpace(line); line != "" {
			files = append(files, line)
		}
	}
	return files, nil
}
