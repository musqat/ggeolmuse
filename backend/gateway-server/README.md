# Gateway Server

Spring Cloud Gateway API Gateway 서비스

## 주요 기능

- 중앙화된 API 라우팅 (모든 요청의 진입점)
- JWT 토큰 검증 (Keycloak 연동)
- Redis 기반 분산 Rate Limiting
- Circuit Breaker (장애 서비스 격리)
- CORS 처리

**의존 서비스:** Keycloak, Redis
