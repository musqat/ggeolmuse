from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Callable


@dataclass(frozen=True)
class IntentResult:
    symbol: str | None
    valid: bool
    reason: str | None


# mini_call: 메시지 → JSON 문자열 (동기 callable). 실제론 OpenAI mini 래퍼 주입.
MiniCall = Callable[[str], str]


async def extract_symbol(
    message: str, supported: set[str], mini_call: MiniCall
) -> IntentResult:
    raw = mini_call(message)
    try:
        parsed = json.loads(raw)
        symbol = parsed.get("symbol")
    except (json.JSONDecodeError, AttributeError):
        return IntentResult(
            symbol=None, valid=False,
            reason="특정 종목을 입력해주세요. (예: AAPL, 애플)",
        )

    if not symbol:
        return IntentResult(
            symbol=None, valid=False,
            reason="특정 종목을 입력해주세요. (예: AAPL, 애플)",
        )

    symbol = str(symbol).upper()
    if symbol not in supported:
        return IntentResult(
            symbol=symbol, valid=False,
            reason=f"{symbol}는 지원하지 않는 종목입니다.",
        )

    return IntentResult(symbol=symbol, valid=True, reason=None)
