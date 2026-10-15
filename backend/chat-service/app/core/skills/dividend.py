"""배당 스킬. 배당 내역과 현재가로 1년 치 합계 · 수익률 · 주기 · 증감을 자료로 만든다"""
from __future__ import annotations

import asyncio

from app.core.analysis.dividend import DividendSnapshot, summarize_dividends
from app.core.models import Evidence, Quote
from app.core.tools.market import MarketTools


def dividend_lines(s: DividendSnapshot, quote: Quote, years: int) -> list[str]:
    """배당 스냅숏 → 자료 줄"""
    lines = [f"기준일 {quote.as_of.isoformat()} 현재가 {quote.price:.2f}"]
    if not s.recent:
        lines.append(f"최근 {years}년 배당 기록 없음")
        return lines
    currency = s.recent[0].currency
    recent = ", ".join(f"{d.ex_date.isoformat()} {d.amount:g}" for d in s.recent)
    lines.append(f"최근 배당락일과 주당 금액({currency}): {recent}")
    lines.append(f"지급 주기: {s.frequency or '알 수 없음'}")
    if s.stopped:
        lines.append("마지막 배당락일이 1년보다 오래됨. 1년 치 합계는 계산하지 않음")
    elif s.base_changed_in_year:
        lines.append("최근 1년 안에 금액 기준이 바뀐 것으로 보임(분할 등). 1년 치 합계 · 수익률은 계산하지 않음")
    elif s.year_total is not None:
        dividend_yield = f", 배당 수익률 {s.yield_percent:.2f}%" if s.yield_percent is not None else ""
        lines.append(f"최근 1년 치 합계 {s.year_total:g}{dividend_yield}")
    if s.change_percent is not None:
        lines.append(f"증감: 그 앞 1년 치 {s.previous_total:g} 대비 {s.change_percent:+.1f}%")
    elif s.base_changed:
        lines.append("금액 기준이 바뀐 것으로 보여 증감은 계산하지 않음")
    return lines


class DividendSkill:
    name = "dividend"
    description = "배당: 최근 배당락일과 금액, 최근 1년 합계, 배당 수익률, 지급 주기, 증감"
    guide = (
        "자료는 최근 배당 기록과 현재가로 계산한 값이다. 지급일은 자료에 없어 배당락일 기준으로 말한다. "
        "자료에 없는 값은 계산하지 않았다고 말한다."
    )

    def __init__(self, lookback_years: int):
        self._lookback_years = lookback_years

    async def gather(self, symbol: str, tools: MarketTools) -> Evidence | None:
        quote, dividends = await asyncio.gather(
            tools.quote(symbol), tools.dividends(symbol, self._lookback_years)
        )
        if quote is None:
            return None
        snapshot = summarize_dividends(dividends, quote.price, tools.today())
        return Evidence(lines=tuple(dividend_lines(snapshot, quote, self._lookback_years)), as_of=quote.as_of)
