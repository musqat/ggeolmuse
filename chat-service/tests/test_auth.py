import pytest

from app.auth import extract_user_id, AuthError


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
