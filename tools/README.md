# tools

CI · 릴리스 워크플로와 운영 확인에 쓰는 Go 명령(테스트 포함). 릴리스 쪽은 표준 라이브러리만, netpol-check 는 쿠버네티스 타입(`k8s.io/api`), rollout-check 는 client-go 를 쓴다.

| 명령 | 하는 일 |
|---|---|
| `cmd/release-plan` | 다시 빌드 · 배포할 서비스 고르기(`-head` 면 운영 태그부터, 아니면 표준 입력의 파일 목록). `-head` 와 `-body` 를 함께 주면 운영 태그부터 바뀐 서비스 가운데 그 PR 본문 빌드할 서비스 칸에서 체크한 것만 내고, 서비스별 태그(버전, 비우면 SHA 7자리) · 보류(바뀌었지만 체크 안 함) · 체크했지만 바뀐 것 없음을 함께 출력. 버전이 운영 값 파일 이력의 가장 높은 버전 이하면 실패. `-body` 대신 `-picks`(쉼표 목록, 손 릴리스)도 같은 검사. `-only` 에 `-tags` 를 함께 주면 다시 고른 서비스의 버전도 같은 하한으로 확인 |
| `cmd/set-image-tag` | 운영 값 파일의 이미지 태그 줄만 바꾸기. `-tags` · `-built`(커밋 SHA) 를 주면 서비스마다 그 태그로 바꾸고 봇 커밋 제목을 출력 |
| `cmd/netpol-check` | 렌더한 매니페스트에서 business 워크로드(네이티브 사이드카 포함)의 containerPort 를 그 파드를 고르는 NetworkPolicy 가 게이트웨이 파드에서 여는지 대조. 선택자는 실제 파드 라벨에 대 보고 프로토콜 · 포트 범위 · 생략도 읽음(빠진 포트 · 이름 포트 · 게이트웨이나 정책 없음이면 실패) |
| `cmd/ci-changes` | CI 가 빌드 · 테스트할 대상 고르기(push 는 전부, PR 은 베이스에서 갈라진 뒤 바뀐 폴더만, CI 설정 · 공용 액션 · 이 명령 · 메이븐 설정이 바뀌면 전부). `services` · `frontend` · `chat` 세 줄 출력, 옮기기 전 bash 는 `.github/scripts/backup` |
| `cmd/image-check` | 렌더한 매니페스트의 이미지 태그가 Docker Hub 에 있는지 대조(공식 이미지는 `library/`, 429 · 5xx 는 다시 물음). 다른 레지스트리 · digest 고정은 건너뛰고, 없는 태그나 확인 실패가 있으면 실패 |
| `cmd/rollout-check` | 네임스페이스(기본 `default`)의 Deployment 마다 이미지 태그 · READY · 재시작 수를 한 줄씩 출력. `-values` 를 주면 운영 값 파일의 서비스 태그와 대조. 롤아웃이 덜 끝났거나(`kubectl rollout status` 와 같은 기준) 태그가 다르거나 값 파일의 서비스가 클러스터에 없으면 실패, 재시작 수는 출력만 |
| `internal/release` | 릴리스 봇 커밋 제목 만들기 · 읽기(끝 괄호의 빌드한 커밋) |
| `internal/values` | 값 파일 읽기 · 쓰기 |
| `internal/kube` | kubeconfig(비우면 `KUBECONFIG` · `~/.kube/config`)로 클러스터 클라이언트 만들기 |

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
go run ./cmd/image-check /tmp/rendered.yaml
go run ./cmd/ci-changes -event pull_request -base origin/master
go run ./cmd/rollout-check -values ../helm/ggeolmuse/values-prod.yaml
```
