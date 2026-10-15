"""Authorization 헤더 → 사용자 ID"""
from __future__ import annotations

from typing import Callable

from fastapi import Header, HTTPException, Request

from app.core.models import AuthUnavailable

VerifyFn = Callable[[str], dict]


class AuthError(Exception):
    pass


def extract_user_id(authorization: str | None, verify: VerifyFn) -> str:
    """토큰의 sub. 공개키를 못 받으면 AuthUnavailable, 나머지 실패는 AuthError"""
    if not authorization or not authorization.startswith("Bearer "):
        raise AuthError("Authorization 헤더 없음")
    token = authorization[len("Bearer "):]
    try:
        claims = verify(token)
    except AuthUnavailable:
        raise
    except Exception as e:
        raise AuthError(f"토큰 검증 실패: {e}") from e
    sub = claims.get("sub")
    if not sub:
        raise AuthError("sub 클레임 없음")
    return sub


def current_user_id(request: Request, authorization: str | None = Header(default=None)) -> str:
    try:
        return extract_user_id(authorization, request.app.state.verify_token)
    except AuthUnavailable:
        raise HTTPException(status_code=503, detail="인증 서버에 연결할 수 없습니다.")
    except AuthError as e:
        raise HTTPException(status_code=401, detail=str(e))
