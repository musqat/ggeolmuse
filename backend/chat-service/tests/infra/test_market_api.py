import httpx
import pytest
import respx

from app.core.models import MarketUnavailable
from app.infra.market_api import MarketClient

BASE = "http://market-data-service:8083"


@pytest.fixture
async def market():
    async with httpx.AsyncClient(base_url=BASE, timeout=5.0) as http:
        yield MarketClient(http)


@respx.mock
async def test_fetch_ohlc_range_returns_list(market):
    route = respx.get(f"{BASE}/api/internal/market/ohlc/AAPL/range").mock(return_value=httpx.Response(200, json=[
        {"date": "2026-01-02", "closePrice": 101.0, "adjustedClose": 101.0,
         "highPrice": 102.0, "lowPrice": 100.0, "volume": 1000000},
    ]))
    data = await market.fetch_ohlc_range("AAPL", "2025-03-01", "2026-01-02")
    assert route.calls[0].request.url.params["startDate"] == "2025-03-01"
    assert data[0]["closePrice"] == 101.0


@respx.mock
async def test_fetch_supported_symbols_caches(market):
    route = respx.get(f"{BASE}/api/market/symbols").mock(
        return_value=httpx.Response(200, json=[{"symbol": "AAPL"}, {"symbol": "MSFT"}])
    )
    assert await market.fetch_supported_symbols() == {"AAPL", "MSFT"}
    assert await market.fetch_supported_symbols() == {"AAPL", "MSFT"}
    assert route.call_count == 1


@respx.mock
async def test_404_is_empty(market):
    respx.get(f"{BASE}/api/internal/market/ohlc/ZZZZ/range").mock(return_value=httpx.Response(404))
    assert await market.fetch_ohlc_range("ZZZZ", "2025-03-01", "2026-01-02") == []


@respx.mock
@pytest.mark.parametrize("response", [httpx.Response(500), httpx.Response(400), httpx.ReadTimeout("slow"),
                                      httpx.ConnectError("down")])
async def test_failures_become_market_unavailable(market, response):
    route = respx.get(f"{BASE}/api/internal/market/ohlc/AAPL/range")
    if isinstance(response, httpx.Response):
        route.mock(return_value=response)
    else:
        route.mock(side_effect=response)
    with pytest.raises(MarketUnavailable):
        await market.fetch_ohlc_range("AAPL", "2025-03-01", "2026-01-02")
