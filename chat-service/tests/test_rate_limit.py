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
async def test_increment_reduces_remaining(redis):
    rl = RateLimiter(redis, limit=5)
    await rl.increment("user1")
    assert await rl.remaining("user1") == 4


@pytest.mark.asyncio
async def test_blocks_when_exhausted(redis):
    rl = RateLimiter(redis, limit=2)
    assert await rl.is_allowed("user1") is True
    await rl.increment("user1")
    await rl.increment("user1")
    assert await rl.is_allowed("user1") is False


@pytest.mark.asyncio
async def test_users_isolated(redis):
    rl = RateLimiter(redis, limit=5)
    await rl.increment("user1")
    assert await rl.remaining("user1") == 4
    assert await rl.remaining("user2") == 5
