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

    # 오늘 한 칸을 잡고 그 키를 준다. 먼저 올려(INCR) 동시에 와도 한도를 안 넘는다. 넘으면 되돌리고 None
    async def try_acquire(self, user_id: str) -> str | None:
        key = self._key(user_id)
        count = await self._redis.incr(key)
        if count == 1:
            await self._redis.expire(key, self._ttl)
        if count > self._limit:
            await self._redis.decr(key)
            return None
        return key

    # 잡은 칸을 돌려준다. 자정을 넘겨도 잡은 날의 키를 쓴다
    async def release(self, key: str) -> None:
        await self._redis.decr(key)
