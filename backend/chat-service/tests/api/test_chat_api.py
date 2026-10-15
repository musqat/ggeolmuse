import asyncio

import pytest
from fastapi.testclient import TestClient

from app.api.auth import current_user_id
from app.api.deps import get_chat_model, get_market_client, get_rate_limiter
from app.core.models import AiUnavailable, MarketUnavailable
from app.infra.quota import RateLimiter
from app.main import app


class _FakeMarket:
    async def fetch_supported_symbols(self):
        return {"AAPL", "MSFT"}

    async def fetch_ohlc_range(self, symbol, start, end):
        return [
            {"date": f"2026-01-{i + 1:02d}", "openPrice": 100 + i,
             "highPrice": 101 + i, "lowPrice": 99 + i,
             "closePrice": 100 + i, "adjustedClose": 100 + i,
             "volume": 1_000_000}
            for i in range(60)
        ]


class _FakeModel:
    """AI 호출 대신 쓴다. 테스트마다 route_fn · analyze_fn 을 바꿔 끼운다(기본: AAPL 추출 + 분석 응답)"""

    def __init__(self):
        self.route_fn = lambda msg: '{"symbol": "AAPL"}'
        self.analyze_fn = lambda s, summary, q: "상승 경향으로 해석될 수 있습니다.\n\n※ 면책"

    def route(self, message):
        return self.route_fn(message)

    def analyze(self, symbol, summary, question):
        return self.analyze_fn(symbol, summary, question)


@pytest.fixture
def client(fake_rate_limiter):
    model = _FakeModel()
    app.dependency_overrides[current_user_id] = lambda: "user-1"
    app.dependency_overrides[get_rate_limiter] = lambda: fake_rate_limiter
    app.dependency_overrides[get_market_client] = lambda: _FakeMarket()
    app.dependency_overrides[get_chat_model] = lambda: model
    yield TestClient(app), fake_rate_limiter, model
    app.dependency_overrides.clear()


def test_valid_symbol_returns_analysis(client):
    c, _, _ = client
    res = c.post("/api/chat", json={"message": "AAPL 어때?"})
    assert res.status_code == 200
    body = res.json()
    assert body["symbol"] == "AAPL"
    assert "경향" in body["answer"]
    assert body["remaining"] == 4


def test_no_symbol_does_not_consume_quota(client):
    c, _, model = client
    model.route_fn = lambda msg: '{"symbol": null}'
    res = c.post("/api/chat", json={"message": "PER이 뭐야?"})
    assert res.status_code == 200
    assert res.json()["remaining"] == 5  # 차감 안 됨


def test_rejections_over_cap_use_quota(client, fake_redis):
    c, _, model = client
    app.dependency_overrides[get_rate_limiter] = lambda: RateLimiter(fake_redis, limit=5, reject_limit=1)
    model.route_fn = lambda msg: '{"symbol": null}'
    first = c.post("/api/chat", json={"message": "PER이 뭐야?"}).json()
    second = c.post("/api/chat", json={"message": "PER이 뭐야?"}).json()
    assert (first["remaining"], second["remaining"]) == (5, 4)


def test_symbol_param_skips_mini(client):
    c, _, model = client
    # mini가 호출되면 실패하게 만들어 스킵 검증
    def _boom(msg):
        raise AssertionError("mini를 호출하면 안 됨")
    model.route_fn = _boom
    res = c.post("/api/chat", json={"message": "분석해줘", "symbol": "MSFT"})
    assert res.status_code == 200
    body = res.json()
    assert body["symbol"] == "MSFT"
    assert body["remaining"] == 4


def test_symbol_param_unsupported_rejected(client):
    c, _, _ = client
    res = c.post("/api/chat", json={"message": "분석해줘", "symbol": "ZZZZ"})
    assert res.status_code == 200
    body = res.json()
    assert "지원하지 않는" in body["answer"]
    assert body["remaining"] == 5  # 차감 안 됨


@pytest.mark.asyncio
async def test_quota_exhausted_returns_429(client):
    c, limiter, _ = client
    for _ in range(5):
        await limiter.try_acquire("user-1")
    res = c.post("/api/chat", json={"message": "AAPL 어때?"})
    assert res.status_code == 429


class _DownMarket(_FakeMarket):
    async def fetch_ohlc_range(self, symbol, start, end):
        raise MarketUnavailable("market-data down")


@pytest.mark.asyncio
async def test_market_failure_returns_slot(client):
    c, limiter, _ = client
    app.dependency_overrides[get_market_client] = lambda: _DownMarket()
    res = c.post("/api/chat", json={"message": "AAPL 어때?"})
    assert res.status_code == 503
    assert await limiter.remaining("user-1") == 5


@pytest.mark.asyncio
async def test_analysis_failure_returns_slot(client):
    c, limiter, model = client

    def _fail(symbol, summary, question):
        raise AiUnavailable("openai down")
    model.analyze_fn = _fail
    res = c.post("/api/chat", json={"message": "AAPL 어때?"})
    assert res.status_code == 503
    assert await limiter.remaining("user-1") == 5


def _on_event_loop() -> bool:
    try:
        asyncio.get_running_loop()
        return True
    except RuntimeError:
        return False


def test_ai_calls_run_off_event_loop(client):
    # 동기 OpenAI 호출을 루프에서 부르면 답을 기다리는 동안 다른 요청 · /health 가 멈춘다
    c, _, model = client
    seen = []

    def _router(msg):
        seen.append(("router", _on_event_loop()))
        return '{"symbol": "AAPL"}'

    def _analysis(symbol, summary, question):
        seen.append(("analysis", _on_event_loop()))
        return "답"

    model.route_fn = _router
    model.analyze_fn = _analysis
    res = c.post("/api/chat", json={"message": "AAPL 어때?"})
    assert res.status_code == 200
    assert seen == [("router", False), ("analysis", False)]
