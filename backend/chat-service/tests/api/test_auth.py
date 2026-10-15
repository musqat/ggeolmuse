import pytest
from fastapi import HTTPException

from app.api import auth
from app.api.auth import AuthError, extract_user_id
from app.core.models import AuthUnavailable


def test_extract_user_id_from_verified_claims():
    # verify_fn을 주입해 JWKS 검증을 모킹
    def fake_verify(token: str) -> dict:
        return {"sub": "user-123", "email": "a@b.com"}

    uid = extract_user_id("Bearer sometoken", verify_fn=fake_verify)
    assert uid == "user-123"


def test_missing_bearer_raises():
    with pytest.raises(AuthError):
        extract_user_id(None, verify_fn=lambda t: {})


def test_invalid_token_raises():
    def fake_verify(token: str) -> dict:
        raise ValueError("invalid")

    with pytest.raises(AuthError):
        extract_user_id("Bearer bad", verify_fn=fake_verify)


def test_claims_without_sub_raises():
    with pytest.raises(AuthError):
        extract_user_id("Bearer t", verify_fn=lambda t: {"email": "x"})


def test_auth_errors_map_to_503_and_401(monkeypatch):
    def raising(exc):
        def extract(_authorization):
            raise exc
        return extract

    monkeypatch.setattr(auth, "extract_user_id", raising(AuthUnavailable("down")))
    with pytest.raises(HTTPException) as unavailable:
        auth.current_user_id("Bearer x")
    monkeypatch.setattr(auth, "extract_user_id", raising(AuthError("bad")))
    with pytest.raises(HTTPException) as invalid:
        auth.current_user_id("Bearer x")

    assert unavailable.value.status_code == 503
    assert invalid.value.status_code == 401
