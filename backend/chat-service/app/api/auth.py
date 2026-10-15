"""Authorization 헤더 → 사용자 ID"""
from __future__ import annotations

from typing import Callable

from fastapi import Header, HTTPException

from app.core.models import AuthUnavailable
from app.infra.jwks import verify_token

VerifyFn = Callable[[str], dict]


class AuthError(Exception):
    pass


def extract_user_id(
    authorization: str | None, verify_fn: VerifyFn = verify_token
) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise AuthError("Authorization 헤더 없음")
    token = authorization[len("Bearer "):]
    try:
        claims = verify_fn(token)
    except AuthUnavailable:
        raise
    except Exception as e:
        raise AuthError(f"토큰 검증 실패: {e}") from e
    sub = claims.get("sub")
    if not sub:
        raise AuthError("sub 클레임 없음")
    return sub


def current_user_id(authorization: str | None = Header(default=None)) -> str:
    try:
        return extract_user_id(authorization)
    except AuthUnavailable:
        raise HTTPException(status_code=503, detail="인증 서버에 연결할 수 없습니다.")
    except AuthError as e:
        raise HTTPException(status_code=401, detail=str(e))
