from fastapi.testclient import TestClient

from app.infra.jwks import JwksVerifier
from app.infra.llm import ChatModel
from app.infra.market_api import MarketClient
from app.infra.quota import RateLimiter
from app.main import app


def test_health_returns_ok():
    res = TestClient(app).get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "UP"}


def test_lifespan_builds_clients():
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert isinstance(app.state.verify_token, JwksVerifier)
        assert isinstance(app.state.market, MarketClient)
        assert isinstance(app.state.quota, RateLimiter)
        assert isinstance(app.state.model, ChatModel)
