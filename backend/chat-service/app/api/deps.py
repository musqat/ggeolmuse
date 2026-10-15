from __future__ import annotations

import redis.asyncio as aioredis

from app.config import settings
from app.infra.llm import ChatModel
from app.infra.market_api import MarketClient
from app.infra.quota import RateLimiter

_redis = aioredis.Redis(
    host=settings.redis_host, port=settings.redis_port, decode_responses=True
)
_market = MarketClient(base_url=settings.market_data_base_url)
_chat_model = ChatModel()


def get_redis():
    return _redis


def get_market_client() -> MarketClient:
    return _market


def get_rate_limiter() -> RateLimiter:
    return RateLimiter(_redis, limit=settings.daily_limit)


def get_chat_model() -> ChatModel:
    return _chat_model
