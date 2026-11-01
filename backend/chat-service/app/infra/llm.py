"""OpenAI 호출(동기 클라이언트를 스레드로 넘김, 실패는 AiUnavailable)"""
from __future__ import annotations

import asyncio

from openai import OpenAI, OpenAIError

from app.core.models import AiUnavailable, Completion


class ChatModel:
    def __init__(self, client: OpenAI, *, router_model: str, main_model: str,
                 router_timeout: float, answer_timeout: float, max_answer_tokens: int):
        self._client = client
        self._router_model = router_model
        self._main_model = main_model
        self._router_timeout = router_timeout
        self._answer_timeout = answer_timeout
        self._max_answer_tokens = max_answer_tokens

    async def choose(self, messages: list[dict]) -> Completion:
        """작은 모델(JSON 한 덩어리로 답함)"""
        return await self._call(
            model=self._router_model,
            messages=messages,
            temperature=0,
            response_format={"type": "json_object"},
            max_tokens=30,
            timeout=self._router_timeout,
        )

    async def answer(self, messages: list[dict]) -> Completion:
        """큰 모델"""
        return await self._call(
            model=self._main_model,
            messages=messages,
            temperature=0.4,
            max_tokens=self._max_answer_tokens,
            timeout=self._answer_timeout,
        )

    async def _call(self, **kwargs) -> Completion:
        try:
            resp = await asyncio.to_thread(self._client.chat.completions.create, **kwargs)
        except OpenAIError as e:
            raise AiUnavailable(type(e).__name__) from e
        usage = getattr(resp, "usage", None)
        return Completion(
            text=resp.choices[0].message.content or "",
            tokens=getattr(usage, "total_tokens", 0) or 0,
        )
