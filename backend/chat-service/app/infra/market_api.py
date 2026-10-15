"""market-data 내부 API. 장애 → MarketUnavailable"""
from __future__ import annotations

import time

import httpx

from app.core.models import MarketUnavailable


class MarketClient:
    def __init__(self, http: httpx.AsyncClient, cache_ttl: int = 600):
        self._http = http
        self._cache_ttl = cache_ttl
        self._symbols_cache: set[str] | None = None
        self._symbols_cached_at: float = 0.0

    async def fetch_ohlc_range(
        self, symbol: str, start_date: str, end_date: str
    ) -> list[dict]:
        data = await self._get(
            f"/api/internal/market/ohlc/{symbol}/range",
            {"startDate": start_date, "endDate": end_date},
        )
        return data or []

    async def fetch_supported_symbols(self) -> set[str]:
        now = time.monotonic()
        if (
            self._symbols_cache is not None
            and now - self._symbols_cached_at < self._cache_ttl
        ):
            return self._symbols_cache

        data = await self._get("/api/market/symbols") or []
        symbols = {item["symbol"].upper() for item in data if item.get("symbol")}
        self._symbols_cache = symbols
        self._symbols_cached_at = now
        return symbols

    async def _get(self, path: str, params: dict | None = None):
        """404 면 None. 그 밖의 4xx · 5xx · 시간 초과 · 연결 실패는 MarketUnavailable"""
        try:
            res = await self._http.get(path, params=params)
        except httpx.HTTPError as e:
            raise MarketUnavailable(f"{path} {type(e).__name__}") from e
        if res.status_code == 404:
            return None
        if res.status_code >= 400:
            raise MarketUnavailable(f"{path} {res.status_code}")
        return res.json()
