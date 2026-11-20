"""market-data 내부 API 요청에 싣는 Keycloak 서비스 토큰(client_credentials, 만료 30초 전까지 재사용, 받기 실패 뒤 5초는 다시 요청 안 함)"""
from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncGenerator, Callable

import httpx

REFRESH_MARGIN_SECONDS = 30.0
RETRY_AFTER_FAILURE_SECONDS = 5.0


class ServiceTokenAuth(httpx.Auth):
    def __init__(self, token_url: str, client_id: str, client_secret: str, http: httpx.AsyncClient,
                 clock: Callable[[], float] = time.monotonic):
        self._token_url = token_url
        self._form = {"grant_type": "client_credentials", "client_id": client_id, "client_secret": client_secret}
        self._http = http
        self._clock = clock
        self._lock = asyncio.Lock()
        self._token: str | None = None
        self._refresh_at = 0.0
        self._expires_at = 0.0
        self._retry_at = 0.0

    async def async_auth_flow(self, request: httpx.Request) -> AsyncGenerator[httpx.Request, httpx.Response]:
        request.headers["Authorization"] = f"Bearer {await self._current()}"
        yield request

    async def _current(self) -> str:
        """받아 둔 토큰(없거나 만료가 가까우면 새로 받음, 새로 받기에 실패하면 만료 전인 받아 둔 토큰, 만료 전 토큰도 없으면 httpx.HTTPError)"""
        async with self._lock:
            now = self._clock()
            if self._token is not None and now < self._refresh_at:
                return self._token
            if now < self._retry_at:
                return self._unexpired_or(now, httpx.HTTPError("서비스 토큰 받기 실패 뒤 대기"))
            try:
                res = await self._http.post(self._token_url, data=self._form)
                res.raise_for_status()
                body = res.json()
                token = body.get("access_token")
                if not token:
                    raise httpx.HTTPError("서비스 토큰 응답에 access_token 없음")
            except httpx.HTTPError as e:
                self._retry_at = self._clock() + RETRY_AFTER_FAILURE_SECONDS
                return self._unexpired_or(now, e)
            self._token = token
            self._expires_at = now + float(body.get("expires_in", 0))
            self._refresh_at = self._expires_at - REFRESH_MARGIN_SECONDS
            return self._token

    def _unexpired_or(self, now: float, error: httpx.HTTPError) -> str:
        if self._token is not None and now < self._expires_at:
            return self._token
        raise error
