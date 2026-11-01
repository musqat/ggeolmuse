"""Redis 대화 저장(키에 사용자가 들어 있어 다른 사용자의 대화는 못 읽음)"""
from __future__ import annotations

import json
import uuid

from app.core.models import Conversation, Turn


def _meta_key(user_id: str, conversation_id: str) -> str:
    return f"aichat:conv:{user_id}:{conversation_id}"


def _turns_key(user_id: str, conversation_id: str) -> str:
    return f"{_meta_key(user_id, conversation_id)}:turns"


def _is_uuid(value: str) -> bool:
    try:
        uuid.UUID(value)
    except ValueError:
        return False
    return True


class RedisConversationStore:
    def __init__(self, redis, ttl_seconds: int, max_turns: int, answer_chars: int):
        self._redis = redis
        self._ttl = ttl_seconds
        self._max_turns = max_turns
        self._answer_chars = answer_chars

    async def create(self, user_id: str, symbol: str, name: str | None) -> Conversation:
        conversation_id = str(uuid.uuid4())
        key = _meta_key(user_id, conversation_id)
        async with self._redis.pipeline(transaction=True) as pipe:
            pipe.hset(key, mapping={"symbol": symbol, "name": name or ""})
            pipe.expire(key, self._ttl)
            await pipe.execute()
        return Conversation(id=conversation_id, user_id=user_id, symbol=symbol, name=name)

    async def load(self, user_id: str, conversation_id: str) -> Conversation | None:
        if not _is_uuid(conversation_id):
            return None
        meta = await self._redis.hgetall(_meta_key(user_id, conversation_id))
        if not meta:
            return None
        rows = await self._redis.lrange(_turns_key(user_id, conversation_id), 0, -1)
        turns = tuple(Turn(question=t["q"], answer=t["a"], skill=t["skill"]) for t in map(json.loads, rows))
        return Conversation(id=conversation_id, user_id=user_id, symbol=meta["symbol"],
                            name=meta.get("name") or None, turns=turns)

    async def append(self, conversation: Conversation, turn: Turn) -> None:
        """턴을 붙이고 최근 max_turns 개만 남김(두 키의 수명 다시 설정)"""
        meta = _meta_key(conversation.user_id, conversation.id)
        turns = _turns_key(conversation.user_id, conversation.id)
        row = json.dumps({"q": turn.question, "a": turn.answer[: self._answer_chars], "skill": turn.skill},
                         ensure_ascii=False)
        async with self._redis.pipeline(transaction=True) as pipe:
            pipe.rpush(turns, row)
            pipe.ltrim(turns, -self._max_turns, -1)
            pipe.expire(turns, self._ttl)
            pipe.expire(meta, self._ttl)
            await pipe.execute()
