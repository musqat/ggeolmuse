import asyncio
import socket

import httpx
import pytest
import respx
from fastapi.testclient import TestClient
from redis.exceptions import TimeoutError as RedisTimeoutError

from app.config import Settings
from app.core.chat_service import ChatService
from app.infra.jwks import JwksVerifier
from app.main import app, build_market_http, build_openai, build_redis, build_verifier
from tests.tokens import rsa_key, token


def test_health_returns_ok():
    assert TestClient(app).get("/health").json() == {"status": "UP"}


def test_scrape_serves_request_metrics():
    client = TestClient(app)
    client.get("/health")

    res = client.get("/metrics")

    assert res.status_code == 200
    assert "# TYPE http_server_requests_seconds histogram" in res.text


def test_openai_client_does_not_retry():
    # 재시도 없음(재시도하면 시간 초과도 다시 기다려 게이트웨이 30초를 넘김)
    assert build_openai(Settings(openai_api_key="test")).max_retries == 0


@pytest.mark.parametrize("password, sent", [("pw", "pw"), ("", None)])
def test_redis_client_sends_password_when_set(password, sent):
    # 운영 · 로컬 Redis 는 requirepass(비밀번호 없는 Redis 에는 안 보냄)
    redis = build_redis(Settings(redis_password=password))
    assert redis.connection_pool.connection_kwargs["password"] == sent


async def test_redis_client_gives_up_on_a_silent_server():
    # 연결만 받고 답하지 않는 서버
    with socket.socket() as silent:
        silent.bind(("127.0.0.1", 0))
        silent.listen()
        redis = build_redis(Settings(redis_host="127.0.0.1", redis_port=silent.getsockname()[1],
                                     redis_timeout_seconds=0.2))
        try:
            with pytest.raises(RedisTimeoutError):
                await asyncio.wait_for(redis.get("k"), timeout=3)
        finally:
            await redis.aclose()


def test_redis_client_limits_connect_time():
    redis = build_redis(Settings(redis_timeout_seconds=0.2))
    assert redis.connection_pool.connection_kwargs["socket_connect_timeout"] == 0.2


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


async def test_market_client_sends_service_token_from_settings():
    config = Settings(market_data_base_url="http://market-data-service:8083",
                      keycloak_token_url="http://keycloak:8080/auth/realms/r/protocol/openid-connect/token",
                      keycloak_service_client_id="svc", keycloak_service_client_secret="s3cret")
    with respx.mock:
        token = respx.post(config.keycloak_token_url).mock(
            return_value=httpx.Response(200, json={"access_token": "t1", "expires_in": 300}))
        price = respx.get("http://market-data-service:8083/api/internal/market/price/AAPL").mock(
            return_value=httpx.Response(404))
        http, token_http = build_market_http(config)
        try:
            await http.get("/api/internal/market/price/AAPL")
        finally:
            await http.aclose()
            await token_http.aclose()

    assert "client_id=svc" in token.calls.last.request.content.decode()
    assert price.calls.last.request.headers["Authorization"] == "Bearer t1"


def test_lifespan_builds_service_and_verifier():
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert isinstance(app.state.chat_service, ChatService)
        assert isinstance(app.state.verify_token, JwksVerifier)
