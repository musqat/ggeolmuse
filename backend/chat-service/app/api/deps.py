from fastapi import Request

from app.infra.llm import ChatModel
from app.infra.market_api import MarketClient
from app.infra.quota import RateLimiter


def get_market_client(request: Request) -> MarketClient:
    return request.app.state.market


def get_rate_limiter(request: Request) -> RateLimiter:
    return request.app.state.quota


def get_chat_model(request: Request) -> ChatModel:
    return request.app.state.model
