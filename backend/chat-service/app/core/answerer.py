"""자료 · 최근 턴 · 질문으로 답 쓰기(큰 모델)"""
from __future__ import annotations

import re

from app.core.messages import ANSWER_RULES
from app.core.models import Completion, Conversation, Evidence
from app.core.ports import LanguageModel
from app.core.skills.base import Skill


def strip_markdown(text: str) -> str:
    """*기울임* · 제목 # · 목록 기호를 지운다. **굵게** 는 둔다"""
    text = re.sub(r"(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)", r"\1", text)
    text = re.sub(r"^#{1,6}\s*", "", text, flags=re.MULTILINE)
    text = re.sub(r"^\s*[-*]\s+", "", text, flags=re.MULTILINE)
    return text


class Answerer:
    def __init__(self, model: LanguageModel, history_turns: int):
        self._model = model
        self._history_turns = history_turns

    async def answer(self, conversation: Conversation, skill: Skill, evidence: Evidence,
                     question: str) -> Completion:
        """면책 문구 없는 답"""
        completion = await self._model.answer(self.messages(conversation, skill, evidence, question))
        return Completion(text=strip_markdown(completion.text.strip()), tokens=completion.tokens)

    def messages(self, conversation: Conversation, skill: Skill, evidence: Evidence,
                 question: str) -> list[dict]:
        messages = [{"role": "system", "content": f"{ANSWER_RULES}\n\n{skill.guide}"}]
        history = conversation.turns[-self._history_turns:] if self._history_turns > 0 else ()
        for turn in history:
            messages.append({"role": "user", "content": turn.question})
            messages.append({"role": "assistant", "content": turn.answer})
        name = f" ({conversation.name})" if conversation.name else ""
        data = "\n".join(f"- {line}" for line in evidence.lines)
        messages.append({
            "role": "user",
            "content": (
                f"종목: {conversation.symbol}{name}\n기준일: {evidence.as_of.isoformat()}\n"
                f"자료:\n{data}\n\n질문: {question}"
            ),
        })
        return messages
