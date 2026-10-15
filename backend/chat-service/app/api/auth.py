"""Authorization 헤더 → 사용자 ID"""
from __future__ import annotations

from typing import Callable

from fastapi import Header, Request

from app.api.errors import Unauthorized
from app.core.models import AuthUnavailable

VerifyFn = Callable[[str], dict]


def extract_user_id(authorization: str | None, verify: VerifyFn) -> str:
    """토큰의 sub. 공개키를 못 받으면 AuthUnavailable, 나머지 실패는 Unauthorized"""
    if not authorization or not authorization.startswith("Bearer "):
        raise Unauthorized("Authorization 헤더 없음")
    token = authorization[len("Bearer "):]
    try:
        claims = verify(token)
    except AuthUnavailable:
        raise
    except Exception as e:
        raise Unauthorized(f"토큰 검증 실패: {e}") from e
    sub = claims.get("sub")
    if not sub:
        raise Unauthorized("sub 클레임 없음")
    return sub


def current_user_id(request: Request, authorization: str | None = Header(default=None)) -> str:
    return extract_user_id(authorization, request.app.state.verify_token)
