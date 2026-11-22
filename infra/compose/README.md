# Docker Compose

로컬 개발과 CI 의 E2E 에 쓴다. 운영 배포는 [Helm 차트](../README.md), 운영 클러스터는 [k8s](../k8s/README.md) 에 있다.

<br>

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
| postgres | - | 서비스 넷의 DB (호스트로 열지 않음) |
| zookeeper | 2181 | Kafka 코디네이터 |
| kafka | 9092 / 9094 | 이벤트 브로커 |

호스트 8080은 keycloak이 써서 user-service는 8085로 연다.
게이트웨이가 라우팅하므로 평소엔 8070 만 쓴다.
게이트웨이가 연결하는 서비스 · keycloak · redis 는 고정 주소(`10.123.0.11` ~ `10.123.0.17`)를 써서 하나만 다시 띄워도 주소가 그대로다.

각 서비스의 actuator 는 management 포트 9090 에 있고 호스트로 열지 않는다.

### DB

user · trade · market-data · backtest 는 Postgres 컨테이너 하나에 DB 를 하나씩 쓴다
(`userdb` · `tradedb` · `marketdb` · `backtestdb`). 스키마는 운영과 같은 Flyway 마이그레이션으로 만든다.
데이터는 tmpfs 에 있어 `docker compose down` 하면 사라진다. 서비스 하나만 다시 띄우면 남아 있다.

### 관측성

| 서비스 | 포트 | 역할 |
|--------|------|------|
| prometheus | 9091 | 메트릭 수집 (컨테이너 내부는 9090) |
| grafana | 3001 | 대시보드 (로그인 없음) |
| tempo | - | 트레이스 저장 (호스트로 열지 않음) |

포트를 비켜 쓴 이유는 두 가지다. 9090은 각 서비스의 actuator 포트와 겹치고,
3000은 frontend-web이 쓴다.

설정은 `observability/` 아래에 있다.

<br>

## E2E 스택

`docker-compose.e2e.yml` 을 위에 얹으면 market-data 가 `fixed-quotes` 프로필로 외부 시세 대신 jar 안 고정 데이터
(FQA · FQB · SPY, 2023-07-03 ~ 2025-06-30)를 쓴다. CI 의 E2E 와 같은 스택이다.
