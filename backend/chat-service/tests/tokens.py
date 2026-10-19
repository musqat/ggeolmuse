"""시험용 RSA 키와 Keycloak 꼴 토큰"""
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from jose import jwk, jwt

ISSUER = "http://keycloak:8080/realms/test"
CLIENT_ID = "ggeolmuse-client"


def rsa_key(kid: str):
    """서명용 개인키 PEM 과, JWKS 에 들어갈 공개키"""
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                serialization.NoEncryption())
    public_pem = private.public_key().public_bytes(serialization.Encoding.PEM,
                                                   serialization.PublicFormat.SubjectPublicKeyInfo)
    public_jwk = jwk.construct(public_pem, "RS256").to_dict()
    public_jwk["kid"] = kid
    return pem, public_jwk


def token(pem, kid: str, algorithm: str = "RS256", **claims) -> str:
    """sub · iss · azp 를 채운 토큰. 값을 None 으로 준 클레임은 뺌"""
    body = {"sub": "user-1", "iss": ISSUER, "azp": CLIENT_ID, **claims}
    return jwt.encode({k: v for k, v in body.items() if v is not None}, pem, algorithm=algorithm,
                      headers={"kid": kid})
