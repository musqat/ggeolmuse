"""스킬이 쓰는 시세 도구(조회 기간은 여기서)"""
from __future__ import annotations

from datetime import date, timedelta
from typing import Callable

from app.core.models import Bar, Dividend, Quote
from app.core.ports import MarketData


class MarketTools:
    def __init__(self, market: MarketData, today: Callable[[], date] = date.today):
        self._market = market
        self._today = today

    def today(self) -> date:
        return self._today()

    async def quote(self, symbol: str) -> Quote | None:
        """현재가 · 이름 · 상장 여부(없는 종목이면 None)"""
        return await self._market.quote(symbol)

    async def bars(self, symbol: str, days: int) -> list[Bar]:
        """최근 days 일(달력)의 일봉, 오래된 것부터"""
        end = self._today()
        return await self._market.bars(symbol, end - timedelta(days=days), end)

    async def dividends(self, symbol: str, years: int) -> list[Dividend]:
        """최근 years 년의 배당, 오래된 것부터"""
        end = self._today()
        return await self._market.dividends(symbol, end - timedelta(days=365 * years), end)
