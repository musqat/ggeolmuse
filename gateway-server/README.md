# Gateway Server

Spring Cloud Gateway API Gateway 서비스

## 주요 기능

- 중앙화된 API 라우팅 (모든 요청의 진입점)
- JWT 토큰 검증 (Keycloak 연동)
- Redis 기반 분산 Rate Limiting
- Circuit Breaker (장애 서비스 격리)
- CORS 처리

## 시스템 내 역할

**책임:**
- 라우팅: 요청 경로에 따라 적절한 서비스로 전달
- 인증: JWT 토큰 검증 (Keycloak Public Key)
- Rate Limiting: IP 기반 속도 제한 (Redis Token Bucket)
- Circuit Breaker: 장애 서비스 차단 및 Fallback 응답
- CORS: 프론트엔드 CORS 정책 처리

**의존 서비스:**
- Keycloak: JWT 토큰 검증
- Redis: Rate Limit 카운터 저장
- Config Server: 라우팅 규칙 및 보안 설정

## 라우팅

### 서비스별 경로 매핑
- **User Service**: `/api/users/**`, `/api/auth/**`, `/api/accounts/**`
- **Trade Service**: `/api/trades/**`, `/api/holdings/**`, `/api/transactions/**`
- **Market Data Service**: `/api/market/**`, `/api/assets/**`, `/api/candles/**`
- **Backtest Service**: `/api/backtest/**`

### 보안 계층
- **Rate Limiting**: IP별 요청 속도 제한 (Redis Token Bucket)
- **Circuit Breaker**: 장애 서비스 자동 차단 및 복구
- **JWT 검증**: Keycloak Public Key로 서명 확인

### 아키텍처
- Spring WebFlux 기반 비동기 Non-Blocking I/O
- Redis 기반 분산 Rate Limiting
- Reactive 아키텍처로 높은 처리량 달성
