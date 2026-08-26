# 빌드와 배포

로컬에서 띄우는 방법과 운영에 올릴 때 어떤 경로를 거치는지 적었다.


## 로컬에서 띄우기

Docker Desktop 이 떠 있으면 한 번에 올라간다.

```bash
bash scripts/local-up.sh
```

접속은 http://localhost:3000, 시드 계정은 `admin@test.com` / `Admin123!` 이다.
종료는 `bash scripts/local-up.sh down`.

스크립트가 `.env` 생성 → 이미지 빌드 → 전체 스택 기동까지 한다.
직접 하려면 `cd docker-compose && docker compose up --build -d`.

AI 챗봇은 OpenAI 키가 있을 때만 뜬다. 없으면 chat-service 가 빠지고 버튼은 보이되
호출하면 안내가 뜬다.

```bash
OPENAI_API_KEY=sk-... bash scripts/local-up.sh
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

이미지를 손으로 빌드해 Docker Hub 에 올리고 helm values 의 태그를 바꾸면 ArgoCD 가
master 를 보고 sync 한다. CI 도 이미지를 빌드하지만 Trivy 로 훑어보기 위한 것이라
push 하지 않는다. 배포용 태그는 직접 만들어 올린다.

    코드 머지  →  이미지 빌드  →  Docker Hub push  →  values-prod 태그 수정  →  ArgoCD sync

Java 서비스는 멀티스테이지 Dockerfile 이고 빌드 컨텍스트가 레포 루트다.
`ggeolmuse-bom` 을 먼저 설치해야 해서다. frontend 와 chat-service 만 자기 폴더가
컨텍스트다.

태그는 `helm/ggeolmuse/values-prod.yaml` 의 서비스별 `image.tag` 다. 바꿔서 master 에
올리면 ArgoCD 가 롤아웃한다.

순서가 뒤집히면 멈춘다. 태그가 master 에 먼저 올라가고 이미지가 아직 허브에 없으면
`ImagePullBackOff` 다. push 를 끝내고 태그를 올린다.

설정을 함께 바꿨다면 config 저장소도 같이 올려야 한다. 코드와 설정이 짝인 변경은
한쪽만 배포하면 기본값으로 돌아간다.
