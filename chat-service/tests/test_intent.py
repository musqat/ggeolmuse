import json
import pytest

from app.intent import extract_symbol, IntentResult


class _FakeLLM:
    """OpenAI mini 호출 대체. 지정한 JSON 문자열을 반환."""

    def __init__(self, payload: str):
        self._payload = payload

    def __call__(self, message: str) -> str:
        return self._payload


@pytest.mark.asyncio
async def test_extracts_valid_supported_symbol():
    llm = _FakeLLM(json.dumps({"symbol": "AAPL"}))
    result = await extract_symbol(
        "애플 요즘 어때?", supported={"AAPL", "MSFT"}, mini_call=llm
    )
    assert result == IntentResult(symbol="AAPL", valid=True, reason=None)


@pytest.mark.asyncio
async def test_unsupported_symbol_marked_invalid():
    llm = _FakeLLM(json.dumps({"symbol": "TSLA"}))
    result = await extract_symbol(
        "테슬라 어때?", supported={"AAPL", "MSFT"}, mini_call=llm
    )
    assert result.valid is False
    assert result.symbol == "TSLA"
    assert "지원" in result.reason


@pytest.mark.asyncio
async def test_no_symbol_returns_invalid():
    llm = _FakeLLM(json.dumps({"symbol": None}))
    result = await extract_symbol(
        "PER이 뭐야?", supported={"AAPL"}, mini_call=llm
    )
    assert result.valid is False
    assert result.symbol is None
    assert "특정 종목" in result.reason


@pytest.mark.asyncio
async def test_malformed_llm_output_returns_invalid():
    llm = _FakeLLM("쓰레기 출력")
    result = await extract_symbol(
        "AAPL", supported={"AAPL"}, mini_call=llm
    )
    assert result.valid is False
    assert result.symbol is None
