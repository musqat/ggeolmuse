# Gateway Server

Spring Cloud Gateway API Gateway 서비스

## 주요 기능

- 중앙화된 API 라우팅 (모든 요청의 진입점)
- JWT 토큰 검증 (Keycloak 연동)
- Redis 기반 분산 Rate Limiting
- Circuit Breaker (장애 서비스 격리)
- CORS 처리

## 시스템 내 역할

**핵심 기능:**
- 6개 마이크로서비스에 대한 단일 진입점
- JWT 기반 인증 및 권한 검증
- IP 기반 Rate Limiting (Redis Token Bucket)
- 장애 서비스 격리 및 Fallback 응답
