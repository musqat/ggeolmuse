from __future__ import annotations

from datetime import date, timedelta

from fastapi import Depends, FastAPI, Header, HTTPException

from app.auth import AuthError, extract_user_id
from app.config import settings
from app.deps import get_market_client, get_rate_limiter
from app.indicators import build_indicator_summary
from app.intent import extract_symbol
from app.llm import call_analysis, call_router
from app.market_client import MarketClient
from app.rate_limit import RateLimiter
from app.schemas import ChatRequest, ChatResponse

app = FastAPI(title="ai-chat-service")


@app.get("/health")
def health():
    return {"status": "UP"}


def current_user_id(authorization: str | None = Header(default=None)) -> str:
    try:
        return extract_user_id(authorization)
    except AuthError as e:
        raise HTTPException(status_code=401, detail=str(e))


@app.post("/api/chat", response_model=ChatResponse)
async def chat(
    req: ChatRequest,
    user_id: str = Depends(current_user_id),
    limiter: RateLimiter = Depends(get_rate_limiter),
    market: MarketClient = Depends(get_market_client),
):
    # 1. 일일 한도
    if not await limiter.is_allowed(user_id):
        raise HTTPException(status_code=429, detail="오늘 사용 한도를 초과했습니다.")

    # 2. 종목 추출 + 가드
    supported = await market.fetch_supported_symbols()
    intent = await extract_symbol(req.message, supported, mini_call=call_router)
    if not intent.valid:
        # 거절 — 한도 차감 안 함
        return ChatResponse(
            answer=intent.reason,
            remaining=await limiter.remaining(user_id),
            symbol=intent.symbol,
        )

    # 3. OHLC 조회
    end = date.today()
    start = end - timedelta(days=settings.ohlc_lookback_days)
    try:
        ohlc = await market.fetch_ohlc_range(
            intent.symbol, start.isoformat(), end.isoformat()
        )
    except Exception:
        raise HTTPException(status_code=503, detail="시세 데이터 조회에 실패했습니다.")

    # 4. 지표 계산
    summary = build_indicator_summary(ohlc)
    if summary is None:
        return ChatResponse(
            answer=f"{intent.symbol}의 분석 가능한 데이터가 충분하지 않습니다.",
            remaining=await limiter.remaining(user_id),
            symbol=intent.symbol,
        )

    # 5. 분석 생성
    try:
        answer = call_analysis(intent.symbol, summary, req.message)
    except Exception:
        raise HTTPException(status_code=503, detail="AI 응답 생성에 실패했습니다.")

    # 6. 정상 완료 — 한도 차감
    await limiter.increment(user_id)
    return ChatResponse(
        answer=answer,
        remaining=await limiter.remaining(user_id),
        symbol=intent.symbol,
    )
