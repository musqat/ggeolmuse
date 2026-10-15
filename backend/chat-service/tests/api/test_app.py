from fastapi.testclient import TestClient

from app.config import Settings
from app.core.chat_service import ChatService
from app.infra.jwks import JwksVerifier
from app.main import app, build_openai


def test_health_returns_ok():
    assert TestClient(app).get("/health").json() == {"status": "UP"}


def test_openai_client_does_not_retry():
    # 재시도하면 시간 초과도 다시 기다려 게이트웨이 30초를 넘긴다
    assert build_openai(Settings(openai_api_key="test")).max_retries == 0


def test_lifespan_builds_service_and_verifier():
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert isinstance(app.state.chat_service, ChatService)
        assert isinstance(app.state.verify_token, JwksVerifier)
