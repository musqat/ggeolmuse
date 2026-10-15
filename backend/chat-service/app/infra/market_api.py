"""market-data 내부 API. JSON → Bar, 장애 → MarketUnavailable"""
from __future__ import annotations

import time
from datetime import date

import httpx

from app.core.models import Bar, MarketUnavailable


class MarketClient:
    def __init__(self, http: httpx.AsyncClient, cache_ttl: int = 600):
        self._http = http
        self._cache_ttl = cache_ttl
        self._symbols_cache: set[str] | None = None
        self._symbols_cached_at: float = 0.0

    async def bars(self, symbol: str, start: date, end: date) -> list[Bar]:
        data = await self._get(
            f"/api/internal/market/ohlc/{symbol}/range",
            {"startDate": start.isoformat(), "endDate": end.isoformat()},
        )
        bars = [_bar(row) for row in data or []]
        return sorted((b for b in bars if b is not None), key=lambda b: b.date)

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


def _bar(row: dict) -> Bar | None:
    """종가는 차트처럼 closePrice, 없으면 adjustedClose"""
    close = row.get("closePrice")
    adjusted = row.get("adjustedClose")
    if close is None:
        close = adjusted
    if close is None or not row.get("date"):
        return None
    close = float(close)
    return Bar(
        date=date.fromisoformat(row["date"]),
        open=float(row.get("openPrice") or close),
        high=float(row.get("highPrice") or close),
        low=float(row.get("lowPrice") or close),
        close=close,
        adj_close=float(adjusted) if adjusted is not None else close,
        volume=float(row.get("volume") or 0),
    )
