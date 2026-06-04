# GGeolmuse

**Live Demo**: https://ggeolmuse.com

[![CI/CD](https://github.com/musqat/ggeolmuse/actions/workflows/ci.yml/badge.svg)](https://github.com/musqat/ggeolmuse/actions)
[![Security Scan](https://github.com/musqat/ggeolmuse/actions/workflows/nightly-security-scan.yml/badge.svg)](https://github.com/musqat/ggeolmuse/actions)

미국 주식 데이터 기반 투자 전략 백테스팅 플랫폼

<div align="center">
  <img src=".github/images/main/메인페이지.png" alt="GGeolmuse 메인 화면" width="800"/>
</div>

## 서비스 개요

NYSE, NASDAQ, NYSE ARCA 상장 11,000개 이상 종목의 20년치 일별 가격 데이터로 투자 전략을 백테스팅합니다.
환율 변동, 배당 재투자, 실제 수수료까지 반영해 실전에 가까운 시뮬레이션을 제공합니다.

**주요 기능**
- 실시간 주식 시세 조회 및 과거 OHLC 데이터 제공
- 5가지 투자 전략 백테스팅 (단순, 적립식, 조건부 매매, 전략 비교, 종목 비교)
- 환율 변동을 고려한 원화/달러 수익률 계산
- 배당금 자동 재투자 시뮬레이션
- 실제 거래 수수료 및 슬리피지 반영
- AI 종목 기술 분석 (지표 기반 챗봇, 차트에서 원클릭 분석)

**기술 스택**
- Backend: Spring Boot, Spring Cloud (Java) · FastAPI (Python, AI 챗봇)
- AI: OpenAI (gpt-4o / gpt-4o-mini)
- Data: PostgreSQL, Redis, Kafka
- Frontend: React, TypeScript
- Infrastructure: Kubernetes, Helm, ArgoCD

---

## 마이크로서비스

| Service | 설명 | README |
|---------|------|--------|
| **Config Server** | 중앙 설정 관리 | [📄](config-server/README.md) |
| **Gateway Server** | API Gateway | [📄](gateway-server/README.md) |
| **User Service** | 인증, 계좌 관리, 환전 | [📄](user-service/README.md) |
| **Trade Service** | 거래 실행, 포트폴리오 관리 | [📄](trade-service/README.md) |
| **Market Data Service** | 시세 데이터 수집/제공 | [📄](market-data-service/README.md) |
| **Backtest Service** | 투자 전략 백테스팅 | [📄](backtest-service/README.md) |
| **Chat Service** | AI 종목 기술 분석 (FastAPI + OpenAI) | [📄](chat-service/README.md) |

## 인프라 & 배포

| 항목       | 설명 | README |
|----------|------|--------|
| **helm** | AWS 아키텍처, 비용 최적화, CI/CD, 모니터링, 트러블슈팅 | [📄](helm/README.md) |

---

## 아키텍처

**Microservices Architecture**
- 7개 독립 서비스로 구성 (Java 6 + Python 1, 서비스별 독립 배포·스케일링 경험 목적, 실제 운영은 단일 EC2)
- Kafka 기반 이벤트 드리븐 아키텍처
- Spring Cloud Gateway로 라우팅 통합 (이종 언어 서비스도 동일 게이트웨이로 통합)

**인프라**
- Kubernetes (K3s) 기반 컨테이너 오케스트레이션
- ArgoCD로 GitOps 배포
- Prometheus + Grafana 모니터링

---

## 프로젝트 구조

```
ggeolmuse/
├── backtest-service/          # 백테스팅 시뮬레이션
├── trade-service/             # 거래 및 포트폴리오 관리
├── user-service/              # 인증 및 계좌 관리
├── market-data-service/       # 시세 데이터 수집/제공
├── chat-service/              # AI 종목 기술 분석 (FastAPI + OpenAI)
├── gateway-server/            # API Gateway
├── config-server/             # 중앙 설정 관리
├── ggeolmuse-bom/             # 공통 라이브러리 (예외처리, 로깅, 유틸)
├── messaging/                 # Kafka 메시징 공통 라이브러리
└── frontend-web/              # React 프론트엔드
```

<img width="850" height="600" alt="껄무새 drawio (2)" src="https://github.com/user-attachments/assets/e54861a9-3a24-40f5-bf48-c0e0c55ada22" />

---

## 성능 최적화

**데이터 처리**
- Bulk API 패턴: API 호출 99% 감소 (2000+ → 2-4 calls)
- Redis 캐싱: 응답시간 90% 개선 (1.2s → 150ms)
- 데이터베이스 인덱싱: 시가총액 정렬 조회 98% 개선 (2.5s → 50ms)

**안정성**
- Circuit Breaker로 장애 서비스 격리, Retry 정책으로 일시적 오류 복구
- API 과부하는 Rate Limiting으로 방지

**운영 자동화**
- GitHub Actions CI/CD 파이프라인
- 컨테이너 보안 스캔 (Trivy, OWASP)
- 분산 추적 및 로그 수집 (OpenTelemetry, Loki)

---

## 프로젝트 규모

**데이터**
- 11,000+ 미국 주식 종목
- 290만+ 일별 캔들 데이터 (최대 20년치)
- 7,000+ 환율 데이터

**성능 지표**
- Cache Hit Rate: 77%

---

## 모니터링

- **Prometheus + Grafana**: 메트릭 수집 및 시각화
- **Loki**: 로그 집계
- **Tempo + OpenTelemetry**: 분산 추적
- **ArgoCD**: GitOps 배포 관리
- **Kafka UI**: 이벤트 스트림 모니터링
