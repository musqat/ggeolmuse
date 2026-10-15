"""앱 조립: 시작할 때 클라이언트를 만들고, 끝날 때 닫는다"""
from __future__ import annotations

from contextlib import asynccontextmanager

import httpx
import redis.asyncio as aioredis
from fastapi import FastAPI
from openai import OpenAI

from app.api.chat import router as chat_router
from app.config import settings
from app.infra.jwks import JwksVerifier
from app.infra.llm import ChatModel
from app.infra.market_api import MarketClient
from app.infra.quota import RateLimiter


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis = aioredis.Redis(host=settings.redis_host, port=settings.redis_port, decode_responses=True)
    http = httpx.AsyncClient(base_url=settings.market_data_base_url, timeout=settings.market_timeout_seconds)
    openai = OpenAI(api_key=settings.openai_api_key, max_retries=0)
    app.state.verify_token = JwksVerifier(settings.keycloak_jwks_url)
    app.state.market = MarketClient(http)
    app.state.quota = RateLimiter(redis, limit=settings.daily_limit, reject_limit=settings.daily_reject_limit)
    app.state.model = ChatModel(
        openai,
        router_model=settings.openai_model_router,
        main_model=settings.openai_model_main,
        router_timeout=settings.router_timeout_seconds,
        answer_timeout=settings.answer_timeout_seconds,
        max_answer_tokens=settings.max_answer_tokens,
    )
    yield
    await http.aclose()
    await redis.aclose()
    openai.close()


app = FastAPI(title="ai-chat-service", lifespan=lifespan)
app.include_router(chat_router)


@app.get("/health")
def health():
    return {"status": "UP"}
