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

master 에 머지하고 CI 가 초록이면 `릴리스` 워크플로(`.github/workflows/release.yml`)가 머지한 PR 본문의
`## 빌드할 서비스` 칸에서 체크한 서비스 가운데 운영 태그부터 바뀐 것만 빌드 · push 하고,
`helm/ggeolmuse/values-prod.yaml` 의 태그를 바꾼 커밋을 master 에 올린다.
ArgoCD 가 그 커밋을 보고 롤아웃한다. CI 의 이미지 빌드는 Trivy 검사용이라 push 하지 않고, E2E 는 기다리지 않는다.

    코드 머지  →  CI 초록  →  PR 에서 체크한 서비스 고르기  →  빌드 · push  →  태그 커밋(봇)  →  ArgoCD sync

- 태그는 서비스 뒤에 적은 버전(`- [x] backtest-service 1.8.0`)이고, 비우면 커밋 SHA 7자리다
- 버전이 운영 값 파일 이력의 가장 높은 버전 이하면 실패한다(되돌린 뒤에도 하한은 그대로라 다음은 더 높은 버전)
- 서비스마다 운영 태그부터 바뀐 것만 빌드한다(테스트 · 문서만이면 없음, BOM · common · messaging 이면 Java 여섯). 체크했지만 바뀐 것이 없으면 건너뛴다
- 바뀌었지만 체크하지 않은 서비스는 보류다. 뒤 PR 에서 체크하면 그때 나간다
- 칸이 없는 PR 과 PR 없는 직접 커밋은 아무것도 내지 않는다
- 본문은 릴리스의 plan 잡이 돌 때 읽는다. 본문을 고쳤으면 그 릴리스 실행을 모든 작업 다시 실행(Re-run all jobs)으로 돌린다.
  실패한 작업만 다시 돌리면 처음 고른 서비스와 태그가 그대로 쓰인다
- 실패 · 취소된 실행은 다음 실행이 이어받지 않는다. 본문을 고치지 않았으면 실패한 작업만 다시 돌려도 된다
- CI 가 실패 · 취소된 머지는 릴리스가 건너뛴다. 그 CI 실행을 다시 돌려 초록이 되면 릴리스도 돈다
- 더 새 커밋이 나간 서비스는 건너뛴다(옛 실행을 다시 돌려도 안 되돌아감)
- Hub 에 같은 태그가 있으면 이미지 레이블의 커밋을 대조해 같으면 빌드를 건너뛰고, 다르면 실패한다
- 태그 커밋 직전에 버전 하한과 이미지 레이블을 다시 본다
- 봇 커밋 제목은 `chore(release): backtest-service 1.8.0 · frontend-web (8fbaacf)` 모양이고, 끝 괄호가 빌드한 커밋이다
- 마이그레이션도 바로 나간다. 옛 이미지로도 돌게 쓰고(열 추가 먼저, `DROP` 은 다음 PR), 데이터를 지우면 머지 전 RDS 수동 스냅숏
- 되돌리기는 봇 태그 커밋 revert(메시지에 `[skip ci]`), DB 는 RDS 자동 백업(7일)
- push 토큰은 `DOCKERHUB_PUSH_TOKEN`(GitHub 시크릿)

Java 서비스는 멀티스테이지 Dockerfile 이고 빌드 컨텍스트가 레포 루트다.
`ggeolmuse-bom` 을 먼저 설치해야 해서다. frontend 와 chat-service 만 자기 폴더가
컨텍스트다.

설정을 함께 바꿨다면 config 저장소도 같이 올린다. 코드와 설정이 짝인 변경은
한쪽만 배포하면 기본값으로 돌아간다.
