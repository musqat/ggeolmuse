import httpx
import pytest
import respx

from app.core.models import MarketUnavailable
from app.infra.market_api import MarketClient
from app.infra.service_token import ServiceTokenAuth

BASE = "http://market-data-service:8083"
TOKEN_URL = "http://keycloak:8080/realms/r/protocol/openid-connect/token"


class Clock:
    """테스트가 옮기는 초 단위 시계"""

    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


@pytest.fixture
def clock():
    return Clock()


@pytest.fixture
async def market(clock):
    async with httpx.AsyncClient() as token_http:
        auth = ServiceTokenAuth(TOKEN_URL, "svc", "s3cret", token_http, clock=clock)
        async with httpx.AsyncClient(base_url=BASE, timeout=5.0, auth=auth) as http:
            yield MarketClient(http)


def _token(value: str, expires_in: int = 300) -> httpx.Response:
    return httpx.Response(200, json={"access_token": value, "expires_in": expires_in, "token_type": "Bearer"})


def _price() -> httpx.Response:
    return httpx.Response(200, json={"symbol": "AAPL", "currentPrice": 1, "date": "2026-10-15", "available": True})


@respx.mock
async def test_internal_call_carries_client_credentials_token(market):
    token = respx.post(TOKEN_URL).mock(return_value=_token("t1"))
    price = respx.get(f"{BASE}/api/internal/market/price/AAPL").mock(return_value=_price())

    await market.quote("AAPL")

    form = dict(httpx.QueryParams(token.calls.last.request.content.decode()))
    assert form == {"grant_type": "client_credentials", "client_id": "svc", "client_secret": "s3cret"}
    assert price.calls.last.request.headers["Authorization"] == "Bearer t1"


@respx.mock
async def test_token_is_reused_until_30_seconds_before_expiry(market, clock):
    token = respx.post(TOKEN_URL).mock(side_effect=[_token("t1"), _token("t2")])
    price = respx.get(f"{BASE}/api/internal/market/price/AAPL").mock(return_value=_price())

    await market.quote("AAPL")
    clock.now += 269
    await market.quote("AAPL")
    assert token.call_count == 1

    clock.now += 1
    await market.quote("AAPL")
    assert token.call_count == 2
    assert price.calls.last.request.headers["Authorization"] == "Bearer t2"


@respx.mock
async def test_refresh_failure_keeps_unexpired_token(market, clock):
    token = respx.post(TOKEN_URL).mock(side_effect=[_token("t1"), httpx.Response(401), httpx.Response(401)])
    price = respx.get(f"{BASE}/api/internal/market/price/AAPL").mock(return_value=_price())

    await market.quote("AAPL")
    clock.now += 280
    await market.quote("AAPL")
    assert price.calls.last.request.headers["Authorization"] == "Bearer t1"

    clock.now += 2
    await market.quote("AAPL")
    assert token.call_count == 2

    clock.now += 18
    with pytest.raises(MarketUnavailable):
        await market.quote("AAPL")
    assert token.call_count == 3


@respx.mock
async def test_failure_waits_5_seconds_before_requesting_again(market, clock):
    token = respx.post(TOKEN_URL).mock(side_effect=[httpx.Response(401), _token("t1")])
    respx.get(f"{BASE}/api/internal/market/price/AAPL").mock(return_value=_price())

    with pytest.raises(MarketUnavailable):
        await market.quote("AAPL")
    clock.now += 4
    with pytest.raises(MarketUnavailable):
        await market.quote("AAPL")
    assert token.call_count == 1

    clock.now += 1
    await market.quote("AAPL")
    assert token.call_count == 2


@respx.mock
@pytest.mark.parametrize("response", [httpx.Response(401), httpx.Response(200, json={}), httpx.ConnectError("down")])
async def test_token_failure_becomes_market_unavailable(market, response):
    route = respx.post(TOKEN_URL)
    if isinstance(response, httpx.Response):
        route.mock(return_value=response)
    else:
        route.mock(side_effect=response)
    price = respx.get(f"{BASE}/api/internal/market/price/AAPL").mock(return_value=_price())

    with pytest.raises(MarketUnavailable):
        await market.quote("AAPL")
    assert not price.called
