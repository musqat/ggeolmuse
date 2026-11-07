# tools

릴리스 워크플로가 쓰는 Go 명령(표준 라이브러리만, 테스트 포함).

| 명령 | 하는 일 |
|---|---|
| `cmd/release-plan` | 다시 빌드 · 배포할 서비스 고르기(`-head` 면 운영 태그부터, 아니면 표준 입력의 파일 목록). `-head` 와 `-body` 를 함께 주면 운영 태그부터 바뀐 서비스 가운데 그 PR 본문 빌드할 서비스 칸에서 체크한 것만 내고, 서비스별 태그(버전, 비우면 SHA 7자리) · 보류(바뀌었지만 체크 안 함) · 체크했지만 바뀐 것 없음을 함께 출력. 버전이 운영 값 파일 이력의 가장 높은 버전 이하면 실패 |
| `cmd/set-image-tag` | 운영 값 파일의 이미지 태그 줄만 바꾸기 |
| `internal/values` | 값 파일 읽기 · 쓰기 |

```bash
cd tools
go test ./...
go run ./cmd/release-plan -head HEAD
go run ./cmd/release-plan -head HEAD -body pr-body.md
go run ./cmd/set-image-tag ../helm/ggeolmuse/values-prod.yaml 7c93f2b user-service
```
