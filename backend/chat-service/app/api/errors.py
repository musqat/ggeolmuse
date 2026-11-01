"""도메인 예외 → HTTP 상태와 본문 {code, message}"""
from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.core.messages import ERRORS
from app.core.models import (
    AiUnavailable,
    AuthUnavailable,
    ConversationNotFound,
    MarketUnavailable,
    QuotaExceeded,
    SymbolNotFound,
)

log = logging.getLogger("chat")


class Unauthorized(Exception):
    """토큰이 없거나 틀림"""


ERROR_CODES: dict[type[Exception], tuple[int, str]] = {
    SymbolNotFound: (404, "symbol_not_found"),
    ConversationNotFound: (404, "conversation_not_found"),
    QuotaExceeded: (429, "quota_exceeded"),
    Unauthorized: (401, "unauthorized"),
    AuthUnavailable: (503, "auth_unavailable"),
    MarketUnavailable: (503, "market_unavailable"),
    AiUnavailable: (503, "ai_unavailable"),
}


OUTAGES = ("auth_unavailable", "market_unavailable", "ai_unavailable")


def error_code(exc: Exception) -> tuple[int, str]:
    """예외의 상태와 코드(모르는 예외는 500 internal_error)"""
    for kind, value in ERROR_CODES.items():
        if isinstance(exc, kind):
            return value
    return 500, "internal_error"


def error_response(exc: Exception) -> JSONResponse:
    status, code = error_code(exc)
    return JSONResponse(status_code=status, content={"code": code, "message": ERRORS[code]})


def log_value(value: str) -> str:
    """로그에 넣는 값(줄바꿈 · 공백이 있으면 repr 로 적어 한 줄 · 한 칸 유지)"""
    return value if value.isprintable() and " " not in value else repr(value)


def log_detail(exc: Exception) -> str:
    """장애면 원인(' detail=…'), 아니면 빈 문자열(사용자 값이 든 4xx 예외 문구는 안 남김)"""
    return f" detail={log_value(str(exc))}" if error_code(exc)[1] in OUTAGES else ""


def log_level(exc: Exception) -> int:
    """장애(5xx)는 WARNING, 예상하지 못한 예외는 ERROR, 나머지는 INFO"""
    status, code = error_code(exc)
    if code == "internal_error":
        return logging.ERROR
    return logging.WARNING if status >= 500 else logging.INFO


def register_error_handlers(app: FastAPI) -> None:
    """엔드포인트 밖(인증 의존성)에서 난 도메인 예외 → 같은 본문"""

    async def handle(request: Request, exc: Exception) -> JSONResponse:
        if error_code(exc)[0] >= 500:
            log.warning("request path=%s outcome=error:%s%s", request.url.path, error_code(exc)[1],
                        log_detail(exc))
        return error_response(exc)

    for kind in ERROR_CODES:
        app.add_exception_handler(kind, handle)
