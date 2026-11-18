"""테스트용 RSA 키와 Keycloak 형식 토큰"""
import base64
import hashlib
import hmac
import json

import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from jwt.algorithms import RSAAlgorithm

ISSUER = "http://keycloak:8080/realms/test"
CLIENT_ID = "ggeolmuse-client"


def _claims(**claims) -> dict:
    body = {"sub": "user-1", "iss": ISSUER, "azp": CLIENT_ID, "email_verified": True, **claims}
    return {k: v for k, v in body.items() if v is not None}


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def rsa_key(kid: str):
    """서명용 개인키 PEM 과, JWKS 에 들어갈 공개키"""
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                serialization.NoEncryption())
    public_jwk = RSAAlgorithm.to_jwk(private.public_key(), as_dict=True)
    public_jwk.update(kid=kid, alg="RS256")
    return pem, public_jwk


def public_key_bytes(pem, encoding=serialization.Encoding.PEM) -> bytes:
    """개인키 PEM 의 공개키(PEM 또는 DER)"""
    public = serialization.load_pem_private_key(pem, password=None).public_key()
    return public.public_bytes(encoding, serialization.PublicFormat.SubjectPublicKeyInfo)


def token(pem, kid: str, algorithm: str = "RS256", **claims) -> str:
    """sub · iss · azp · email_verified 를 채운 토큰(값을 None 으로 준 클레임은 뺌)"""
    return jwt.encode(_claims(**claims), pem, algorithm=algorithm, headers={"kid": kid})


def forged_token(kid: str, algorithm: str, secret: bytes = b"") -> str:
    """JWT 라이브러리 없이 만든 토큰(HS256 은 secret 으로 HMAC 서명, none 은 빈 서명)"""
    header = _b64(json.dumps({"alg": algorithm, "typ": "JWT", "kid": kid}).encode())
    signing_input = f"{header}.{_b64(json.dumps(_claims()).encode())}"
    signature = hmac.new(secret, signing_input.encode(), hashlib.sha256).digest() if algorithm == "HS256" else b""
    return f"{signing_input}.{_b64(signature)}"
