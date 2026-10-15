from types import SimpleNamespace

import httpx
import openai
import pytest

from app.core.models import AiUnavailable
from app.infra.llm import ChatModel, build_analysis_messages, DISCLAIMER, SYSTEM_GUARD


def test_analysis_messages_include_system_guard():
    msgs = build_analysis_messages(
        symbol="AAPL", summary="RSI 60(중립), MA20>MA60", question="어때?"
    )
    assert msgs[0]["role"] == "system"
    assert SYSTEM_GUARD in msgs[0]["content"]
    # 지표 요약과 종목이 유저 메시지에 포함
    user = msgs[1]["content"]
    assert "AAPL" in user
    assert "RSI 60(중립)" in user


def test_disclaimer_text_present():
    assert "투자 조언이 아니" in DISCLAIMER


class _FakeCompletions:
    def __init__(self, content=None, error=None):
        self.content = content
        self.error = error
        self.calls = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        if self.error:
            raise self.error
        message = SimpleNamespace(content=self.content)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])


def _model(content=None, error=None):
    completions = _FakeCompletions(content, error)
    client = SimpleNamespace(chat=SimpleNamespace(completions=completions))
    model = ChatModel(client, router_model="mini", main_model="main", router_timeout=5.0,
                      answer_timeout=15.0, max_answer_tokens=700)
    return model, completions


def test_route_asks_router_model_for_json_with_timeout():
    model, completions = _model('{"symbol": "AAPL"}')
    assert model.route("애플 어때?") == '{"symbol": "AAPL"}'
    call = completions.calls[0]
    assert (call["model"], call["timeout"]) == ("mini", 5.0)
    assert call["response_format"] == {"type": "json_object"}


def test_route_without_content_returns_empty_object():
    model, _ = _model(None)
    assert model.route("x") == "{}"


def test_analyze_strips_markdown_and_appends_disclaimer():
    model, completions = _model("# 제목\n*기울임* **굵게**")
    answer = model.analyze("AAPL", "RSI 60", "어때?")
    assert answer == f"제목\n기울임 **굵게**\n\n{DISCLAIMER}"
    call = completions.calls[0]
    assert (call["model"], call["timeout"], call["max_tokens"]) == ("main", 15.0, 700)


def test_openai_error_becomes_ai_unavailable():
    request = httpx.Request("POST", "https://api.openai.com/v1/chat/completions")
    model, _ = _model(error=openai.APITimeoutError(request=request))
    with pytest.raises(AiUnavailable):
        model.analyze("AAPL", "RSI 60", "어때?")
