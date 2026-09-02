from __future__ import annotations

from datetime import datetime, timezone


class RateLimiter:
    def __init__(self, redis, limit: int = 5, ttl_seconds: int = 90_000):
        self._redis = redis
        self._limit = limit
        self._ttl = ttl_seconds  # 25시간, 날짜키라 정리용

    def _key(self, user_id: str) -> str:
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        return f"aichat:daily:{user_id}:{today}"

    async def _count(self, user_id: str) -> int:
        val = await self._redis.get(self._key(user_id))
        return int(val) if val else 0

    async def remaining(self, user_id: str) -> int:
        return max(0, self._limit - await self._count(user_id))

    async def is_allowed(self, user_id: str) -> bool:
        return await self._count(user_id) < self._limit

    async def increment(self, user_id: str) -> int:
        key = self._key(user_id)
        new_val = await self._redis.incr(key)
        if new_val == 1:
            await self._redis.expire(key, self._ttl)
        return new_val
