# 빌드와 배포

로컬에서 띄우는 방법과 운영에 올릴 때 어떤 경로를 거치는지 적었다.


## 로컬에서 띄우기

Docker Desktop 이 떠 있으면 한 번에 올라간다.

```bash
bash docker-compose/local-up.sh
```

접속은 http://localhost:3000, 시드 계정은 `admin@test.com` / `Admin123!` 이다.
종료는 `bash docker-compose/local-up.sh down`.

스크립트가 `.env` 생성 → 이미지 빌드 → 전체 스택 기동까지 한다.
직접 하려면 `cd docker-compose && docker compose up --build -d`.

AI 챗봇은 OpenAI 키가 있을 때만 뜬다. 없으면 chat-service 가 빠지고 버튼은 보이되
호출하면 안내가 뜬다.

```bash
OPENAI_API_KEY=sk-... bash docker-compose/local-up.sh
```

알아두면 좋은 것

- 로컬 설정과 비밀번호는 전부 더미값이다 (`docker-compose/.env`, gitignore). 운영과 무관하다
- 서비스 설정은 외부 public 저장소 `musqat/ggeolmuse-config` 에서 읽는다. 인터넷이 필요하다
- 시세는 기동 약 90초 뒤 50 종목이 자동 수집된다. 그 전에는 차트가 비어 보인다
- 전체 스택은 메모리를 꽤 쓴다. 관측 스택까지 띄우면 여유가 없을 수 있다

테스트만 돌릴 거면 스택 없이 된다. Testcontainers 를 쓰는 쪽은 도커가 필요하다.

```bash
mvn test
```


## 운영에 올릴 때

master 에 머지하고 머지 커밋의 CI · E2E 가 둘 다 초록이면 `릴리스` 워크플로(`.github/workflows/release.yml`)가 머지한 PR 본문의
`## 빌드할 서비스` 칸에서 체크한 서비스 가운데 운영 태그부터 바뀐 것만 빌드 · push 하고,
`helm/ggeolmuse/values-prod.yaml` 의 태그를 바꾼 커밋을 master 에 올린다.
ArgoCD 가 태그 커밋을 보고 롤아웃하고, 릴리스 끝의 `rollout` 잡이 새 이미지 반영과 스모크 요청을 확인한다. CI 의 이미지 빌드는 Trivy 검사용이라 push 하지 않는다.

    코드 머지  →  CI · E2E 초록  →  PR 에서 체크한 서비스 고르기  →  빌드 · push  →  태그 커밋(봇)  →  ArgoCD sync

- 태그는 서비스 뒤에 적은 버전(`- [x] backtest-service 1.8.0`)이고, 비우면 커밋 SHA 7자리다
- 버전이 운영 값 파일 이력의 가장 높은 버전 이하면 실패한다(되돌린 뒤에도 하한은 그대로라 다음은 더 높은 버전)
- 서비스마다 운영 태그부터 바뀐 것만 빌드한다(테스트 · 문서만이면 없음, BOM · common · messaging 이면 Java 여섯). 체크했지만 바뀐 것이 없으면 건너뛴다
- 바뀌었지만 체크하지 않은 서비스는 보류다. 뒤 PR 에서 체크하면 그때 나간다
- 칸이 없는 PR 과 PR 없는 직접 커밋은 아무것도 내지 않는다
- 본문은 릴리스의 plan 잡이 돌 때 읽는다. 본문을 고쳤으면 그 릴리스 실행을 모든 작업 다시 실행(Re-run all jobs)으로 돌린다.
  실패한 작업만 다시 돌리면 처음 고른 서비스와 태그가 그대로 쓰인다
- 실패 · 취소된 실행은 다음 실행이 이어받지 않는다. 본문을 고치지 않았으면 실패한 작업만 다시 돌려도 된다
- CI 나 E2E 가 실패 · 취소된 머지는 릴리스가 건너뛴다. 그 push 실행을 다시 돌려 둘 다 초록이 되면 릴리스가 돈다
- 보류된 서비스는 손으로도 낼 수 있다. Actions 의 `릴리스` 를 master 에서 Run workflow 로 돌리고, 서비스와 버전을 쉼표로 적는다
  (`backtest-service 1.8.0, frontend-web`). CI · E2E 가 둘 다 초록인 가장 최근 master 커밋을 내고, 검사는 PR 칸과 같다
- 더 새 커밋이 나간 서비스는 건너뛴다(옛 실행을 다시 돌려도 안 되돌아감)
- Hub 에 같은 태그가 있으면 이미지 레이블의 커밋을 대조해 같으면 빌드를 건너뛰고, 다르면 실패한다
- 태그 커밋 직전에 버전 하한과 이미지 레이블을 다시 본다
- 봇 커밋 제목은 `chore(release): backtest-service 1.8.0 · frontend-web (8fbaacf)` 모양이고, 끝 괄호가 빌드한 커밋이다.
  본문의 `[skip ci]` 로 봇 커밋에서는 CI · E2E · 릴리스가 돌지 않는다
- 마이그레이션도 바로 나간다. 옛 이미지로도 돌게 쓰고(열 추가 먼저, `DROP` 은 다음 PR), 데이터를 지우면 머지 전 RDS 수동 스냅숏
- 되돌리기는 봇 태그 커밋을 revert 하는 PR(`[skip ci]` 없이), DB 는 RDS 자동 백업(7일)
- push 토큰은 `DOCKERHUB_PUSH_TOKEN`, 태그 커밋은 환경 `release` 의 쓰기 배포 키 `RELEASE_DEPLOY_KEY` 로 올린다.
  키가 없으면 첫 단계에서 멈추니 키를 넣고 실패한 작업만 다시 돌린다

처음 머지 전 준비: Deploy keys 에 쓰기 키 하나 등록 → 환경 `release`(배포 브랜치 master)에 `RELEASE_DEPLOY_KEY` →
ruleset `master` 우회 대상에 Deploy keys(Always), 규칙은 삭제 · 강제 푸시 금지 · PR 필수 · 필수 검사 `ci-result`(CI 잡
결과를 모은 잡) · `E2E` → enforcement Active.

Java 서비스는 멀티스테이지 Dockerfile 이고 빌드 컨텍스트가 레포 루트다.
`ggeolmuse-bom` 을 먼저 설치해야 해서다. frontend 와 chat-service 만 자기 폴더가
컨텍스트다.

설정을 함께 바꿨다면 config 저장소도 같이 올린다. 코드와 설정이 짝인 변경은
한쪽만 배포하면 기본값으로 돌아간다.

ArgoCD 자신은 `helm/argocd/application-argocd.yaml` 이 차트 버전과 `values-argocd.yaml` 로 맞춘다.
자동 동기화는 꺼 두어, 콘솔에서 바꾼 값은 OutOfSync 로만 보인다. 값을 바꾸는 PR 을 머지한 뒤
ArgoCD 화면에서 `argocd` 앱의 차이를 보고 Sync 한다. 차트 버전은 `install-argocd.sh` 와 같게 둔다.

## 배포 뒤 한 번 하는 일

PR 이 기존 데이터를 다시 계산하거나 다시 쌓아야 하면 그 PR 의 첫 배포 뒤 한 번 한다.

- 보유 평균 단가를 거래일 순서로 쌓게 바꾼 배포: 관리자 토큰으로 `POST /api/admin/trade/holdings/rebuild` 를 한 번 부르고
  응답의 `changed`(바뀐 보유 행 수)를 본다. 다시 불러도 결과가 같다
- 평균 환율을 환전한 달러 누계로 바꾼 배포(user V12): `flyway_schema_history` 에 V12 가 success 인지 보고 아래가 0행인지 본다.
  롤링 중 옛 파드가 처리한 환전이 있으면 행이 나오는데, 그 계좌의 다음 환전 때 코드가 맞추고 바로 맞추려면 V12 의 UPDATE 셋을 다시 돌린다

      SELECT id FROM account WHERE total_exchanged_krw > 0
        AND (total_exchanged_usd = 0 OR ABS(total_exchanged_krw / total_exchanged_usd - avg_exchange_rate) > 0.01);
