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

master 에 머지하고 CI 가 초록이면 `릴리스` 워크플로(`.github/workflows/release.yml`)가 바뀐 서비스만
커밋 SHA 7자리 태그로 빌드 · push 하고, `helm/ggeolmuse/values-prod.yaml` 의 태그를 바꾼 커밋을 master 에 올린다.
ArgoCD 가 그 커밋을 보고 롤아웃한다. CI 의 이미지 빌드는 Trivy 검사용이라 push 하지 않고, E2E 는 기다리지 않는다.

    코드 머지  →  CI 초록  →  바뀐 서비스 고르기  →  빌드 · push  →  태그 커밋(봇)  →  ArgoCD sync

- 서비스마다 운영 태그부터 바뀐 것만 빌드한다(테스트 · 문서만이면 없음, BOM · common · messaging 이면 Java 여섯)
- 실패 · 취소된 실행은 다음 실행이 이어 받고, 다시 돌려도 된다
- 더 새 커밋이 나간 서비스는 건너뛴다(옛 실행을 다시 돌려도 안 되돌아감)
- 같은 태그 이미지가 있으면 빌드를 건너뛰고, 태그는 이미지를 확인한 뒤 바꾼다
- 마이그레이션도 바로 나간다. 옛 이미지로도 돌게 쓰고(열 추가 먼저, `DROP` 은 다음 PR), 데이터를 지우면 머지 전 RDS 수동 스냅숏
- 되돌리기는 봇 태그 커밋 revert(메시지에 `[skip ci]`), DB 는 RDS 자동 백업(7일)
- push 토큰은 `DOCKERHUB_PUSH_TOKEN`(GitHub 시크릿)

Java 서비스는 멀티스테이지 Dockerfile 이고 빌드 컨텍스트가 레포 루트다.
`ggeolmuse-bom` 을 먼저 설치해야 해서다. frontend 와 chat-service 만 자기 폴더가
컨텍스트다.

설정을 함께 바꿨다면 config 저장소도 같이 올린다. 코드와 설정이 짝인 변경은
한쪽만 배포하면 기본값으로 돌아간다.
