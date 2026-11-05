// set-image-tag 는 운영 값 파일에서 서비스의 이미지 태그 줄만 바꾼다
//
//	go run ./cmd/set-image-tag ../helm/ggeolmuse/values-prod.yaml 7c93f2b user-service chat-service
package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/musqat/ggeolmuse/tools/internal/values"
)

func main() {
	if len(os.Args) < 4 {
		fmt.Fprintln(os.Stderr, "사용: set-image-tag <값 파일> <태그> <서비스>...")
		os.Exit(2)
	}
	path, tag, services := os.Args[1], os.Args[2], os.Args[3:]
	b, err := os.ReadFile(path)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	text, err := values.SetTags(string(b), tag, services)
	if err != nil {
		fmt.Fprintln(os.Stderr, "FAIL ", err)
		os.Exit(1)
	}
	if err := os.WriteFile(path, []byte(text), 0o644); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	fmt.Printf("%s → %s\n", strings.Join(services, " · "), tag)
}
