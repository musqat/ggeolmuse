import logging

import pytest
from fastapi.testclient import TestClient

from app.api.auth import extract_user_id
from app.api.errors import Unauthorized
from app.core.models import AuthUnavailable
from app.main import app


def test_extract_user_id_from_verified_claims():
    assert extract_user_id("Bearer t", verify=lambda token: {"sub": "user-123"}) == "user-123"


@pytest.mark.parametrize("header", [None, "Basic abc", "Bearer"])
def test_missing_bearer_is_unauthorized(header):
    with pytest.raises(Unauthorized):
        extract_user_id(header, verify=lambda token: {"sub": "x"})


def test_invalid_token_or_missing_sub_is_unauthorized():
    def invalid(token):
        raise ValueError("bad signature")

    with pytest.raises(Unauthorized):
        extract_user_id("Bearer t", verify=invalid)
    with pytest.raises(Unauthorized):
        extract_user_id("Bearer t", verify=lambda token: {"email": "x"})


def test_auth_server_down_passes_through():
    def down(token):
        raise AuthUnavailable("keycloak down")

    with pytest.raises(AuthUnavailable):
        extract_user_id("Bearer t", verify=down)


@pytest.fixture
def client():
    yield TestClient(app)
    del app.state.verify_token


@pytest.mark.parametrize("method, path, body", [
    ("POST", "/api/chat/conversations", {"symbol": "AAPL"}),
    ("POST", "/api/chat", {"conversation_id": "c", "message": "hi"}),
    ("GET", "/api/chat/quota", None),
])
def test_requests_without_token_get_401_code(client, method, path, body):
    app.state.verify_token = lambda token: {"sub": "u1"}
    res = client.request(method, path, json=body)
    assert res.status_code == 401
    assert res.json() == {"code": "unauthorized", "message": "로그인이 필요합니다."}


def test_auth_server_down_gets_503_code(client, caplog):
    def down(token):
        raise AuthUnavailable("keycloak down")

    app.state.verify_token = down
    with caplog.at_level(logging.WARNING, logger="chat"):
        res = client.post("/api/chat", json={"conversation_id": "c", "message": "hi"},
                          headers={"Authorization": "Bearer t"})
    assert res.status_code == 503
    assert res.json()["code"] == "auth_unavailable"
    assert "detail='keycloak down'" in caplog.text
