"""core 테스트용 가짜 포트와 고정 캔들"""
from __future__ import annotations

import uuid
from dataclasses import replace
from datetime import date, timedelta

from app.core.models import Bar, Completion, Conversation, Evidence, Quote, Turn

TODAY = date(2026, 10, 16)


def make_bars(n: int = 300, start: date = date(2025, 1, 1)) -> list[Bar]:
    """정수(센트)로 만든 고정 캔들. 차트 기준값을 뽑은 node 스크립트와 같은 식"""
    seed = 20261016
    price = 10000

    def next_seed() -> int:
        nonlocal seed
        seed = (seed * 48271) % 2147483647
        return seed

    bars = []
    for i in range(n):
        step = next_seed() % 401 - 200
        open_ = price
        close = max(1000, price + step)
        high = max(open_, close) + next_seed() % 151
        low = min(open_, close) - next_seed() % 151
        volume = 1_000_000 + next_seed() % 500_000
        bars.append(Bar(date=start + timedelta(days=i), open=open_ / 100, high=high / 100, low=low / 100,
                        close=close / 100, adj_close=close / 100, volume=float(volume)))
        price = close
    return bars


def line_bars(closes: list[float], start: date = date(2025, 1, 1)) -> list[Bar]:
    """종가만 정한 캔들. 고가 · 저가는 종가 ± 1, 배당 조정 종가는 종가와 같음"""
    return [Bar(date=start + timedelta(days=i), open=c, high=c + 1, low=c - 1, close=c, adj_close=c,
                volume=1_000_000.0) for i, c in enumerate(closes)]


class FakeMarket:
    def __init__(self):
        self.quotes: dict[str, Quote] = {
            "AAPL": Quote(symbol="AAPL", name="Apple Inc.", price=109.43, as_of=TODAY, active=True),
        }
        self.bar_rows: dict[str, list[Bar]] = {"AAPL": make_bars()}
        self.error: Exception | None = None
        self.calls: list[tuple] = []

    async def quote(self, symbol):
        self.calls.append(("quote", symbol))
        if self.error:
            raise self.error
        return self.quotes.get(symbol)

    async def bars(self, symbol, start, end):
        self.calls.append(("bars", symbol, start, end))
        if self.error:
            raise self.error
        return self.bar_rows.get(symbol, [])


class FakeModel:
    """choose · answer 가 돌려줄 글을 정해 두고, 받은 메시지를 남긴다"""

    def __init__(self, choose='{"skill": "indicators"}', answer="상승 경향으로 해석될 수 있습니다."):
        self.choose_text = choose
        self.answer_text = answer
        self.choose_error: Exception | None = None
        self.answer_error: Exception | None = None
        self.chosen: list[list[dict]] = []
        self.answered: list[list[dict]] = []

    async def choose(self, messages):
        self.chosen.append(messages)
        if self.choose_error:
            raise self.choose_error
        return Completion(text=self.choose_text, tokens=10)

    async def answer(self, messages):
        self.answered.append(messages)
        if self.answer_error:
            raise self.answer_error
        return Completion(text=self.answer_text, tokens=100)


class MemoryStore:
    def __init__(self, max_turns: int = 3):
        self.max_turns = max_turns
        self.rows: dict[tuple[str, str], Conversation] = {}
        self.append_error: Exception | None = None

    async def create(self, user_id, symbol, name):
        conversation = Conversation(id=str(uuid.uuid4()), user_id=user_id, symbol=symbol, name=name)
        self.rows[(user_id, conversation.id)] = conversation
        return conversation

    async def load(self, user_id, conversation_id):
        return self.rows.get((user_id, conversation_id))

    async def append(self, conversation, turn: Turn):
        if self.append_error:
            raise self.append_error
        key = (conversation.user_id, conversation.id)
        current = self.rows[key]
        self.rows[key] = replace(current, turns=(current.turns + (turn,))[-self.max_turns:])


class MemoryQuota:
    def __init__(self, limit: int = 5, reject_limit: int = 20):
        self.limit = limit
        self.reject_limit = reject_limit
        self.used: dict[str, int] = {}
        self.rejects: dict[str, int] = {}

    async def remaining(self, user_id):
        return max(0, self.limit - self.used.get(user_id, 0))

    async def try_acquire(self, user_id):
        if self.used.get(user_id, 0) >= self.limit:
            return None
        self.used[user_id] = self.used.get(user_id, 0) + 1
        return user_id

    async def release(self, slot):
        self.used[slot] -= 1

    async def count_reject(self, user_id):
        self.rejects[user_id] = self.rejects.get(user_id, 0) + 1
        return self.rejects[user_id] <= self.reject_limit


STUB_EVIDENCE = Evidence(lines=("RSI(14): 44.2 (중립)",), as_of=TODAY)


class StubSkill:
    """자료를 정해 두고 돌려주는 스킬. evidence=None 이면 데이터 부족"""

    def __init__(self, name="indicators", evidence: Evidence | None = STUB_EVIDENCE):
        self.name = name
        self.description = f"{name} 설명"
        self.guide = f"{name} 지침"
        self.evidence = evidence
        self.gathered: list[str] = []

    async def gather(self, symbol, tools):
        self.gathered.append(symbol)
        return self.evidence
