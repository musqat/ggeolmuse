from dataclasses import replace
from datetime import date, timedelta

from app.core.models import Dividend, Quote
from app.core.skills import build_skills
from app.core.skills.dividend import DividendSkill
from app.core.skills.indicators import IndicatorsSkill
from app.core.skills.trend import TrendSkill
from app.core.tools.market import MarketTools
from tests.core.fakes import TODAY, FakeMarket, line_bars, make_bars


def _tools(market):
    return MarketTools(market, today=lambda: TODAY)


async def test_indicators_skill_reads_lookback_days():
    market = FakeMarket()
    evidence = await IndicatorsSkill(lookback_days=400).gather("AAPL", _tools(market))

    assert market.calls == [("bars", "AAPL", TODAY - timedelta(days=400), TODAY)]
    assert evidence.as_of == make_bars()[-1].date
    assert evidence.lines[0].startswith("기준일 ")


async def test_indicators_skill_without_bars_is_none():
    market = FakeMarket()
    market.bar_rows["AAPL"] = []
    assert await IndicatorsSkill(lookback_days=400).gather("AAPL", _tools(market)) is None


async def test_trend_skill_lists_returns():
    market = FakeMarket()
    market.bar_rows["AAPL"] = line_bars([100.0] * 253 + [110.0])
    evidence = await TrendSkill(lookback_days=400).gather("AAPL", _tools(market))

    text = "\n".join(evidence.lines)
    assert "1년 +10.0% / +10.0%" in text
    assert "52주 최고 111.00" in text
    assert "변동성" in text


async def test_trend_skill_marks_values_it_cannot_compute():
    market = FakeMarket()
    bars = line_bars([100.0] * 253 + [110.0])
    bars = [replace(b, adj_close=None) for b in bars[:-1]] + bars[-1:]
    bars[-100] = replace(bars[-100], low=0.0)  # 워런트처럼 저가 0
    market.bar_rows["AAPL"] = bars
    evidence = await TrendSkill(lookback_days=400).gather("AAPL", _tools(market))

    text = "\n".join(evidence.lines)
    assert "1년 +10.0% / —" in text
    assert "최저 0.00(지금 —)" in text


async def test_trend_skill_names_missing_values():
    market = FakeMarket()
    market.bar_rows["AAPL"] = line_bars([100.0] * 30)
    evidence = await TrendSkill(lookback_days=400).gather("AAPL", _tools(market))
    assert evidence.lines[-1] == (
        "계산 못 한 값: 3달 수익률, 6달 수익률, 1년 수익률, 52주 범위, 1년 최대 낙폭, 변동성 (데이터 30거래일)")


async def test_dividend_skill_without_quote_is_none():
    market = FakeMarket()
    market.quotes.clear()
    assert await DividendSkill(lookback_years=3).gather("AAPL", _tools(market)) is None


async def test_dividend_skill_without_dividends():
    evidence = await DividendSkill(lookback_years=3).gather("AAPL", _tools(FakeMarket()))
    assert evidence.lines[-1] == "최근 3년 배당 기록 없음"


async def test_dividend_skill_lists_year_total_and_yield():
    market = FakeMarket()
    market.quotes["AAPL"] = Quote(symbol="AAPL", name="Apple Inc.", price=104.0, as_of=TODAY, active=True)
    market.dividend_rows["AAPL"] = [
        Dividend(ex_date=date(2025, 11, 10), amount=0.26, currency="USD"),
        Dividend(ex_date=date(2026, 2, 9), amount=0.26, currency="USD"),
        Dividend(ex_date=date(2026, 5, 11), amount=0.26, currency="USD"),
        Dividend(ex_date=date(2026, 8, 10), amount=0.26, currency="USD"),
    ]
    evidence = await DividendSkill(lookback_years=3).gather("AAPL", _tools(market))

    assert "지급 주기: 분기" in evidence.lines
    assert "최근 1년 치 합계 1.04, 배당 수익률 1.00%" in evidence.lines
    assert ("dividends", "AAPL", TODAY - timedelta(days=365 * 3), TODAY) in market.calls


async def test_dividend_skill_notes_changed_base():
    market = FakeMarket()
    market.dividend_rows["AAPL"] = [
        Dividend(ex_date=date(2025, 11, 10), amount=0.88, currency="USD"),
        Dividend(ex_date=date(2026, 2, 9), amount=0.88, currency="USD"),
        Dividend(ex_date=date(2026, 5, 11), amount=0.22, currency="USD"),
        Dividend(ex_date=date(2026, 8, 10), amount=0.22, currency="USD"),
    ]
    evidence = await DividendSkill(lookback_years=3).gather("AAPL", _tools(market))
    assert "최근 1년 안에 금액 기준이 바뀐 것으로 보임(분할 등). 1년 치 합계 · 수익률은 계산하지 않음" in evidence.lines
    assert evidence.lines[-1] == "금액 기준이 바뀐 것으로 보여 증감은 계산하지 않음"


def test_build_skills_names():
    assert [s.name for s in build_skills(400, 3)] == ["indicators", "trend", "dividend"]
