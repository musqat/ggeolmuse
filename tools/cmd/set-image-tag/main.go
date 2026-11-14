// set-image-tag 는 운영 값 파일에서 서비스의 이미지 태그 줄만 바꾼다
//
//	go run ./cmd/set-image-tag ../helm/ggeolmuse/values-prod.yaml 7c93f2b user-service chat-service
//	go run ./cmd/set-image-tag -tags '{"user-service":"1.8.0"}' -built "$SHA" ../helm/ggeolmuse/values-prod.yaml user-service   # 서비스별 태그 · 봇 커밋 제목
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"regexp"
	"strings"

	"github.com/musqat/ggeolmuse/tools/internal/release"
	"github.com/musqat/ggeolmuse/tools/internal/values"
)

func main() {
	tagsJSON := flag.String("tags", "", "서비스별 태그(JSON 객체)")
	built := flag.String("built", "", "빌드한 커밋(봇 커밋 제목 끝 괄호)")
	flag.Parse()
	args := flag.Args()
	if *tagsJSON != "" {
		if len(args) < 2 {
			usage()
		}
		var tags map[string]string
		if err := json.Unmarshal([]byte(*tagsJSON), &tags); err != nil {
			fail(fmt.Errorf("-tags 는 JSON 객체: %w", err))
		}
		text, subject, err := applyTags(read(args[0]), args[1:], tags, *built)
		if err != nil {
			fail(err)
		}
		write(args[0], text)
		fmt.Printf("subject=%s\n", subject)
		return
	}
	if len(args) < 3 {
		usage()
	}
	path, tag, services := args[0], args[1], args[2:]
	text, err := values.SetTags(read(path), tag, services)
	if err != nil {
		fail(err)
	}
	write(path, text)
	fmt.Printf("%s → %s\n", strings.Join(services, " · "), tag)
}

// 빌드한 커밋 SHA(7 ~ 40자리)
var commitSHA = regexp.MustCompile(`^[0-9a-f]{7,40}$`)

// applyTags 는 서비스마다 tags 의 태그로 바꾼 값 파일과 봇 커밋 제목
func applyTags(text string, services []string, tags map[string]string, built string) (string, string, error) {
	if !commitSHA.MatchString(built) {
		return "", "", fmt.Errorf("-built 는 커밋 SHA(받은 값 %q)", built)
	}
	for _, svc := range services {
		tag, ok := tags[svc]
		if !ok {
			return "", "", fmt.Errorf("%s 태그 없음", svc)
		}
		next, err := values.SetTags(text, tag, []string{svc})
		if err != nil {
			return "", "", err
		}
		text = next
	}
	return text, release.Subject(services, tags, built), nil
}

func usage() {
	fmt.Fprintln(os.Stderr, "사용: set-image-tag <값 파일> <태그> <서비스>... | set-image-tag -tags <JSON> -built <커밋> <값 파일> <서비스>...")
	os.Exit(2)
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, "FAIL ", err)
	os.Exit(1)
}

func read(path string) string {
	b, err := os.ReadFile(path)
	if err != nil {
		fail(err)
	}
	return string(b)
}

func write(path, text string) {
	if err := os.WriteFile(path, []byte(text), 0o644); err != nil {
		fail(err)
	}
}
