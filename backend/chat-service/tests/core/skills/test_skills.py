from datetime import timedelta

from app.core.skills import build_skills
from app.core.skills.indicators import IndicatorsSkill
from app.core.tools.market import MarketTools
from tests.core.fakes import TODAY, FakeMarket, make_bars


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


def test_build_skills_names():
    assert [s.name for s in build_skills(400)] == ["indicators"]
