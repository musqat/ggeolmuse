import time

import httpx
import pytest
import respx
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from jose import jwk, jwt

import app.auth as auth
from app.auth import extract_user_id, AuthError
from app.config import settings


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


def _rsa_key(kid: str):
    """서명용 개인키 PEM 과, JWKS 에 들어갈 공개키를 만든다"""
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                serialization.NoEncryption())
    public_pem = private.public_key().public_bytes(serialization.Encoding.PEM,
                                                   serialization.PublicFormat.SubjectPublicKeyInfo)
    public_jwk = jwk.construct(public_pem, "RS256").to_dict()
    public_jwk["kid"] = kid
    return pem, public_jwk


def _token(pem, kid: str, algorithm: str = "RS256") -> str:
    return jwt.encode({"sub": "user-1"}, pem, algorithm=algorithm, headers={"kid": kid})


def test_jwks_fetch_failure_is_unavailable(monkeypatch):
    pem, _ = _rsa_key("k1")
    monkeypatch.setattr(auth, "_jwks_cache", None)

    with respx.mock:
        respx.get(settings.keycloak_jwks_url).mock(side_effect=httpx.ConnectError("keycloak down"))
        with pytest.raises(auth.AuthUnavailable):
            extract_user_id(f"Bearer {_token(pem, 'k1')}")


def test_only_rs256_is_accepted(monkeypatch):
    pem, public_jwk = _rsa_key("k1")
    monkeypatch.setattr(auth, "_jwks_cache", {"keys": [public_jwk]})

    assert extract_user_id(f"Bearer {_token(pem, 'k1')}") == "user-1"
    with pytest.raises(AuthError):
        extract_user_id(f"Bearer {_token(pem, 'k1', 'RS512')}")


def test_unknown_kid_refetches_jwks(monkeypatch):
    _, old_jwk = _rsa_key("old")
    pem, new_jwk = _rsa_key("new")
    monkeypatch.setattr(auth, "_jwks_cache", {"keys": [old_jwk]})
    monkeypatch.setattr(auth, "_jwks_fetched_at", time.monotonic() - auth.JWKS_REFRESH_SECONDS - 1)

    with respx.mock:
        route = respx.get(settings.keycloak_jwks_url).mock(return_value=httpx.Response(200, json={"keys": [new_jwk]}))
        assert extract_user_id(f"Bearer {_token(pem, 'new')}") == "user-1"
        assert route.call_count == 1
        # 방금 받았으니 다음 모르는 kid 로는 다시 받지 않는다
        other_pem, _ = _rsa_key("other")
        with pytest.raises(AuthError):
            extract_user_id(f"Bearer {_token(other_pem, 'other')}")
        assert route.call_count == 1


def test_unknown_kid_waits_before_refetch(monkeypatch):
    _, old_jwk = _rsa_key("old")
    pem, _ = _rsa_key("new")
    monkeypatch.setattr(auth, "_jwks_cache", {"keys": [old_jwk]})
    monkeypatch.setattr(auth, "_jwks_fetched_at", time.monotonic())

    with respx.mock(assert_all_called=False):
        route = respx.get(settings.keycloak_jwks_url).mock(return_value=httpx.Response(200, json={"keys": []}))
        with pytest.raises(AuthError):
            extract_user_id(f"Bearer {_token(pem, 'new')}")
        assert route.call_count == 0
