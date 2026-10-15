import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.core.chat_service import ChatService
from app.infra.jwks import JwksVerifier
from app.main import app, build_openai, build_redis


def test_health_returns_ok():
    assert TestClient(app).get("/health").json() == {"status": "UP"}


def test_openai_client_does_not_retry():
    # 재시도하면 시간 초과도 다시 기다려 게이트웨이 30초를 넘긴다
    assert build_openai(Settings(openai_api_key="test")).max_retries == 0


@pytest.mark.parametrize("password, sent", [("pw", "pw"), ("", None)])
def test_redis_client_sends_password_when_set(password, sent):
    # 운영 · 로컬 Redis 는 비밀번호를 요구한다(requirepass). 비밀번호 없는 Redis 에는 보내지 않는다
    redis = build_redis(Settings(redis_password=password))
    assert redis.connection_pool.connection_kwargs["password"] == sent


def test_lifespan_builds_service_and_verifier():
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert isinstance(app.state.chat_service, ChatService)
        assert isinstance(app.state.verify_token, JwksVerifier)
