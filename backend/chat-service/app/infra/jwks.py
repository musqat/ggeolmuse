"""Keycloak 공개키(JWKS) 조회 · 캐시와 토큰 검증"""
from __future__ import annotations

import time

import httpx
from jose import jwt
from jose.exceptions import JWTError

from app.config import settings
from app.core.models import AuthUnavailable

# 모르는 kid 가 와도 공개키를 이 간격(초)보다 자주 다시 받지 않는다
JWKS_REFRESH_SECONDS = 60.0

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


def verify_token(token: str) -> dict:
    """서명 · 만료를 확인한 클레임. 틀린 토큰은 ValueError"""
    try:
        kid = jwt.get_unverified_header(token).get("kid")
        return jwt.decode(
            token, _jwks_for(kid), algorithms=["RS256"], options={"verify_aud": False}
        )
    except JWTError as e:
        raise ValueError(str(e)) from e
