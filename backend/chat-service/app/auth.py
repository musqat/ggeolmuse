from __future__ import annotations

import time
from typing import Callable

import httpx
from jose import jwt
from jose.exceptions import JWTError

from app.config import settings

VerifyFn = Callable[[str], dict]

# 모르는 kid 가 와도 공개키를 이 간격(초)보다 자주 다시 받지 않는다
JWKS_REFRESH_SECONDS = 60.0


class AuthError(Exception):
    pass


class AuthUnavailable(Exception):
    """인증 서버에서 공개키(JWKS)를 못 받음. 503 으로 보낸다"""


_jwks_cache: dict | None = None
_jwks_fetched_at: float = 0.0


def _fetch_jwks() -> dict:
    try:
        resp = httpx.get(settings.keycloak_jwks_url, timeout=10.0)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        raise AuthUnavailable(str(e)) from e


# 공개키 묶음. 처음이거나, 모르는 kid 인데 받은 지 오래면 다시 받는다(키 교체)
def _jwks_for(kid: str | None) -> dict:
    global _jwks_cache, _jwks_fetched_at
    known = _jwks_cache is not None and any(
        key.get("kid") == kid for key in _jwks_cache.get("keys", [])
    )
    stale = time.monotonic() - _jwks_fetched_at >= JWKS_REFRESH_SECONDS
    if _jwks_cache is None or (not known and stale):
        _jwks_cache = _fetch_jwks()
        _jwks_fetched_at = time.monotonic()
    return _jwks_cache


def _default_verify(token: str) -> dict:
    try:
        kid = jwt.get_unverified_header(token).get("kid")
        return jwt.decode(
            token, _jwks_for(kid), algorithms=["RS256"], options={"verify_aud": False}
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
    except AuthUnavailable:
        raise
    except Exception as e:
        raise AuthError(f"토큰 검증 실패: {e}") from e
    sub = claims.get("sub")
    if not sub:
        raise AuthError("sub 클레임 없음")
    return sub
