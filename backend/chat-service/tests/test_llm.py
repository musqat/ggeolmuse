from types import SimpleNamespace

from app.config import settings
from app.llm import ChatModel, build_analysis_messages, DISCLAIMER, SYSTEM_GUARD


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
    def __init__(self, content):
        self.content = content
        self.calls = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        message = SimpleNamespace(content=self.content)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])


def _model(content):
    completions = _FakeCompletions(content)
    client = SimpleNamespace(chat=SimpleNamespace(completions=completions))
    return ChatModel(client), completions


def test_route_asks_router_model_for_json():
    model, completions = _model('{"symbol": "AAPL"}')
    assert model.route("애플 어때?") == '{"symbol": "AAPL"}'
    assert completions.calls[0]["model"] == settings.openai_model_router
    assert completions.calls[0]["response_format"] == {"type": "json_object"}


def test_route_without_content_returns_empty_object():
    model, _ = _model(None)
    assert model.route("x") == "{}"


def test_analyze_strips_markdown_and_appends_disclaimer():
    model, completions = _model("# 제목\n*기울임* **굵게**")
    answer = model.analyze("AAPL", "RSI 60", "어때?")
    assert answer == f"제목\n기울임 **굵게**\n\n{DISCLAIMER}"
    assert completions.calls[0]["model"] == settings.openai_model_main
