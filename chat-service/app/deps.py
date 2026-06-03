from __future__ import annotations

import redis.asyncio as aioredis

from app.config import settings
from app.market_client import MarketClient
from app.rate_limit import RateLimiter

_redis = aioredis.Redis(
    host=settings.redis_host, port=settings.redis_port, decode_responses=True
)
_market = MarketClient(base_url=settings.market_data_base_url)


def get_redis():
    return _redis


def get_market_client() -> MarketClient:
    return _market


def get_rate_limiter() -> RateLimiter:
    return RateLimiter(_redis, limit=settings.daily_limit)
