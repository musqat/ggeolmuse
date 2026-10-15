import httpx
import pytest
import respx
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from jose import jwk, jwt

from app.core.models import AuthUnavailable
from app.infra.jwks import JwksVerifier

URL = "http://keycloak:8080/realms/test/protocol/openid-connect/certs"


def _rsa_key(kid: str):
    """서명용 개인키 PEM 과, JWKS 에 들어갈 공개키"""
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


class _Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


def test_fetch_failure_is_unavailable():
    pem, _ = _rsa_key("k1")
    with respx.mock:
        respx.get(URL).mock(side_effect=httpx.ConnectError("keycloak down"))
        with pytest.raises(AuthUnavailable):
            JwksVerifier(URL)(_token(pem, "k1"))


def test_only_rs256_is_accepted():
    pem, public_jwk = _rsa_key("k1")
    with respx.mock:
        respx.get(URL).mock(return_value=httpx.Response(200, json={"keys": [public_jwk]}))
        verifier = JwksVerifier(URL)
        assert verifier(_token(pem, "k1"))["sub"] == "user-1"
        with pytest.raises(ValueError):
            verifier(_token(pem, "k1", "RS512"))


def test_unknown_kid_refetches_after_interval():
    _, old_jwk = _rsa_key("old")
    pem, new_jwk = _rsa_key("new")
    clock = _Clock()
    with respx.mock:
        route = respx.get(URL).mock(side_effect=[httpx.Response(200, json={"keys": [old_jwk]}),
                                                 httpx.Response(200, json={"keys": [new_jwk]})])
        verifier = JwksVerifier(URL, refresh_seconds=60.0, clock=clock)
        with pytest.raises(ValueError):
            verifier(_token(pem, "new"))  # 처음 받은 묶음에 없음, 방금 받아서 다시 안 받음
        assert route.call_count == 1

        clock.now += 61
        assert verifier(_token(pem, "new"))["sub"] == "user-1"
        assert route.call_count == 2
