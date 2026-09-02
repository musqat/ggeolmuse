from __future__ import annotations

import time

import httpx


class MarketClient:
    def __init__(self, base_url: str, cache_ttl: int = 600):
        self._base = base_url.rstrip("/")
        self._cache_ttl = cache_ttl
        self._symbols_cache: set[str] | None = None
        self._symbols_cached_at: float = 0.0

    async def fetch_ohlc_range(
        self, symbol: str, start_date: str, end_date: str
    ) -> list[dict]:
        url = f"{self._base}/api/internal/market/ohlc/{symbol}/range"
        params = {"startDate": start_date, "endDate": end_date}
        async with httpx.AsyncClient(timeout=10.0) as client:
            res = await client.get(url, params=params)
            res.raise_for_status()
            return res.json()

    async def fetch_supported_symbols(self) -> set[str]:
        now = time.monotonic()
        if (
            self._symbols_cache is not None
            and now - self._symbols_cached_at < self._cache_ttl
        ):
            return self._symbols_cache

        url = f"{self._base}/api/market/symbols"
        async with httpx.AsyncClient(timeout=10.0) as client:
            res = await client.get(url)
            res.raise_for_status()
            data = res.json()

        symbols = {item["symbol"].upper() for item in data if item.get("symbol")}
        self._symbols_cache = symbols
        self._symbols_cached_at = now
        return symbols
