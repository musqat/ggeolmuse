import pytest
from fastapi.testclient import TestClient

from app.api.auth import AuthError, extract_user_id
from app.core.models import AuthUnavailable
from app.main import app


def test_extract_user_id_from_verified_claims():
    assert extract_user_id("Bearer t", verify=lambda token: {"sub": "user-123"}) == "user-123"


@pytest.mark.parametrize("header", [None, "Basic abc", "Bearer"])
def test_missing_bearer_raises(header):
    with pytest.raises(AuthError):
        extract_user_id(header, verify=lambda token: {"sub": "x"})


def test_invalid_token_or_missing_sub_raises():
    def invalid(token):
        raise ValueError("bad signature")

    with pytest.raises(AuthError):
        extract_user_id("Bearer t", verify=invalid)
    with pytest.raises(AuthError):
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


def test_requests_without_token_get_401(client):
    app.state.verify_token = lambda token: {"sub": "u1"}
    res = client.post("/api/chat", json={"message": "hi"})
    assert res.status_code == 401


def test_auth_server_down_gets_503(client):
    def down(token):
        raise AuthUnavailable("keycloak down")

    app.state.verify_token = down
    res = client.post("/api/chat", json={"message": "hi"}, headers={"Authorization": "Bearer t"})
    assert res.status_code == 503
