import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from app.config import Settings
from app.core.chat_service import ChatService
from app.infra.jwks import JwksVerifier
from app.main import app, build_openai, build_redis, build_verifier
from tests.tokens import rsa_key, token


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


def test_verifier_takes_issuers_and_clients_from_settings():
    url = "http://keycloak:8080/auth/realms/r/protocol/openid-connect/certs"
    config = Settings(keycloak_jwks_url=url,
                      keycloak_issuers="https://example.test/auth/realms/r, http://keycloak:8080/auth/realms/r",
                      keycloak_client_ids="web-client,app-client")
    pem, public_jwk = rsa_key("k1")
    with respx.mock:
        respx.get(url).mock(return_value=httpx.Response(200, json={"keys": [public_jwk]}))
        verify = build_verifier(config)
        for issuer in ("https://example.test/auth/realms/r", "http://keycloak:8080/auth/realms/r"):
            assert verify(token(pem, "k1", iss=issuer, azp="app-client"))["sub"] == "user-1"
        with pytest.raises(ValueError):
            verify(token(pem, "k1", iss="http://keycloak:8080/auth/realms/r", azp="admin-cli"))


def test_lifespan_builds_service_and_verifier():
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert isinstance(app.state.chat_service, ChatService)
        assert isinstance(app.state.verify_token, JwksVerifier)
