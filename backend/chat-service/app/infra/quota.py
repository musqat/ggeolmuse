"""Redis 하루 한도와 거절 한도. 하루는 한국 날짜"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Callable

KST = timezone(timedelta(hours=9))
KEY_TTL_SECONDS = 90_000  # 25시간, 날짜 키 정리용


def _now_kst() -> datetime:
    return datetime.now(KST)


class RateLimiter:
    def __init__(self, redis, limit: int, reject_limit: int,
                 now: Callable[[], datetime] = _now_kst):
        self._redis = redis
        self._limit = limit
        self._reject_limit = reject_limit
        self._now = now

    def _day(self) -> str:
        return self._now().astimezone(KST).strftime("%Y-%m-%d")

    def _key(self, user_id: str) -> str:
        return f"aichat:daily:{user_id}:{self._day()}"

    async def remaining(self, user_id: str) -> int:
        val = await self._redis.get(self._key(user_id))
        return max(0, self._limit - (int(val) if val else 0))

    # 오늘 한 칸을 잡고 그 키를 준다. 먼저 올려(INCR) 동시에 와도 한도를 안 넘는다. 넘으면 되돌리고 None
    async def try_acquire(self, user_id: str) -> str | None:
        key = self._key(user_id)
        count = await self._redis.incr(key)
        if count == 1:
            await self._redis.expire(key, KEY_TTL_SECONDS)
        if count > self._limit:
            await self._redis.decr(key)
            return None
        return key

    # 잡은 칸을 돌려준다. 자정을 넘겨도 잡은 날의 키를 쓴다
    async def release(self, slot: str) -> None:
        await self._redis.decr(slot)

    async def count_reject(self, user_id: str) -> bool:
        """거절 한 번 기록. 오늘 거절이 한도 안이면 True(칸을 돌려줌)"""
        key = f"aichat:rejected:{user_id}:{self._day()}"
        count = await self._redis.incr(key)
        if count == 1:
            await self._redis.expire(key, KEY_TTL_SECONDS)
        return count <= self._reject_limit
