import httpx
import pytest
import respx

from app.core.models import AuthUnavailable
from app.infra.jwks import JwksVerifier
from tests.tokens import CLIENT_ID, ISSUER, rsa_key, token

URL = "http://keycloak:8080/realms/test/protocol/openid-connect/certs"
PUBLIC_ISSUER = "https://example.test/auth/realms/test"
FRONTEND_CLIENT_ID = "ggeolmuse-frontend"


def _verifier(**kwargs) -> JwksVerifier:
    return JwksVerifier(URL, issuers=(ISSUER, PUBLIC_ISSUER), client_ids=(CLIENT_ID, FRONTEND_CLIENT_ID),
                        **kwargs)


class _Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


def test_fetch_failure_is_unavailable():
    pem, _ = rsa_key("k1")
    with respx.mock:
        respx.get(URL).mock(side_effect=httpx.ConnectError("keycloak down"))
        with pytest.raises(AuthUnavailable):
            _verifier()(token(pem, "k1"))


def test_only_rs256_is_accepted():
    pem, public_jwk = rsa_key("k1")
    with respx.mock:
        respx.get(URL).mock(return_value=httpx.Response(200, json={"keys": [public_jwk]}))
        verifier = _verifier()
        assert verifier(token(pem, "k1"))["sub"] == "user-1"
        with pytest.raises(ValueError):
            verifier(token(pem, "k1", "RS512"))


def test_issuer_must_be_allowed():
    # Keycloak hostname 이 있으면(운영 · compose) iss 가 공개 주소, 없으면(helm dev) 내부 주소
    pem, public_jwk = rsa_key("k1")
    with respx.mock:
        respx.get(URL).mock(return_value=httpx.Response(200, json={"keys": [public_jwk]}))
        verifier = _verifier()
        for issuer in (ISSUER, PUBLIC_ISSUER):
            assert verifier(token(pem, "k1", iss=issuer))["sub"] == "user-1"
        for issuer in ("http://keycloak:8080/realms/other", None):
            with pytest.raises(ValueError):
                verifier(token(pem, "k1", iss=issuer))


def test_client_must_be_allowed():
    # 같은 realm 의 다른 클라이언트(admin-cli 등)로 받은 토큰 거절
    pem, public_jwk = rsa_key("k1")
    with respx.mock:
        respx.get(URL).mock(return_value=httpx.Response(200, json={"keys": [public_jwk]}))
        verifier = _verifier()
        for client_id in (CLIENT_ID, FRONTEND_CLIENT_ID):
            assert verifier(token(pem, "k1", azp=client_id))["sub"] == "user-1"
        for client_id in ("admin-cli", None):
            with pytest.raises(ValueError):
                verifier(token(pem, "k1", azp=client_id))


def test_unknown_kid_refetches_after_interval():
    _, old_jwk = rsa_key("old")
    pem, new_jwk = rsa_key("new")
    clock = _Clock()
    with respx.mock:
        route = respx.get(URL).mock(side_effect=[httpx.Response(200, json={"keys": [old_jwk]}),
                                                 httpx.Response(200, json={"keys": [new_jwk]})])
        verifier = _verifier(refresh_seconds=60.0, clock=clock)
        with pytest.raises(ValueError):
            verifier(token(pem, "new"))  # 처음 받은 묶음에 없음, 방금 받아서 다시 안 받음
        assert route.call_count == 1

        clock.now += 61
        assert verifier(token(pem, "new"))["sub"] == "user-1"
        assert route.call_count == 2
