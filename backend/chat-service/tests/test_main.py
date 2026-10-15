import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app import main
from app.auth import AuthError, AuthUnavailable
from app.main import app

client = TestClient(app)


def test_health_returns_ok():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "UP"}


def test_auth_errors_map_to_503_and_401(monkeypatch):
    def raising(exc):
        def extract(_authorization):
            raise exc
        return extract

    monkeypatch.setattr(main, "extract_user_id", raising(AuthUnavailable("down")))
    with pytest.raises(HTTPException) as unavailable:
        main.current_user_id("Bearer x")
    monkeypatch.setattr(main, "extract_user_id", raising(AuthError("bad")))
    with pytest.raises(HTTPException) as invalid:
        main.current_user_id("Bearer x")

    assert unavailable.value.status_code == 503
    assert invalid.value.status_code == 401
