# tools

CI · 릴리스 워크플로가 쓰는 Go 명령(테스트 포함). 릴리스 쪽은 표준 라이브러리만, netpol-check 만 쿠버네티스 타입(`k8s.io/api`)을 쓴다.

| 명령 | 하는 일 |
|---|---|
| `cmd/release-plan` | 다시 빌드 · 배포할 서비스 고르기(`-head` 면 운영 태그부터, 아니면 표준 입력의 파일 목록). `-head` 와 `-body` 를 함께 주면 운영 태그부터 바뀐 서비스 가운데 그 PR 본문 빌드할 서비스 칸에서 체크한 것만 내고, 서비스별 태그(버전, 비우면 SHA 7자리) · 보류(바뀌었지만 체크 안 함) · 체크했지만 바뀐 것 없음을 함께 출력. 버전이 운영 값 파일 이력의 가장 높은 버전 이하면 실패. `-body` 대신 `-picks`(쉼표 목록, 손 릴리스)도 같은 검사. `-only` 에 `-tags` 를 함께 주면 다시 고른 서비스의 버전도 같은 하한으로 확인 |
| `cmd/set-image-tag` | 운영 값 파일의 이미지 태그 줄만 바꾸기. `-tags` · `-built`(커밋 SHA) 를 주면 서비스마다 그 태그로 바꾸고 봇 커밋 제목을 출력 |
| `cmd/netpol-check` | 렌더한 매니페스트에서 게이트웨이 → business NetworkPolicy 허용 포트와 business 워크로드의 containerPort 를 대조(빠진 포트 · 이름 포트 · 정책 없음이면 실패) |
| `internal/release` | 릴리스 봇 커밋 제목 만들기 · 읽기(끝 괄호의 빌드한 커밋) |
| `internal/values` | 값 파일 읽기 · 쓰기 |

```bash
cd tools
go test ./...
go run ./cmd/release-plan -head HEAD
go run ./cmd/release-plan -head HEAD -body pr-body.md
go run ./cmd/release-plan -head "$(git rev-parse HEAD)" -picks 'backtest-service 1.8.0, frontend-web'
go run ./cmd/release-plan -head "$(git rev-parse HEAD)" -rev HEAD -only '["user-service"]' -tags '{"user-service":"1.8.0"}'
go run ./cmd/set-image-tag ../helm/ggeolmuse/values-prod.yaml 7c93f2b user-service
go run ./cmd/set-image-tag -tags '{"user-service":"1.8.0"}' -built "$(git rev-parse HEAD)" ../helm/ggeolmuse/values-prod.yaml user-service
helm template ggeolmuse ../helm/ggeolmuse -f ../helm/ggeolmuse/values-prod.yaml > /tmp/rendered.yaml && go run ./cmd/netpol-check /tmp/rendered.yaml
```
