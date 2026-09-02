import fakeredis.aioredis
import pytest

from app.rate_limit import RateLimiter


@pytest.fixture
def fake_redis():
    return fakeredis.aioredis.FakeRedis(decode_responses=True)


@pytest.fixture
def fake_rate_limiter(fake_redis):
    return RateLimiter(fake_redis, limit=5)
