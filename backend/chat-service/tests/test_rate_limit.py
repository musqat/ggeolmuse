import asyncio

import fakeredis.aioredis
import pytest

from app.rate_limit import RateLimiter


@pytest.fixture
def redis():
    return fakeredis.aioredis.FakeRedis(decode_responses=True)


@pytest.mark.asyncio
async def test_remaining_starts_at_limit(redis):
    rl = RateLimiter(redis, limit=5)
    assert await rl.remaining("user1") == 5


@pytest.mark.asyncio
async def test_acquire_until_limit(redis):
    rl = RateLimiter(redis, limit=2)
    assert await rl.try_acquire("user1") is not None
    assert await rl.try_acquire("user1") is not None
    assert await rl.try_acquire("user1") is None
    assert await rl.remaining("user1") == 0


@pytest.mark.asyncio
async def test_concurrent_acquire_never_exceeds_limit(redis):
    rl = RateLimiter(redis, limit=5)
    slots = await asyncio.gather(*(rl.try_acquire("user1") for _ in range(12)))
    assert sum(slot is not None for slot in slots) == 5
    assert await rl.remaining("user1") == 0


@pytest.mark.asyncio
async def test_rejected_attempt_leaves_no_count(redis):
    # 한도를 넘긴 시도는 되돌려서, 칸을 돌려주면 다시 잡힌다
    rl = RateLimiter(redis, limit=1)
    slot = await rl.try_acquire("user1")
    assert await rl.try_acquire("user1") is None
    await rl.release(slot)
    assert await rl.remaining("user1") == 1


@pytest.mark.asyncio
async def test_release_returns_slot(redis):
    rl = RateLimiter(redis, limit=5)
    slot = await rl.try_acquire("user1")
    await rl.release(slot)
    assert await rl.remaining("user1") == 5


@pytest.mark.asyncio
async def test_release_uses_acquired_day(redis, monkeypatch):
    # 자정을 넘겨 돌려줘도 잡은 날의 칸을 돌려준다
    rl = RateLimiter(redis, limit=5)
    slot = await rl.try_acquire("user1")
    monkeypatch.setattr(rl, "_key", lambda user_id: "aichat:daily:user1:next-day")
    await rl.release(slot)
    assert await redis.get(slot) == "0"
    assert await redis.get("aichat:daily:user1:next-day") is None


@pytest.mark.asyncio
async def test_first_acquire_sets_ttl(redis):
    rl = RateLimiter(redis, limit=5, ttl_seconds=100)
    slot = await rl.try_acquire("user1")
    assert 0 < await redis.ttl(slot) <= 100


@pytest.mark.asyncio
async def test_users_isolated(redis):
    rl = RateLimiter(redis, limit=5)
    await rl.try_acquire("user1")
    assert await rl.remaining("user1") == 4
    assert await rl.remaining("user2") == 5
