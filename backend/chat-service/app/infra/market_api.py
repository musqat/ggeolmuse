"""market-data 내부 API. JSON → Quote · Bar · Dividend, 장애 → MarketUnavailable"""
from __future__ import annotations

from datetime import date

import httpx

from app.core.models import Bar, Dividend, MarketUnavailable, Quote


class MarketClient:
    def __init__(self, http: httpx.AsyncClient):
        self._http = http

    async def quote(self, symbol: str) -> Quote | None:
        data = await self._get(f"/api/internal/market/price/{symbol}")
        if not data or not data.get("available"):
            return None
        return Quote(
            symbol=data["symbol"],
            name=data.get("name"),
            price=float(data["currentPrice"]),
            as_of=date.fromisoformat(data["date"]),
            active=data.get("active"),
        )

    async def bars(self, symbol: str, start: date, end: date) -> list[Bar]:
        data = await self._get(
            f"/api/internal/market/ohlc/{symbol}/range",
            {"startDate": start.isoformat(), "endDate": end.isoformat()},
        )
        bars = [_bar(row) for row in data or []]
        return sorted((b for b in bars if b is not None), key=lambda b: b.date)

    async def dividends(self, symbol: str, start: date, end: date) -> list[Dividend]:
        data = await self._get(
            f"/api/internal/market/dividend/{symbol}",
            {"startDate": start.isoformat(), "endDate": end.isoformat()},
        )
        rows = [
            Dividend(ex_date=date.fromisoformat(row["exDate"]), amount=float(row["amount"]),
                     currency=row.get("currency") or "")
            for row in data or []
            if row.get("exDate") and row.get("amount") is not None
        ]
        return sorted(rows, key=lambda d: d.ex_date)

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
