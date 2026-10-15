"""종목 대화 API. 요청마다 로그 한 줄을 남긴다"""
from __future__ import annotations

import hashlib
import logging
import time

from fastapi import APIRouter, Depends

from app.api.auth import current_user_id
from app.api.deps import get_chat_service
from app.api.errors import error_code, error_response, log_detail, log_level, log_value
from app.api.schemas import AskRequest, AskResponse, QuotaResponse, StartRequest, StartResponse
from app.core.chat_service import ChatService, Rejected

log = logging.getLogger("chat")
router = APIRouter()


def fingerprint(user_id: str) -> str:
    """로그용 사용자 지문. sub 의 SHA-256 앞 12자리"""
    return hashlib.sha256(user_id.encode()).hexdigest()[:12]


def _ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)


@router.post("/api/chat/conversations", status_code=201, response_model=StartResponse)
async def start_conversation(
    req: StartRequest,
    user_id: str = Depends(current_user_id),
    service: ChatService = Depends(get_chat_service),
):
    started = time.perf_counter()
    symbol = req.symbol.strip().upper()[:16]
    try:
        result = await service.start(user_id, req.symbol)
    except Exception as exc:
        code = error_code(exc)[1]
        outcome = code if code == "symbol_not_found" else f"error:{code}"
        log.log(log_level(exc), "start user=%s symbol=%s outcome=%s ms=%d%s",
                fingerprint(user_id), log_value(symbol), outcome, _ms(started), log_detail(exc),
                exc_info=code == "internal_error")
        return error_response(exc)
    log.info("start user=%s symbol=%s outcome=started ms=%d", fingerprint(user_id), log_value(symbol),
             _ms(started))
    conversation = result.conversation
    return StartResponse(conversation_id=conversation.id, symbol=conversation.symbol,
                         name=conversation.name, remaining=result.remaining)


@router.post("/api/chat", response_model=AskResponse)
async def ask(
    req: AskRequest,
    user_id: str = Depends(current_user_id),
    service: ChatService = Depends(get_chat_service),
):
    started = time.perf_counter()
    try:
        result = await service.ask(user_id, req.conversation_id, req.message)
    except Exception as exc:
        code = error_code(exc)[1]
        log.log(log_level(exc), "ask user=%s conv=%s outcome=error:%s ms=%d%s",
                fingerprint(user_id), log_value(req.conversation_id), code, _ms(started), log_detail(exc),
                exc_info=code == "internal_error")
        return error_response(exc)
    outcome = result.outcome
    rejected = outcome.reason if isinstance(outcome, Rejected) else None
    log.info(
        "ask user=%s conv=%s symbol=%s skill=%s outcome=%s ms=%d llm_calls=%d tokens=%d",
        fingerprint(user_id), result.conversation.id, result.conversation.symbol, result.skill or "-",
        f"rejected:{rejected}" if rejected else "answered", _ms(started), result.llm_calls, result.tokens,
    )
    return AskResponse(answer=outcome.text, remaining=result.remaining,
                       conversation_id=result.conversation.id, symbol=result.conversation.symbol,
                       rejected=rejected)


@router.get("/api/chat/quota", response_model=QuotaResponse)
async def quota(
    user_id: str = Depends(current_user_id),
    service: ChatService = Depends(get_chat_service),
):
    started = time.perf_counter()
    try:
        remaining = await service.remaining(user_id)
    except Exception as exc:
        code = error_code(exc)[1]
        log.log(log_level(exc), "quota user=%s outcome=error:%s ms=%d%s",
                fingerprint(user_id), code, _ms(started), log_detail(exc),
                exc_info=code == "internal_error")
        return error_response(exc)
    log.info("quota user=%s remaining=%d ms=%d", fingerprint(user_id), remaining, _ms(started))
    return QuotaResponse(remaining=remaining)
