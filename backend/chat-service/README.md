# Chat Service

AI 종목 대화 서비스 (FastAPI + OpenAI)

## 주요 기능

- 종목 하나를 정하고 그 종목의 기술 지표 · 주가 추이 · 배당을 묻는 대화
- 차트 페이지의 "AI 분석" 버튼 → 그 종목으로 대화 시작
- 질문마다 맞는 분석(스킬)을 작은 모델이 고른다. 메뉴는 없다
- 이어 묻기: 최근 3턴을 Redis 에 둔다(마지막 답부터 5시간)
- 일일 사용 한도 (사용자당 5회/일, 한국 날짜, Redis 카운터). 거절은 하루 20번까지 세지 않는다
- 범위 제한: 한 종목씩 · 시세 · 배당 데이터만(실적/뉴스 미반영) · 투자 자문 아님(면책 명시)

**의존 서비스:** Market Data Service (현재가 · 일봉 · 배당 내부 API), Redis, Keycloak (JWT)

## 구조

```
app/
  main.py   조립: 시작할 때 클라이언트를 만들어 흐름에 넣고, 끝날 때 닫는다
  api/      HTTP. 요청 · 응답 변환, 예외 → {code, message}, 요청 로그
  core/     흐름 · 스킬 고르기 · 답변 · 스킬 · 계산. FastAPI · OpenAI · Redis 를 모른다
  infra/    OpenAI · market-data · Redis · Keycloak 어댑터
```

의존 방향은 `api → core ← infra` 다. core 는 `core/ports.py` 의 Protocol 만 보고 infra 가 구현한다.
스킬을 더할 때는 `core/skills/` 에 파일 하나를 더하고 `build_skills` 에 넣는다. 스킬 고르기 프롬프트는 스킬의 이름 · 설명으로 만든다.

## 설계 포인트

**토큰 비용**
- `gpt-4o-mini` 는 스킬 고르기, `gpt-4o` 는 답에만 쓴다. 질문 하나에 AI 호출은 두 번
- 지표 · 수익률 · 배당은 서버(Python)에서 계산해 숫자만 넘긴다

**차트와 같은 숫자**
- 지표는 프론트 차트(klinecharts 9.8.12)와 같은 식 · 같은 가격(분할만 조정한 종가)으로 계산한다

**환각 방지**
- 계산한 값만 자료로 넣고, 자료에 없는 값은 없다고 답하게 한다
- 시스템 프롬프트로 목표가 · 매수매도 단정 · 확언을 막는다

**장애**
- 시세 5초 · 스킬 고르기 5초 · 답 15초에 끊고 재시도하지 않는다. 게이트웨이 30초 안에 끝난다
- 거절 · 장애면 한도 칸을 돌려준다

**처리 흐름**
```
POST /api/chat/conversations { symbol }   (JWT)
  → 형식 확인 → market-data 현재가로 종목 · 상장 여부 확인 → 대화 생성
  → { conversation_id, symbol, name, remaining }

POST /api/chat { conversation_id, message }   (JWT)
  → 대화 불러오기 → 한도 한 칸 → gpt-4o-mini 스킬 고르기
  → 스킬이 market-data 자료로 계산 → gpt-4o 답 + 면책 → 턴 저장
  → { answer, remaining, conversation_id, symbol, rejected }

GET /api/chat/quota   (JWT)
  → 오늘 남은 질문 수. 한도를 쓰지 않는다
  → { remaining }
```

오류 본문은 `{ code, message }` 다. `unauthorized`(401), `symbol_not_found` · `conversation_not_found`(404), `quota_exceeded`(429), `market_unavailable` · `ai_unavailable` · `auth_unavailable`(503), `internal_error`(500).

## 기술 스택

Python 3.12 · FastAPI · OpenAI SDK(gpt-4o / gpt-4o-mini) · Redis · python-jose(JWT) · httpx
