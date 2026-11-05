# tools

릴리스 워크플로가 쓰는 Go 명령(표준 라이브러리만, 테스트 포함).

| 명령 | 하는 일 |
|---|---|
| `cmd/release-plan` | 다시 빌드 · 배포할 서비스 고르기(`-head` 면 운영 태그부터, 아니면 표준 입력의 파일 목록) |
| `cmd/set-image-tag` | 운영 값 파일의 이미지 태그 줄만 바꾸기 |
| `internal/values` | 값 파일 읽기 · 쓰기 |

```bash
cd tools
go test ./...
go run ./cmd/release-plan -head HEAD
go run ./cmd/set-image-tag ../helm/ggeolmuse/values-prod.yaml 7c93f2b user-service
```
