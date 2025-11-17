# GGeolmuse

미국 주식 데이터 기반 투자 전략 백테스팅 플랫폼

<div align="center">
  <img src=".github/images/main/메인페이지.png" alt="GGeolmuse 메인 화면" width="800"/>
</div>

## 서비스 개요

투자자가 과거 데이터를 바탕으로 다양한 투자 전략을 시뮬레이션할 수 있는 플랫폼입니다.
NYSE, NASDAQ, NYSE ARCA에 상장된 11,000개 이상 종목의 20년치 일별 가격 데이터를 자동으로 수집합니다.

**주요 기능**
- 실시간 주식 시세 조회 및 과거 OHLC 데이터 제공
- 5가지 투자 전략 백테스팅 (단순, 적립식, 조건부 매매, 전략 비교, 종목 비교)
- 환율 변동을 고려한 원화/달러 수익률 계산
- 배당금 자동 재투자 시뮬레이션
- 실제 거래 수수료 및 슬리피지 반영

**기술 스택**
- Backend: Java 21, Spring Boot 3.3.4, Spring Cloud Gateway
- Data: PostgreSQL, Redis (caching), Kafka
- Frontend: React 18, TypeScript, Lightweight Charts
- Infrastructure: Kubernetes (K3s), Helm, ArgoCD, Prometheus/Grafana

---

## 마이크로서비스

| Service | Port | 설명 | README |
|---------|------|------|--------|
| **Config Server** | 8888 | 중앙 설정 관리 (Spring Cloud Config) | [📄](config-server/README.md) |
| **Gateway Server** | 8070 | API Gateway (라우팅, JWT, Rate Limiting) | [📄](gateway-server/README.md) |
| **User Service** | 8080 | 인증, 계좌 관리, 환전 | [📄](user-service/README.md) |
| **Trade Service** | 8081 | 거래 실행, 포트폴리오 관리 | [📄](trade-service/README.md) |
| **Market Data Service** | 8083 | 시세 데이터 수집/제공 (11k+ 종목) | [📄](market-data-service/README.md) |
| **Backtest Service** | 8082 | 투자 전략 백테스팅 (5가지 전략) | [📄](backtest-service/README.md) |

각 서비스의 상세 역할, Flow, 데이터베이스 스키마는 개별 README를 참고하세요.

---

## 기술적 특징

### 아키텍처
- Microservices Architecture (6개 독립 서비스)
- Event-Driven Architecture (Kafka)
- Observability (OpenTelemetry + Tempo)
- GitOps 배포 (ArgoCD)

### 성능 최적화
- Migration 기반 DB 인덱싱 (Partial Index, 복합 인덱스)
- QueryDSL 타입 안전 쿼리 최적화
- Redis 5-tier 캐싱 전략
- HikariCP 커넥션 풀 튜닝
- Gateway Redis 기반 분산 Rate Limiting


### 안정성 (Resilience)
- Resilience4j 5-layer 패턴
  - Circuit Breaker (장애 서비스 격리)
  - Retry (일시적 오류 복구)
  - Time Limiter (Timeout 관리)
  - Bulkhead (리소스 격리)
  - Rate Limiter (API 속도 제한)
- Custom Grafana Dashboards (비즈니스 + 기술 메트릭)

### 운영 자동화
- CI/CD: GitHub Actions + ArgoCD (5분 내 자동 배포)
- Security: Trivy 컨테이너 스캔 + OWASP 의존성 검사
- Observability: Prometheus + Grafana + Loki + Tempo + OpenTelemetry
- Self-Healing: Kubernetes health checks + auto-recovery

---

## 프로젝트 지표

**데이터 규모**
- 종목 수: 11,000+ (NYSE, NASDAQ, NYSE ARCA)
- 일일 캔들 데이터: 약 2.9M rows (11,000개 종목, 최대 20년치)
- 환율 데이터: 일 1회 수집 (USD/KRW, 약 7,000+ rows)

**성능 지표**
- 시가총액 정렬 조회: 2.5s → 50ms (50배 개선)
- 백테스트 응답 시간: 1.2s → 150ms (Redis 캐시)
- Cache Hit Rate: 85~99% (캐시 종류별)
- Kafka Consumer Throughput: 3 TPS (concurrency=3)

**인프라**
- Kubernetes Pods: 15개 (서비스 6 + 인프라 9)
- AWS EC2: t3.xlarge (4 vCPU, 16GB RAM)
- PostgreSQL RDS: db.t3.micro (1 vCPU, 1GB RAM)

---

## 문서

### Service Documentation
- **[User Service](user-service/README.md)** - 인증, 계좌 관리
- **[Trade Service](trade-service/README.md)** - 거래, 포트폴리오
- **[Market Data Service](market-data-service/README.md)** - 데이터 수집/제공
- **[Backtest Service](backtest-service/README.md)** - 전략 백테스팅
- **[Config Server](config-server/README.md)** - 중앙 설정 관리
- **[Gateway Server](gateway-server/README.md)** - API Gateway

---

## 모니터링 & 배포

- **ArgoCD**: (GitOps 배포 관리)
- **Grafana**:  (메트릭 시각화)
- **Kafka UI**: (이벤트 스트림)

---
