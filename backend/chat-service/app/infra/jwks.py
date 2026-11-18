"""Keycloak 공개키(JWKS) 조회 · 캐시와 토큰 검증"""
from __future__ import annotations

import time
from typing import Callable, Iterable

import httpx
import jwt

from app.core.models import AuthUnavailable


class JwksVerifier:
    def __init__(self, jwks_url: str, *, issuers: Iterable[str], client_ids: Iterable[str],
                 refresh_seconds: float = 60.0, clock: Callable[[], float] = time.monotonic):
        self._url = jwks_url
        self._issuers = tuple(issuers)
        self._client_ids = tuple(client_ids)
        self._refresh_seconds = refresh_seconds
        self._clock = clock
        self._keys: dict | None = None
        self._fetched_at = 0.0

    def __call__(self, token: str) -> dict:
        """서명 · 만료 · 발급자(iss) · 받은 클라이언트(azp) · 이메일 인증을 확인한 클레임(틀린 토큰은 ValueError)"""
        try:
            kid = jwt.get_unverified_header(token).get("kid")
            claims = jwt.decode(token, self._signing_key(kid), algorithms=["RS256"], issuer=self._issuers,
                                options={"verify_aud": False})
        except jwt.PyJWTError as e:
            raise ValueError(str(e)) from e
        # aud 에는 Keycloak 기본값(account)만 실려 받은 클라이언트는 azp 로 봄
        if claims.get("azp") not in self._client_ids:
            raise ValueError(f"받지 않는 클라이언트의 토큰: {claims.get('azp')}")
        if claims.get("email_verified") is not True:
            raise ValueError("이메일 인증 전 사용자의 토큰")
        return claims

    # JWKS 에서 키 ID 가 같은 RS256 공개키
    def _signing_key(self, kid: str | None) -> jwt.PyJWK:
        for key in self._keys_for(kid).get("keys", []):
            if key.get("kid") == kid:
                return jwt.PyJWK(key, algorithm="RS256")
        raise ValueError(f"JWKS 에 없는 키 ID: {kid}")

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
