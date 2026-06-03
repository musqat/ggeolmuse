import httpx
import respx
import pytest

from app.market_client import MarketClient


@pytest.mark.asyncio
@respx.mock
async def test_fetch_ohlc_range_returns_list():
    base = "http://market-data-service:8083"
    route = respx.get(
        f"{base}/api/internal/market/ohlc/AAPL/range"
    ).mock(return_value=httpx.Response(200, json=[
        {"date": "2026-01-02", "closePrice": 101.0, "adjustedClose": 101.0,
         "highPrice": 102.0, "lowPrice": 100.0, "volume": 1000000},
    ]))
    client = MarketClient(base_url=base)
    data = await client.fetch_ohlc_range("AAPL", "2025-03-01", "2026-01-02")
    assert route.called
    assert len(data) == 1
    assert data[0]["closePrice"] == 101.0


@pytest.mark.asyncio
@respx.mock
async def test_fetch_supported_symbols_caches():
    base = "http://market-data-service:8083"
    route = respx.get(f"{base}/api/market/symbols").mock(
        return_value=httpx.Response(200, json=[
            {"symbol": "AAPL"}, {"symbol": "MSFT"},
        ])
    )
    client = MarketClient(base_url=base)
    first = await client.fetch_supported_symbols()
    second = await client.fetch_supported_symbols()
    assert first == {"AAPL", "MSFT"}
    assert second == {"AAPL", "MSFT"}
    # 캐시되어 HTTP 호출은 1번만
    assert route.call_count == 1
