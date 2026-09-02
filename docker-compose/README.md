# Docker Compose

## 프로젝트에서의 사용

초기 개발 단계에서 로컬 환경 구성을 위해 사용했다.

```bash
docker-compose up -d  # 전체 스택 한 번에 실행
```

**선택한 이유:**
- 6개 마이크로서비스 + 인프라(Redis, Keycloak, Kafka)를 한 번에 실행 가능
- 서비스 간 의존성 자동 관리 (`depends_on`)
- 로컬 개발/테스트에 충분

**한계:**
- 단일 호스트만 지원 (서버 다운 시 전체 중단)
- 자동 복구/스케일링 없음
- 프로덕션 배포에 부적합

이후 프로덕션 요구사항(고가용성, 자동 스케일링)을 충족하기 위해 Kubernetes로 전환했다.

---

## 현재 상태

**사용 중:**
- 로컬 개발 환경
- CI/CD 통합 테스트

**사용 안 함:**
- 프로덕션 배포 (Kubernetes + Helm 사용)

---

## 서비스 구성

호스트에서 접속할 때 쓰는 포트다.

| 서비스 | 포트 | 역할 |
|--------|------|------|
| frontend-web | 3000 | 웹 화면 |
| gateway-server | 8070 | API Gateway |
| config-server | 8888 | 중앙 설정 관리 |
| user-service | 8085 | 사용자 관리 (컨테이너 내부는 8080) |
| trade-service | 8081 | 거래 관리 |
| backtest-service | 8082 | 백테스팅 |
| market-data-service | 8083 | 시장 데이터 |
| chat-service | 8000 | AI 챗봇 (`--profile ai`) |
| keycloak | 8080 | 인증 서버 |
| redis | 6379 | 캐시 |
| zookeeper | 2181 | Kafka 코디네이터 |
| kafka | 9092 / 9094 | 이벤트 브로커 |

user-service가 8085인 건 호스트 8080을 keycloak이 쓰기 때문이다.
게이트웨이가 라우팅하므로 평소엔 8070만 쓰면 된다.

각 서비스의 actuator는 management 포트 9090에 있고 호스트로 열지 않는다.
컨테이너 네트워크 안에서 `user-service:9090/actuator/prometheus` 처럼 접근한다.

### 관측성

| 서비스 | 포트 | 역할 |
|--------|------|------|
| prometheus | 9091 | 메트릭 수집 (컨테이너 내부는 9090) |
| grafana | 3001 | 대시보드 (로그인 없음) |
| tempo | - | 트레이스 저장 (호스트로 열지 않음) |

포트를 비켜 쓴 이유는 두 가지다. 9090은 각 서비스의 actuator 포트와 겹치고,
3000은 frontend-web이 쓴다.

설정은 `observability/` 아래에 있다.

---

## 빠른 시작

```bash
cd docker-compose
docker-compose up -d              # 전체 시작
docker-compose logs -f user-service  # 로그 확인
docker-compose down               # 종료
```

---

## 자주 겪는 문제

### Kafka가 `NodeExists`로 죽는다

로그 끝에 이런 줄이 있으면 이 경우다.

```
ERROR Exiting Kafka due to fatal exception during startup. (kafka.Kafka$)
org.apache.zookeeper.KeeperException$NodeExistsException: KeeperErrorCode = NodeExists
	at kafka.zk.KafkaZkClient.registerBroker(KafkaZkClient.scala:106)
```

Kafka는 부팅할 때 ZooKeeper에 `/brokers/ids/1` 임시 노드를 만들어 자기를
등록한다. 이 노드는 ZooKeeper 세션이 살아 있는 동안만 존재한다.

Kafka가 갑자기 죽으면 ZooKeeper는 세션이 끊긴 걸 바로 알지 못한다. 세션이
만료되기 전에 Kafka를 다시 띄우면 노드가 아직 남아 있어 등록에 실패하고,
브로커가 그대로 종료된다.

`docker compose down` 없이 중단됐을 때 나온다. Docker Desktop을 그냥 끄거나,
PC가 절전으로 들어가거나, compose가 도중에 실패한 경우다.

30초쯤 기다렸다가 Kafka만 다시 띄운다.

```bash
docker compose up -d kafka
```

그래도 같은 로그가 나오면 ZooKeeper를 재시작해 세션을 정리한다.

```bash
docker compose restart zookeeper
docker compose up -d kafka
```

볼륨은 지우지 않아도 된다. 데이터가 아니라 세션 문제다.

Kafka가 healthy가 되면 여기에 의존하는 user / trade / market-data /
backtest가 뜬다.

```bash
docker compose up -d
```

### 컨테이너 이름이 이미 사용 중이라고 나온다

```
Conflict. The container name "/ggeolmuse-keycloak" is already in use
```

compose를 두 곳에서 동시에 돌리면 나온다. 앞선 실행이 끝났는지 먼저 본다.

```bash
docker compose ps -a
```

`Created` 상태로 멈춰 있으면 그대로 다시 올리면 된다. compose가 그 컨테이너를
재사용한다.

```bash
docker compose up -d
```

### Windows에서 `local-up.sh`가 안 돈다

PowerShell의 `bash`는 Git Bash가 아니라 WSL bash(`C:\Windows\system32\bash.exe`)다.
WSL이 없으면 실패한다. `&&`도 Windows PowerShell 5.1에서는 파서 오류가 난다.

`.env`가 이미 있으면 스크립트를 건너뛰고 compose를 직접 부르면 된다.

```powershell
Set-Location <repo>\docker-compose
docker compose up --build -d
```

스크립트를 그대로 쓰려면 Git Bash를 명시한다.

```powershell
& "C:\Program Files\Git\bin\bash.exe" -c "cd /d/<repo> && bash docker-compose/local-up.sh"
```

---

## 다음 단계

Docker Compose의 한계를 극복하기 위해 **Kubernetes**로 전환
→ 자세한 내용: `../k8s/README.md`
