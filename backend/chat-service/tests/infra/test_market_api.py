from datetime import date

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
async def test_quote_maps_price_and_listing(market):
    respx.get(f"{BASE}/api/internal/market/price/AAPL").mock(return_value=httpx.Response(200, json={
        "symbol": "AAPL", "name": "Apple Inc.", "currentPrice": 238.15, "date": "2026-10-15",
        "available": True, "active": True}))
    quote = await market.quote("AAPL")
    assert (quote.symbol, quote.name, quote.price, quote.as_of, quote.active) == (
        "AAPL", "Apple Inc.", 238.15, date(2026, 10, 15), True)


@respx.mock
async def test_quote_without_listing_field_is_unknown(market):
    respx.get(f"{BASE}/api/internal/market/price/AAPL").mock(return_value=httpx.Response(200, json={
        "symbol": "AAPL", "name": None, "currentPrice": 1, "date": "2026-10-15", "available": True}))
    assert (await market.quote("AAPL")).active is None


@respx.mock
async def test_unavailable_or_404_quote_is_none(market):
    respx.get(f"{BASE}/api/internal/market/price/ZZZZ").mock(
        return_value=httpx.Response(200, json={"symbol": "ZZZZ", "available": False}))
    respx.get(f"{BASE}/api/internal/market/price/GONE").mock(return_value=httpx.Response(404))
    assert await market.quote("ZZZZ") is None
    assert await market.quote("GONE") is None


@respx.mock
async def test_bars_use_close_price_and_sort(market):
    route = respx.get(f"{BASE}/api/internal/market/ohlc/AAPL/range").mock(return_value=httpx.Response(200, json=[
        {"date": "2026-10-15", "openPrice": 10, "highPrice": 12, "lowPrice": 9, "closePrice": 11,
         "adjustedClose": 10.5, "volume": 100},
        {"date": "2026-10-14", "openPrice": None, "highPrice": None, "lowPrice": None, "closePrice": None,
         "adjustedClose": 9.5, "volume": None},
        {"date": "2026-10-13", "closePrice": None, "adjustedClose": None},
    ]))
    bars = await market.bars("AAPL", date(2025, 9, 11), date(2026, 10, 16))

    assert route.calls[0].request.url.params["startDate"] == "2025-09-11"
    assert route.calls[0].request.url.params["endDate"] == "2026-10-16"
    assert [(b.date.isoformat(), b.close, b.adj_close, b.high, b.volume) for b in bars] == [
        ("2026-10-14", 9.5, 9.5, 9.5, 0.0),
        ("2026-10-15", 11.0, 10.5, 12.0, 100.0),
    ]


@respx.mock
async def test_dividends_are_sorted_oldest_first(market):
    route = respx.get(f"{BASE}/api/internal/market/dividend/AAPL").mock(return_value=httpx.Response(200, json=[
        {"symbol": "AAPL", "exDate": "2026-08-10", "amount": 0.26, "currency": "USD"},
        {"symbol": "AAPL", "exDate": "2026-05-11", "amount": 0.26, "currency": "USD"},
    ]))
    dividends = await market.dividends("AAPL", date(2023, 10, 17), date(2026, 10, 16))

    assert route.calls[0].request.url.params["startDate"] == "2023-10-17"
    assert [d.ex_date.isoformat() for d in dividends] == ["2026-05-11", "2026-08-10"]
    assert dividends[0].currency == "USD"


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
    assert await market.bars("ZZZZ", date(2025, 9, 11), date(2026, 10, 16)) == []


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
        await market.bars("AAPL", date(2025, 9, 11), date(2026, 10, 16))
