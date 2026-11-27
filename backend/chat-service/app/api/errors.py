"""도메인 예외 → HTTP 상태와 ProblemDetail 본문"""
from __future__ import annotations

import logging
from datetime import datetime
from http import HTTPStatus

import redis
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
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

PROBLEM_BASE_URL = "https://api.muscat.com/problems/"


class Unauthorized(Exception):
    """토큰이 없거나 틀림"""


ERROR_CODES: dict[type[Exception], tuple[int, str]] = {
    SymbolNotFound: (404, "SYMBOL_NOT_FOUND"),
    ConversationNotFound: (404, "CONVERSATION_NOT_FOUND"),
    QuotaExceeded: (429, "QUOTA_EXCEEDED"),
    Unauthorized: (401, "UNAUTHORIZED"),
    AuthUnavailable: (503, "AUTH_UNAVAILABLE"),
    MarketUnavailable: (503, "MARKET_UNAVAILABLE"),
    AiUnavailable: (503, "AI_UNAVAILABLE"),
    # Redis 연결 실패 · 시간 초과(한도를 못 세므로 막음)
    redis.exceptions.ConnectionError: (503, "STORE_UNAVAILABLE"),
    redis.exceptions.TimeoutError: (503, "STORE_UNAVAILABLE"),
}


OUTAGES = ("AUTH_UNAVAILABLE", "MARKET_UNAVAILABLE", "AI_UNAVAILABLE", "STORE_UNAVAILABLE")

INTERNAL_ERROR = "INTERNAL_SERVER_ERROR"


def error_code(exc: Exception) -> tuple[int, str]:
    """예외의 상태와 코드(모르는 예외는 500 INTERNAL_SERVER_ERROR)"""
    for kind, value in ERROR_CODES.items():
        if isinstance(exc, kind):
            return value
    return 500, INTERNAL_ERROR


# 상태별 오류 분류(목록 밖 상태는 SYSTEM)
ERROR_TYPES = {401: "UNAUTHORIZED", 404: "NOT_FOUND", 429: "RATE_LIMIT", 503: "EXTERNAL_SERVICE"}


def problem_response(status: int, code: str, path: str, title: str | None = None,
                     error_type: str | None = None, **properties) -> JSONResponse:
    """오류 코드의 문구로 만든 ProblemDetail 응답"""
    body = {
        "type": PROBLEM_BASE_URL + code.lower().replace("_", "-"),
        "title": title or HTTPStatus(status).phrase,
        "status": status,
        "detail": ERRORS[code],
        "instance": path,
        "errorCode": code,
        "errorType": error_type or ERROR_TYPES.get(status, "SYSTEM"),
        "timestamp": datetime.now().isoformat(),
        **properties,
    }
    return JSONResponse(status_code=status, content=body, media_type="application/problem+json")


def error_response(exc: Exception, path: str) -> JSONResponse:
    status, code = error_code(exc)
    return problem_response(status, code, path)


def log_value(value: str) -> str:
    """로그에 넣는 값(줄바꿈 · 공백이 있으면 repr 로 적어 한 줄 · 한 칸 유지)"""
    return value if value.isprintable() and " " not in value else repr(value)


def log_detail(exc: Exception) -> str:
    """장애면 원인(' detail=…'), 아니면 빈 문자열(사용자 값이 든 4xx 예외 문구는 안 남김)"""
    return f" detail={log_value(str(exc))}" if error_code(exc)[1] in OUTAGES else ""


def log_level(exc: Exception) -> int:
    """장애(5xx)는 WARNING, 예상하지 못한 예외는 ERROR, 나머지는 INFO"""
    status, code = error_code(exc)
    if code == INTERNAL_ERROR:
        return logging.ERROR
    return logging.WARNING if status >= 500 else logging.INFO


def register_error_handlers(app: FastAPI) -> None:
    """엔드포인트 밖(인증 의존성 · 본문 검사)에서 난 예외 → 같은 모양의 본문"""

    async def handle(request: Request, exc: Exception) -> JSONResponse:
        if error_code(exc)[0] >= 500:
            log.warning("request path=%s outcome=error:%s%s", request.url.path, error_code(exc)[1],
                        log_detail(exc))
        return error_response(exc, request.url.path)

    async def invalid_body(request: Request, exc: RequestValidationError) -> JSONResponse:
        fields = {str(error["loc"][-1]): error["msg"] for error in exc.errors()}
        return problem_response(400, "VALIDATION_ERROR", request.url.path, title="Validation Error",
                                error_type="VALIDATION", validationErrors=fields)

    for kind in ERROR_CODES:
        app.add_exception_handler(kind, handle)
    app.add_exception_handler(RequestValidationError, invalid_body)
