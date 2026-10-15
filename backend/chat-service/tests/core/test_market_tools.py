from datetime import timedelta

from app.core.tools.market import MarketTools
from tests.core.fakes import TODAY, FakeMarket


async def test_bars_cover_calendar_days_back_from_today():
    market = FakeMarket()
    await MarketTools(market, today=lambda: TODAY).bars("AAPL", days=400)
    assert market.calls == [("bars", "AAPL", TODAY - timedelta(days=400), TODAY)]
