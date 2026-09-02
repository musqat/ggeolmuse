import pytest
from fastapi.testclient import TestClient

from app import main
from app import deps
from app.main import app, current_user_id
from app.deps import get_market_client, get_rate_limiter


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


@pytest.fixture
def client(fake_rate_limiter, monkeypatch):
    app.dependency_overrides[current_user_id] = lambda: "user-1"
    app.dependency_overrides[get_rate_limiter] = lambda: fake_rate_limiter
    app.dependency_overrides[get_market_client] = lambda: _FakeMarket()
    # LLM 모킹 (기본: AAPL 추출 + 분석 응답)
    monkeypatch.setattr(main, "call_router", lambda msg: '{"symbol": "AAPL"}')
    monkeypatch.setattr(
        main, "call_analysis",
        lambda s, summary, q: "상승 경향으로 해석될 수 있습니다.\n\n※ 면책",
    )
    yield TestClient(app), fake_rate_limiter, monkeypatch
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
    c, _, monkeypatch = client
    monkeypatch.setattr(main, "call_router", lambda msg: '{"symbol": null}')
    res = c.post("/api/chat", json={"message": "PER이 뭐야?"})
    assert res.status_code == 200
    assert res.json()["remaining"] == 5  # 차감 안 됨


def test_symbol_param_skips_mini(client):
    c, _, monkeypatch = client
    # mini가 호출되면 실패하게 만들어 스킵 검증
    def _boom(msg):
        raise AssertionError("mini를 호출하면 안 됨")
    monkeypatch.setattr(main, "call_router", _boom)
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
        await limiter.increment("user-1")
    res = c.post("/api/chat", json={"message": "AAPL 어때?"})
    assert res.status_code == 429
