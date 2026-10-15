import asyncio
from datetime import datetime, timezone

from app.infra.quota import RateLimiter


def _limiter(redis, limit=5, reject_limit=20, now=None):
    if now is None:
        return RateLimiter(redis, limit=limit, reject_limit=reject_limit)
    return RateLimiter(redis, limit=limit, reject_limit=reject_limit, now=now)


async def test_remaining_starts_at_limit(fake_redis):
    assert await _limiter(fake_redis).remaining("user1") == 5


async def test_acquire_until_limit(fake_redis):
    rl = _limiter(fake_redis, limit=2)
    assert await rl.try_acquire("user1") is not None
    assert await rl.try_acquire("user1") is not None
    assert await rl.try_acquire("user1") is None
    assert await rl.remaining("user1") == 0


async def test_concurrent_acquire_never_exceeds_limit(interleaving_redis):
    rl = _limiter(interleaving_redis)
    slots = await asyncio.gather(*(rl.try_acquire("user1") for _ in range(12)))
    assert sum(slot is not None for slot in slots) == 5
    assert await rl.remaining("user1") == 0


async def test_release_returns_slot(fake_redis):
    rl = _limiter(fake_redis)
    slot = await rl.try_acquire("user1")
    await rl.release(slot)
    assert await rl.remaining("user1") == 5


async def test_release_uses_acquired_day(fake_redis):
    # 자정을 넘겨 돌려줘도 잡은 날의 칸을 돌려준다
    clock = {"now": datetime(2026, 10, 16, 14, 59, tzinfo=timezone.utc)}
    rl = _limiter(fake_redis, now=lambda: clock["now"])
    slot = await rl.try_acquire("user1")
    clock["now"] = datetime(2026, 10, 16, 15, 1, tzinfo=timezone.utc)
    await rl.release(slot)
    assert await fake_redis.get(slot) == "0"
    assert await rl.remaining("user1") == 5


async def test_day_is_korean_date(fake_redis):
    # UTC 15시가 한국 자정
    before = _limiter(fake_redis, now=lambda: datetime(2026, 10, 16, 14, 59, tzinfo=timezone.utc))
    after = _limiter(fake_redis, now=lambda: datetime(2026, 10, 16, 15, 0, tzinfo=timezone.utc))
    assert await before.try_acquire("user1") == "aichat:daily:user1:2026-10-16"
    assert await after.try_acquire("user1") == "aichat:daily:user1:2026-10-17"


async def test_first_acquire_sets_ttl(fake_redis):
    slot = await _limiter(fake_redis).try_acquire("user1")
    assert 0 < await fake_redis.ttl(slot) <= 90_000


async def test_users_isolated(fake_redis):
    rl = _limiter(fake_redis)
    await rl.try_acquire("user1")
    assert await rl.remaining("user1") == 4
    assert await rl.remaining("user2") == 5


async def test_reject_cap(fake_redis):
    rl = _limiter(fake_redis, reject_limit=2)
    assert [await rl.count_reject("user1") for _ in range(3)] == [True, True, False]
    assert await rl.count_reject("user2") is True
