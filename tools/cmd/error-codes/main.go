// error-codes 는 backend main 자바의 예외 생성식과 오류 코드 enum 을 검사한다.
//
//	go run ./cmd/error-codes <backend 경로>
package main

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

var (
	// enum 상수 머리
	constantHead = regexp.MustCompile(`(?m)^\s*([A-Z][A-Z0-9_]*)\s*\(`)
	// ErrorCode 구현 enum
	errorCodeEnum = regexp.MustCompile(`\benum\s+\w+\s+implements\s+(?:[\w.]+\.)?ErrorCode\b`)
	// 백엔드 예외 클래스 선언
	declaredException = regexp.MustCompile(`\bclass\s+([A-Z]\w*Exception)\b`)
	// 예외 생성식(OAuth2Error 같은 오류 설명 객체 제외)
	exceptionNew = regexp.MustCompile(`\bnew\s+([A-Z]\w*Exception|AssertionError|Error)\s*\(`)
)

// 검사에 걸린 곳
type finding struct {
	path    string
	line    int
	message string
}

func main() {
	if len(os.Args) != 2 {
		fmt.Fprintln(os.Stderr, "사용: error-codes <backend 경로>")
		os.Exit(2)
	}
	sources, err := javaSources(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	found := append(exceptionConstructions(sources), unusedConstants(sources)...)
	for _, f := range found {
		fmt.Printf("::error file=%s,line=%d::%s\n", filepath.ToSlash(filepath.Join(os.Args[1], f.path)), f.line, f.message)
	}
	if len(found) > 0 {
		os.Exit(1)
	}
	fmt.Printf("오류 코드 enum %d개 · 예외 생성식 모두 규칙대로\n", len(enumFiles(sources)))
}

// root 아래 자바 파일(target 제외)
func javaSources(root string) (map[string]string, error) {
	sources := map[string]string{}
	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			if d.Name() == "target" || d.Name() == "node_modules" {
				return filepath.SkipDir
			}
			return nil
		}
		if !strings.HasSuffix(path, ".java") {
			return nil
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		sources[filepath.ToSlash(rel)] = string(data)
		return nil
	})
	return sources, err
}

// main 소스 여부
func isMain(path string) bool {
	return strings.Contains(path, "/src/main/") && !strings.Contains(path, "/target/")
}

// main 소스 경로(정렬)
func mainPaths(sources map[string]string) []string {
	var paths []string
	for path := range sources {
		if isMain(path) {
			paths = append(paths, path)
		}
	}
	sort.Strings(paths)
	return paths
}

// 오류 코드 enum 파일
func enumFiles(sources map[string]string) []string {
	var files []string
	for _, path := range mainPaths(sources) {
		byPlace := strings.Contains(path, "/common/enums/response/") && strings.HasSuffix(path, "Response.java")
		if byPlace || errorCodeEnum.MatchString(sources[path]) {
			files = append(files, path)
		}
	}
	return files
}

// enum 상수 이름
func constants(text string) []string {
	open := strings.Index(text, "{")
	if open < 0 {
		return nil
	}
	head := text[open+1:]
	if semi := strings.Index(head, ";"); semi >= 0 {
		head = head[:semi]
	}
	var names []string
	for _, m := range constantHead.FindAllStringSubmatch(head, -1) {
		names = append(names, m[1])
	}
	return names
}

// main 코드에서 안 쓰는 상수
func unusedConstants(sources map[string]string) []finding {
	var found []finding
	for _, path := range enumFiles(sources) {
		enum := strings.TrimSuffix(filepath.Base(path), ".java")
		enumWord := regexp.MustCompile(`\b` + regexp.QuoteMeta(enum) + `\b`)
		var users []string
		for _, other := range mainPaths(sources) {
			if other != path && enumWord.MatchString(sources[other]) {
				users = append(users, sources[other])
			}
		}
		for _, constant := range constants(sources[path]) {
			word := regexp.MustCompile(`\b` + regexp.QuoteMeta(constant) + `\b`)
			used := false
			for _, text := range users {
				if word.MatchString(text) {
					used = true
					break
				}
			}
			if !used {
				found = append(found, finding{path, lineOf(sources[path], strings.Index(sources[path], constant+"(")),
					enum + "." + constant + " 를 쓰는 곳 없음"})
			}
		}
	}
	return found
}

// 표준 예외 · 문자열 문구를 넘기는 예외 생성식
func exceptionConstructions(sources map[string]string) []finding {
	declared := map[string]bool{}
	for _, path := range mainPaths(sources) {
		for _, m := range declaredException.FindAllStringSubmatch(mask(sources[path]), -1) {
			declared[m[1]] = true
		}
	}
	var found []finding
	for _, path := range mainPaths(sources) {
		masked := mask(sources[path])
		for _, m := range exceptionNew.FindAllStringSubmatchIndex(masked, -1) {
			name := masked[m[2]:m[3]]
			line := lineOf(masked, m[0])
			if !declared[name] {
				found = append(found, finding{path, line, name + " 대신 서비스 예외와 오류 코드를 씀"})
				continue
			}
			if strings.Contains(arguments(masked, m[1]), `"`) {
				found = append(found, finding{path, line, name + " 에 문자열 문구를 넘김"})
			}
		}
	}
	return found
}

// 주석 · 리터럴 안을 지운 소스(길이 · 줄 유지)
func mask(src string) string {
	out := []byte(src)
	for i := 0; i < len(out); i++ {
		switch {
		case out[i] == '/' && i+1 < len(out) && out[i+1] == '/':
			for ; i < len(out) && out[i] != '\n'; i++ {
				out[i] = ' '
			}
		case out[i] == '/' && i+1 < len(out) && out[i+1] == '*':
			for ; i < len(out) && !(out[i] == '*' && i+1 < len(out) && out[i+1] == '/'); i++ {
				if out[i] != '\n' {
					out[i] = ' '
				}
			}
			if i+1 < len(out) {
				out[i], out[i+1] = ' ', ' '
				i++
			}
		case out[i] == '"' || out[i] == '\'':
			quote := out[i]
			for i++; i < len(out) && out[i] != quote; i++ {
				if out[i] == '\\' && i+1 < len(out) {
					out[i] = 'x'
					i++
				}
				if out[i] != '\n' {
					out[i] = 'x'
				}
			}
		}
	}
	return string(out)
}

// 짝 괄호까지의 인자
func arguments(masked string, start int) string {
	depth := 1
	for i := start; i < len(masked); i++ {
		switch masked[i] {
		case '(':
			depth++
		case ')':
			depth--
			if depth == 0 {
				return masked[start:i]
			}
		}
	}
	return masked[start:]
}

// offset 의 줄 번호
func lineOf(text string, offset int) int {
	if offset < 0 {
		return 1
	}
	return strings.Count(text[:offset], "\n") + 1
}
