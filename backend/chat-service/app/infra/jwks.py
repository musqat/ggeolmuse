"""Keycloak 공개키(JWKS) 조회 · 캐시와 토큰 검증"""
from __future__ import annotations

import time
from typing import Callable

import httpx
from jose import jwt
from jose.exceptions import JWTError

from app.core.models import AuthUnavailable


class JwksVerifier:
    def __init__(self, jwks_url: str, refresh_seconds: float = 60.0,
                 clock: Callable[[], float] = time.monotonic):
        self._url = jwks_url
        self._refresh_seconds = refresh_seconds
        self._clock = clock
        self._keys: dict | None = None
        self._fetched_at = 0.0

    def __call__(self, token: str) -> dict:
        """서명 · 만료를 확인한 클레임. 틀린 토큰은 ValueError"""
        try:
            kid = jwt.get_unverified_header(token).get("kid")
            return jwt.decode(token, self._keys_for(kid), algorithms=["RS256"],
                              options={"verify_aud": False})
        except JWTError as e:
            raise ValueError(str(e)) from e

    # 처음이거나, 모르는 키 ID 인데 받은 지 refresh_seconds 가 지났으면 다시 받는다(키 교체)
    def _keys_for(self, kid: str | None) -> dict:
        known = self._keys is not None and any(k.get("kid") == kid for k in self._keys.get("keys", []))
        stale = self._clock() - self._fetched_at >= self._refresh_seconds
        if self._keys is None or (not known and stale):
            self._keys = self._fetch()
            self._fetched_at = self._clock()
        return self._keys

    def _fetch(self) -> dict:
        try:
            resp = httpx.get(self._url, timeout=10.0)
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            raise AuthUnavailable(str(e)) from e
