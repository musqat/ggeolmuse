import asyncio
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
    def __init__(self, content="답", error=None):
        self.content = content
        self.error = error
        self.calls = []
        self.on_loop = []

    def create(self, **kwargs):
        try:
            asyncio.get_running_loop()
            self.on_loop.append(True)
        except RuntimeError:
            self.on_loop.append(False)
        self.calls.append(kwargs)
        if self.error:
            raise self.error
        message = SimpleNamespace(content=self.content)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)],
                               usage=SimpleNamespace(total_tokens=42))


def _model(completions):
    client = SimpleNamespace(chat=SimpleNamespace(completions=completions))
    return ChatModel(client, router_model="mini", main_model="main", router_timeout=5.0,
                     answer_timeout=15.0, max_answer_tokens=700)


def test_route_asks_router_model_for_json_with_timeout():
    completions = _FakeCompletions('{"symbol": "AAPL"}')
    assert _model(completions).route("애플 어때?") == '{"symbol": "AAPL"}'
    call = completions.calls[0]
    assert (call["model"], call["timeout"]) == ("mini", 5.0)
    assert call["response_format"] == {"type": "json_object"}


def test_route_without_content_returns_empty_object():
    assert _model(_FakeCompletions(None)).route("x") == "{}"


def test_analyze_strips_markdown_and_appends_disclaimer():
    completions = _FakeCompletions("# 제목\n*기울임* **굵게**")
    answer = _model(completions).analyze("AAPL", "RSI 60", "어때?")
    assert answer == f"제목\n기울임 **굵게**\n\n{DISCLAIMER}"
    call = completions.calls[0]
    assert (call["model"], call["timeout"], call["max_tokens"]) == ("main", 15.0, 700)


async def test_choose_uses_router_model_json_and_timeout():
    completions = _FakeCompletions('{"skill": "trend"}')
    completion = await _model(completions).choose([{"role": "user", "content": "q"}])

    assert (completion.text, completion.tokens) == ('{"skill": "trend"}', 42)
    call = completions.calls[0]
    assert (call["model"], call["timeout"], call["max_tokens"]) == ("mini", 5.0, 30)
    assert call["response_format"] == {"type": "json_object"}


async def test_answer_uses_main_model_and_timeout():
    completions = _FakeCompletions()
    await _model(completions).answer([{"role": "user", "content": "q"}])
    call = completions.calls[0]
    assert (call["model"], call["timeout"], call["max_tokens"]) == ("main", 15.0, 700)


async def test_empty_content_is_empty_text():
    completion = await _model(_FakeCompletions(None)).answer([])
    assert completion.text == ""


async def test_openai_error_becomes_ai_unavailable():
    request = httpx.Request("POST", "https://api.openai.com/v1/chat/completions")
    completions = _FakeCompletions(error=openai.APITimeoutError(request=request))
    with pytest.raises(AiUnavailable):
        await _model(completions).choose([])


async def test_calls_run_off_event_loop():
    # 동기 OpenAI 호출을 루프에서 부르면 답을 기다리는 동안 다른 요청 · /health 가 멈춘다
    completions = _FakeCompletions()
    model = _model(completions)
    await model.choose([])
    await model.answer([])
    assert completions.on_loop == [False, False]
