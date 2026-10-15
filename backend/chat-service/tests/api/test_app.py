from fastapi.testclient import TestClient

from app.core.chat_service import ChatService
from app.infra.jwks import JwksVerifier
from app.main import app


def test_health_returns_ok():
    assert TestClient(app).get("/health").json() == {"status": "UP"}


def test_lifespan_builds_service_and_verifier():
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert isinstance(app.state.chat_service, ChatService)
        assert isinstance(app.state.verify_token, JwksVerifier)
