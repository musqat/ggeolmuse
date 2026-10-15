import logging

import pytest

from app.core.answerer import Answerer
from app.core.chat_service import Answered, ChatService, Rejected
from app.core.messages import DISCLAIMER
from app.core.models import (
    AiUnavailable,
    ConversationNotFound,
    MarketUnavailable,
    Quote,
    QuotaExceeded,
    SymbolNotFound,
)
from app.core.router import Router
from app.core.tools.market import MarketTools
from tests.core.fakes import TODAY, FakeMarket, FakeModel, MemoryQuota, MemoryStore, StubSkill


class Env:
    def __init__(self, choose='{"skill": "indicators"}', skill=None, reject_limit=20):
        self.market = FakeMarket()
        self.model = FakeModel(choose=choose)
        self.store = MemoryStore()
        self.quota = MemoryQuota(limit=5, reject_limit=reject_limit)
        self.skill = skill or StubSkill("indicators")
        self.service = ChatService(
            tools=MarketTools(self.market, today=lambda: TODAY),
            store=self.store,
            quota=self.quota,
            router=Router(self.model, [self.skill]),
            answerer=Answerer(self.model, history_turns=3),
        )

    async def conversation_id(self, user_id="u1"):
        return (await self.service.start(user_id, "AAPL")).conversation.id


async def test_start_creates_conversation_without_using_quota():
    env = Env()
    result = await env.service.start("u1", " aapl ")

    assert result.conversation.symbol == "AAPL"
    assert result.conversation.name == "Apple Inc."
    assert result.remaining == 5
    assert env.model.chosen == [] and env.model.answered == []


@pytest.mark.parametrize("raw", ["애플", "BRK.B", "", "A" * 17])
async def test_start_rejects_bad_format_without_market_call(raw):
    env = Env()
    with pytest.raises(SymbolNotFound):
        await env.service.start("u1", raw)
    assert env.market.calls == []


async def test_start_rejects_unknown_and_delisted():
    env = Env()
    env.market.quotes["OLD"] = Quote(symbol="OLD", name="Old Co.", price=1.0, as_of=TODAY, active=False)
    for symbol in ("ZZZZ", "OLD"):
        with pytest.raises(SymbolNotFound):
            await env.service.start("u1", symbol)


async def test_start_lets_unknown_listing_through_with_warning(caplog):
    env = Env()
    env.market.quotes["NEW"] = Quote(symbol="NEW", name=None, price=1.0, as_of=TODAY, active=None)
    with caplog.at_level(logging.WARNING):
        result = await env.service.start("u1", "NEW")
    assert result.conversation.symbol == "NEW"
    assert "symbol=NEW" in caplog.text


async def test_ask_answers_and_uses_one_slot():
    env = Env()
    conversation_id = await env.conversation_id()
    result = await env.service.ask("u1", conversation_id, "RSI 어때?")

    assert result.outcome == Answered(text=f"상승 경향으로 해석될 수 있습니다.\n\n{DISCLAIMER}", skill="indicators")
    assert result.remaining == 4
    assert (result.skill, result.llm_calls, result.tokens) == ("indicators", 2, 110)
    stored = (await env.store.load("u1", conversation_id)).turns
    assert [(t.question, t.answer, t.skill) for t in stored] == [
        ("RSI 어때?", "상승 경향으로 해석될 수 있습니다.", "indicators")]


async def test_follow_up_sees_previous_turn():
    env = Env()
    conversation_id = await env.conversation_id()
    await env.service.ask("u1", conversation_id, "RSI 어때?")
    await env.service.ask("u1", conversation_id, "왜?")

    assert "직전 질문: RSI 어때?" in env.model.chosen[1][0]["content"]
    assert [m["content"] for m in env.model.answered[1][1:3]] == [
        "RSI 어때?", "상승 경향으로 해석될 수 있습니다."]


async def test_ask_unknown_or_other_users_conversation():
    env = Env()
    conversation_id = await env.conversation_id("u1")
    for user_id, cid in (("u1", "no-such-id"), ("u2", conversation_id)):
        with pytest.raises(ConversationNotFound):
            await env.service.ask(user_id, cid, "RSI 어때?")
    assert env.quota.used == {}


async def test_ask_without_quota_does_not_route():
    env = Env()
    conversation_id = await env.conversation_id()
    env.quota.used["u1"] = 5
    with pytest.raises(QuotaExceeded):
        await env.service.ask("u1", conversation_id, "RSI 어때?")
    assert env.model.chosen == []


async def test_rejection_returns_slot():
    env = Env(choose='{"skill": null, "reason": "other_symbol"}')
    conversation_id = await env.conversation_id()
    result = await env.service.ask("u1", conversation_id, "MSFT 는?")

    assert result.outcome == Rejected(
        reason="other_symbol",
        text="이 대화는 AAPL 전용입니다. 다른 종목은 '종목 바꾸기'로 새로 시작해 주세요.")
    assert result.remaining == 5
    assert (result.skill, result.llm_calls) == (None, 1)
    assert (await env.store.load("u1", conversation_id)).turns == ()


async def test_rejections_over_cap_use_quota():
    env = Env(choose='{"skill": null, "reason": "off_topic"}', reject_limit=1)
    conversation_id = await env.conversation_id()
    first = await env.service.ask("u1", conversation_id, "날씨?")
    second = await env.service.ask("u1", conversation_id, "날씨?")
    assert (first.remaining, second.remaining) == (5, 4)


async def test_insufficient_data_is_rejection():
    env = Env(skill=StubSkill("indicators", evidence=None))
    conversation_id = await env.conversation_id()
    result = await env.service.ask("u1", conversation_id, "RSI 어때?")

    assert result.outcome.reason == "insufficient_data"
    assert result.skill == "indicators"
    assert result.remaining == 5


@pytest.mark.parametrize("fail", ["router", "market", "answer", "store"])
async def test_failures_return_slot_and_store_nothing(fail):
    env = Env()
    conversation_id = await env.conversation_id()
    if fail == "router":
        env.model.choose_error = AiUnavailable("timeout")
    elif fail == "market":
        env.skill.gather = _raise(MarketUnavailable("down"))
    elif fail == "answer":
        env.model.answer_error = AiUnavailable("timeout")
    else:
        env.store.append_error = RuntimeError("redis down")

    with pytest.raises(Exception):
        await env.service.ask("u1", conversation_id, "RSI 어때?")
    assert await env.quota.remaining("u1") == 5
    assert (await env.store.load("u1", conversation_id)).turns == ()


def _raise(error):
    async def gather(symbol, tools):
        raise error
    return gather
