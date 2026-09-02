# Chat Service

AI 종목 기술 분석 서비스 (FastAPI + OpenAI)

## 주요 기능

- 특정 종목의 기술적 분석 (이동평균, RSI, MACD, 볼린저밴드, 스토캐스틱, 거래량)
- 차트 페이지에서 원클릭 "AI 분석" → 현재 종목 자동 분석
- 일일 사용 한도 (사용자당 5회/일, Redis 카운터)
- 핵심 지표·결론만 굵게 강조 (마크다운 bold)
- 범위 제한: 한 종목씩 · 기술적 지표만(펀더멘털/뉴스 미반영) · 투자 자문 아님(면책 명시)

**의존 서비스:** Market Data Service (OHLC 조회), Redis, Keycloak (JWT)

## 설계 포인트

플랫폼 유일의 Python 서비스로, 기존 Java 마이크로서비스와 동일 게이트웨이로 통합.

**토큰 비용 최적화**
- `gpt-4o-mini`로 종목 추출(라우팅) + 가드 → 싸고 빠름
- 지표는 서버(Python)에서 직접 계산 → raw OHLC 대신 압축 요약만 LLM에 주입
- `gpt-4o`는 최종 분석 답변에만 사용

**환각 방지**
- 실제 계산된 지표값만 주입 (모델이 수치를 지어내지 않음)
- 시스템 프롬프트로 목표가/매수매도 단정, 펀더멘털 언급 금지

**처리 흐름**
```
POST /api/chat { message }  (JWT)
  → JWT 검증 → 일일 한도 체크 → gpt-4o-mini 종목 추출
  → market-data OHLC 조회 → Python 지표 계산/요약
  → gpt-4o 분석 + 면책 → { answer, remaining, symbol }
```

## 기술 스택

Python 3.12 · FastAPI · OpenAI SDK(gpt-4o / gpt-4o-mini) · pandas(지표 직접 계산) · Redis · python-jose(JWT) · httpx
