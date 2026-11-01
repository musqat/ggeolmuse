"""종목 대화 흐름: 대화 시작과 질문"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from app.core.answerer import Answerer
from app.core.messages import DISCLAIMER, REJECTIONS
from app.core.models import (
    Conversation,
    ConversationNotFound,
    QuotaExceeded,
    SymbolNotFound,
    Turn,
)
from app.core.ports import ConversationStore, Quota
from app.core.router import Router
from app.core.tools.market import MarketTools

log = logging.getLogger(__name__)

SYMBOL_PATTERN = re.compile(r"^[A-Z0-9.^-]{1,16}$")


@dataclass(frozen=True)
class StartResult:
    conversation: Conversation
    remaining: int


@dataclass(frozen=True)
class Answered:
    text: str  # 면책 문구를 붙인 답
    skill: str


@dataclass(frozen=True)
class Rejected:
    reason: str  # off_topic · other_symbol · insufficient_data
    text: str


@dataclass(frozen=True)
class AskResult:
    outcome: Answered | Rejected
    remaining: int
    conversation: Conversation
    skill: str | None  # 라우터가 고른 스킬
    llm_calls: int
    tokens: int


class ChatService:
    def __init__(self, tools: MarketTools, store: ConversationStore, quota: Quota,
                 router: Router, answerer: Answerer):
        self._tools = tools
        self._store = store
        self._quota = quota
        self._router = router
        self._answerer = answerer

    async def start(self, user_id: str, raw_symbol: str) -> StartResult:
        """종목 확인 · 대화 생성(AI 호출 · 한도 사용 없음)"""
        symbol = raw_symbol.strip().upper()
        if not SYMBOL_PATTERN.match(symbol):
            raise SymbolNotFound(symbol)
        quote = await self._tools.quote(symbol)
        if quote is None or quote.active is False:
            raise SymbolNotFound(symbol)
        if quote.active is None:
            log.warning("상장 여부 없음, 통과: symbol=%s", symbol)
        conversation = await self._store.create(user_id, symbol, quote.name)
        return StartResult(conversation=conversation, remaining=await self._quota.remaining(user_id))

    async def remaining(self, user_id: str) -> int:
        """오늘 남은 질문 수(한도 사용 없음)"""
        return await self._quota.remaining(user_id)

    async def ask(self, user_id: str, conversation_id: str, question: str) -> AskResult:
        """한도 한 칸을 잡고 답변(거절 · 예외면 칸 반환, 거절 한도를 넘으면 반환 안 함)"""
        conversation = await self._store.load(user_id, conversation_id)
        if conversation is None:
            raise ConversationNotFound(conversation_id)
        slot = await self._quota.try_acquire(user_id)
        if slot is None:
            raise QuotaExceeded(user_id)
        try:
            outcome, skill, calls, tokens = await self._run(conversation, question)
        except Exception:
            await self._quota.release(slot)
            raise
        if isinstance(outcome, Rejected) and await self._quota.count_reject(user_id):
            await self._quota.release(slot)
        return AskResult(
            outcome=outcome,
            remaining=await self._quota.remaining(user_id),
            conversation=conversation,
            skill=skill,
            llm_calls=calls,
            tokens=tokens,
        )

    async def _run(self, conversation: Conversation,
                   question: str) -> tuple[Answered | Rejected, str | None, int, int]:
        decision = await self._router.decide(conversation, question)
        if decision.skill is None:
            return self._rejected(conversation, decision.reason or "off_topic"), None, 1, decision.tokens
        skill = decision.skill
        evidence = await skill.gather(conversation.symbol, self._tools)
        if evidence is None:
            return self._rejected(conversation, "insufficient_data"), skill.name, 1, decision.tokens
        completion = await self._answerer.answer(conversation, skill, evidence, question)
        await self._store.append(conversation, Turn(question=question, answer=completion.text, skill=skill.name))
        answered = Answered(text=f"{completion.text}\n\n{DISCLAIMER}", skill=skill.name)
        return answered, skill.name, 2, decision.tokens + completion.tokens

    @staticmethod
    def _rejected(conversation: Conversation, reason: str) -> Rejected:
        return Rejected(reason=reason, text=REJECTIONS[reason].format(symbol=conversation.symbol))
