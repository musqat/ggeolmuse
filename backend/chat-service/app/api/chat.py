from __future__ import annotations

import asyncio
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException

from app.api.auth import current_user_id
from app.api.deps import get_chat_model, get_market_client, get_rate_limiter
from app.api.schemas import ChatRequest, ChatResponse
from app.config import settings
from app.core.analysis.indicators import build_indicator_summary
from app.core.intent import IntentResult, extract_symbol
from app.core.models import AiUnavailable, MarketUnavailable
from app.infra.llm import ChatModel
from app.infra.market_api import MarketClient
from app.infra.quota import RateLimiter

router = APIRouter()


@router.post("/api/chat", response_model=ChatResponse)
async def chat(
    req: ChatRequest,
    user_id: str = Depends(current_user_id),
    limiter: RateLimiter = Depends(get_rate_limiter),
    market: MarketClient = Depends(get_market_client),
    model: ChatModel = Depends(get_chat_model),
):
    # 1. 일일 한도. 먼저 한 칸을 잡고, 답을 못 주면(거절 · 실패) 돌려준다. 거절이 하루 한도를 넘으면 안 돌려준다
    slot = await limiter.try_acquire(user_id)
    if slot is None:
        raise HTTPException(status_code=429, detail="오늘 사용 한도를 초과했습니다.")
    try:
        answer, symbol, answered = await _analyze(req, market, model)
    except MarketUnavailable:
        await limiter.release(slot)
        raise HTTPException(status_code=503, detail="시세 데이터 조회에 실패했습니다.")
    except AiUnavailable:
        await limiter.release(slot)
        raise HTTPException(status_code=503, detail="AI 응답 생성에 실패했습니다.")
    except Exception:
        await limiter.release(slot)
        raise
    if not answered and await limiter.count_reject(user_id):
        await limiter.release(slot)
    return ChatResponse(
        answer=answer,
        remaining=await limiter.remaining(user_id),
        symbol=symbol,
    )


# 종목을 정하고 분석한다. 거절이면 answered=False
async def _analyze(
    req: ChatRequest, market: MarketClient, model: ChatModel
) -> tuple[str, str | None, bool]:
    # 2. 종목 결정
    supported = await market.fetch_supported_symbols()
    if req.symbol:
        # 차트 등에서 종목 확정 전달 → mini 추출 생략, 지원종목 가드만
        sym = req.symbol.strip().upper()
        if sym not in supported:
            return f"{sym}는 지원하지 않는 종목입니다.", sym, False
        intent = IntentResult(symbol=sym, valid=True, reason=None)
    else:
        # 자유 입력 → mini로 종목 추출 + 가드
        intent = await extract_symbol(req.message, supported, mini_call=model.route)
        if not intent.valid:
            return intent.reason, intent.symbol, False

    # 3. OHLC 조회
    end = date.today()
    start = end - timedelta(days=settings.ohlc_lookback_days)
    ohlc = await market.fetch_ohlc_range(intent.symbol, start.isoformat(), end.isoformat())

    # 4. 지표 계산
    summary = build_indicator_summary(ohlc)
    if summary is None:
        return f"{intent.symbol}의 분석 가능한 데이터가 충분하지 않습니다.", intent.symbol, False

    # 5. 분석 생성
    # 동기 OpenAI 호출. 스레드에서 돌린다
    answer = await asyncio.to_thread(model.analyze, intent.symbol, summary, req.message)
    return answer, intent.symbol, True
