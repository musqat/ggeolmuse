from __future__ import annotations

from typing import Callable

from jose import jwt
from jose.exceptions import JWTError

from app.config import settings

VerifyFn = Callable[[str], dict]


class AuthError(Exception):
    pass


_jwks_cache: dict | None = None


def _default_verify(token: str) -> dict:
    global _jwks_cache
    import httpx

    if _jwks_cache is None:
        resp = httpx.get(settings.keycloak_jwks_url, timeout=10.0)
        resp.raise_for_status()
        _jwks_cache = resp.json()
    try:
        return jwt.decode(
            token, _jwks_cache, options={"verify_aud": False}
        )
    except JWTError as e:
        raise ValueError(str(e)) from e


def extract_user_id(
    authorization: str | None, verify_fn: VerifyFn = _default_verify
) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise AuthError("Authorization 헤더 없음")
    token = authorization[len("Bearer "):]
    try:
        claims = verify_fn(token)
    except Exception as e:
        raise AuthError(f"토큰 검증 실패: {e}") from e
    sub = claims.get("sub")
    if not sub:
        raise AuthError("sub 클레임 없음")
    return sub
