# tools

CI · 릴리스 · 운영 확인에 쓰는 Go 명령이다.

| 명령 | 하는 일 |
|---|---|
| `cmd/release-plan` | 릴리스할 서비스와 태그를 고른다. PR 본문 "빌드할 서비스" 에서 체크했고 운영 태그 뒤로 바뀐 서비스만 내고, 태그는 적은 버전이나 커밋 SHA 7자리 |
| `cmd/set-image-tag` | 운영 값 파일의 이미지 태그를 바꾸고 봇 커밋 제목을 낸다 |
| `cmd/netpol-check` | 게이트웨이에서 서비스 포트로 가는 길이 NetworkPolicy 에 열려 있는지 본다 |
| `cmd/ci-changes` | 바뀐 폴더로 CI 가 빌드 · 테스트할 대상을 고른다 |
| `cmd/chat-coverage` | chat 테스트의 줄 · 분기 커버리지를 최소치와 비교하고 표를 잡 요약에 남긴다 |
| `cmd/image-check` | 렌더한 이미지 태그가 Docker Hub 에 있는지 본다 |
| `cmd/rollout-check` | Deployment 마다 이미지 태그 · 준비 · 재시작 수를 보고 운영 값 파일의 태그와 대조한다 |
| `cmd/db-check` | 읽기 전용 계정으로 임시 파드를 띄워 운영 DB 쿼리 결과(숫자)만 받는다 |
| `internal/release` | 봇 커밋 제목 만들기 · 읽기 |
| `internal/values` | 값 파일 읽기 · 쓰기 |
| `internal/kube` | kubeconfig 로 클러스터 클라이언트 만들기 |

```bash
cd tools
go test ./...
go run ./cmd/release-plan -head HEAD -body pr-body.md
go run ./cmd/release-plan -head "$(git rev-parse HEAD)" -picks 'backtest-service 1.8.0, frontend-web'
go run ./cmd/set-image-tag -tags '{"user-service":"1.8.0"}' -built "$(git rev-parse HEAD)" ../helm/ggeolmuse/values-prod.yaml user-service
helm template ggeolmuse ../helm/ggeolmuse -f ../helm/ggeolmuse/values-prod.yaml > /tmp/rendered.yaml && go run ./cmd/netpol-check /tmp/rendered.yaml
go run ./cmd/image-check /tmp/rendered.yaml
go run ./cmd/ci-changes -event pull_request -base origin/master
go run ./cmd/chat-coverage ../backend/chat-service/coverage.json 90 80
go run ./cmd/rollout-check -values ../helm/ggeolmuse/values-prod.yaml
go run ./cmd/db-check trade 'SELECT count(*) FROM trades'
go run ./cmd/db-check -render trade 'SELECT count(*) FROM trades' > /tmp/db-check.yaml
```
