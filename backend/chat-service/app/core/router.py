"""질문에 맞는 스킬 고르기(작은 모델)"""
from __future__ import annotations

import json
from dataclasses import dataclass

from app.core.models import Conversation
from app.core.ports import LanguageModel
from app.core.skills.base import Skill

REASONS = ("other_symbol", "off_topic")


@dataclass(frozen=True)
class RouteDecision:
    skill: Skill | None
    reason: str | None  # skill 이 None 일 때 거절 이유
    tokens: int


class Router:
    def __init__(self, model: LanguageModel, skills: list[Skill]):
        self._model = model
        self._skills = {skill.name: skill for skill in skills}

    async def decide(self, conversation: Conversation, question: str) -> RouteDecision:
        completion = await self._model.choose(self.messages(conversation, question))
        skill, reason = self._parse(completion.text)
        return RouteDecision(skill=skill, reason=reason, tokens=completion.tokens)

    def messages(self, conversation: Conversation, question: str) -> list[dict]:
        skills = "\n".join(f"- {s.name}: {s.description}" for s in self._skills.values())
        name = f" ({conversation.name})" if conversation.name else ""
        last = conversation.last_turn
        previous = f"직전 스킬: {last.skill}\n직전 질문: {last.question}" if last else "직전 질문 없음"
        system = (
            f"너는 주식 질문을 분류한다. 이 대화의 종목은 {conversation.symbol}{name}이다.\n"
            f"스킬 목록:\n{skills}\n{previous}\n"
            '질문에 맞는 스킬 하나를 골라 JSON 으로만 답하라: {"skill": "<이름>"}\n'
            "\"왜?\" \"더 자세히\" 처럼 이어 묻는 질문은 직전 스킬을 고른다.\n"
            f'{conversation.symbol} 이 아닌 다른 종목을 물으면 {{"skill": null, "reason": "other_symbol"}}\n'
            '시세 · 지표 · 배당과 관계없는 질문이면 {"skill": null, "reason": "off_topic"}'
        )
        return [{"role": "system", "content": system}, {"role": "user", "content": question}]

    def _parse(self, text: str) -> tuple[Skill | None, str | None]:
        """JSON 이 아니거나 모르는 스킬이면 off_topic"""
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            return None, "off_topic"
        if not isinstance(data, dict):
            return None, "off_topic"
        name = data.get("skill")
        skill = self._skills.get(name) if isinstance(name, str) else None
        if skill is not None:
            return skill, None
        reason = data.get("reason")
        return None, reason if reason in REASONS else "off_topic"
