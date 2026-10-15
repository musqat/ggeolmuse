import logging

import pytest
from fastapi.testclient import TestClient

from app.api.auth import current_user_id
from app.api.chat import fingerprint
from app.api.deps import get_chat_service
from app.core.answerer import Answerer
from app.core.chat_service import ChatService
from app.core.messages import DISCLAIMER
from app.core.models import AiUnavailable, MarketUnavailable
from app.core.router import Router
from app.core.tools.market import MarketTools
from app.main import app
from tests.core.fakes import TODAY, FakeMarket, FakeModel, MemoryQuota, MemoryStore, StubSkill


class Env:
    def __init__(self):
        self.market = FakeMarket()
        self.model = FakeModel()
        self.quota = MemoryQuota()
        self.skill = StubSkill("indicators")
        self.service = ChatService(
            tools=MarketTools(self.market, today=lambda: TODAY),
            store=MemoryStore(),
            quota=self.quota,
            router=Router(self.model, [self.skill]),
            answerer=Answerer(self.model, history_turns=3),
        )
        self.client = TestClient(app)

    def start(self, symbol="AAPL"):
        return self.client.post("/api/chat/conversations", json={"symbol": symbol})

    def ask(self, conversation_id, message="RSI 어때?"):
        return self.client.post("/api/chat", json={"conversation_id": conversation_id, "message": message})


@pytest.fixture
def env():
    env = Env()
    app.dependency_overrides[current_user_id] = lambda: "user-1"
    app.dependency_overrides[get_chat_service] = lambda: env.service
    yield env
    app.dependency_overrides.clear()


def test_start_returns_conversation(env):
    res = env.start("aapl")
    assert res.status_code == 201
    body = res.json()
    assert (body["symbol"], body["name"], body["remaining"]) == ("AAPL", "Apple Inc.", 5)
    assert body["conversation_id"]


def test_start_unknown_symbol_is_404_with_code(env):
    res = env.start("ZZZZ")
    assert res.status_code == 404
    assert res.json() == {"code": "symbol_not_found",
                          "message": "종목을 찾을 수 없습니다. 종목 코드로 입력해 주세요 (예: AAPL)."}


def test_ask_answers(env):
    conversation_id = env.start().json()["conversation_id"]
    res = env.ask(conversation_id)
    assert res.status_code == 200
    assert res.json() == {"answer": f"상승 경향으로 해석될 수 있습니다.\n\n{DISCLAIMER}", "remaining": 4,
                          "conversation_id": conversation_id, "symbol": "AAPL", "rejected": None}


def test_ask_rejection_keeps_quota(env):
    env.model.choose_text = '{"skill": null, "reason": "off_topic"}'
    conversation_id = env.start().json()["conversation_id"]
    body = env.ask(conversation_id, "날씨 어때?").json()
    assert (body["rejected"], body["remaining"]) == ("off_topic", 5)
    assert body["answer"] == "AAPL의 지표, 주가 추이, 배당에 대해 물어봐 주세요."


def test_ask_unknown_conversation_is_404(env):
    res = env.ask("00000000-0000-0000-0000-000000000000")
    assert (res.status_code, res.json()["code"]) == (404, "conversation_not_found")


def test_quota_exceeded_is_429(env):
    conversation_id = env.start().json()["conversation_id"]
    env.quota.used["user-1"] = 5
    res = env.ask(conversation_id)
    assert (res.status_code, res.json()["code"]) == (429, "quota_exceeded")


def test_outages_are_503_with_source(env):
    conversation_id = env.start().json()["conversation_id"]
    env.model.answer_error = AiUnavailable("timeout")
    assert env.ask(conversation_id).json()["code"] == "ai_unavailable"

    env.model.answer_error = None
    env.market.error = MarketUnavailable("down")
    assert env.start().json()["code"] == "market_unavailable"


def test_unexpected_error_is_500(env, caplog):
    conversation_id = env.start().json()["conversation_id"]
    env.model.answer_error = RuntimeError("bug")
    with caplog.at_level(logging.ERROR, logger="chat"):
        res = env.ask(conversation_id)
    assert (res.status_code, res.json()["code"]) == (500, "internal_error")
    assert "Traceback" in caplog.text


def test_outage_log_keeps_cause(env, caplog):
    conversation_id = env.start().json()["conversation_id"]
    env.model.answer_error = AiUnavailable("APITimeoutError")
    with caplog.at_level(logging.WARNING, logger="chat"):
        env.ask(conversation_id)

    line = [r.getMessage() for r in caplog.records if r.getMessage().startswith("ask ")][0]
    assert "outcome=error:ai_unavailable" in line
    assert "detail=APITimeoutError" in line


def test_client_values_stay_on_one_log_line(env, caplog):
    with caplog.at_level(logging.INFO, logger="chat"):
        env.start("A\nB")
        env.ask("c\nask user=forged outcome=answered")

    lines = [r.getMessage() for r in caplog.records if r.name == "chat"]
    assert len(lines) == 2
    assert all("\n" not in line for line in lines)


def test_empty_message_is_422(env):
    assert env.ask("c", "").status_code == 422


def test_request_log_line(env, caplog):
    conversation_id = env.start().json()["conversation_id"]
    with caplog.at_level(logging.INFO, logger="chat"):
        env.ask(conversation_id)

    line = [r.getMessage() for r in caplog.records if r.getMessage().startswith("ask ")][0]
    assert f"user={fingerprint('user-1')}" in line
    assert "user-1" not in line
    assert "symbol=AAPL skill=indicators outcome=answered" in line
    assert "llm_calls=2 tokens=110" in line
