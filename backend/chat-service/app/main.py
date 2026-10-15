"""앱 조립: 시작할 때 클라이언트를 만들어 흐름에 넣고, 끝날 때 닫는다"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

import httpx
import redis.asyncio as aioredis
from fastapi import FastAPI
from openai import OpenAI

from app.api.chat import router as chat_router
from app.api.errors import register_error_handlers
from app.config import Settings, settings
from app.core.answerer import Answerer
from app.core.chat_service import ChatService
from app.core.router import Router
from app.core.skills import build_skills
from app.core.tools.market import MarketTools
from app.infra.conversations import RedisConversationStore
from app.infra.jwks import JwksVerifier
from app.infra.llm import ChatModel
from app.infra.market_api import MarketClient
from app.infra.quota import RateLimiter

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


def build_openai(config: Settings) -> OpenAI:
    """OpenAI 클라이언트. 재시도 없이 호출마다 시간 제한만 둔다"""
    return OpenAI(api_key=config.openai_api_key, max_retries=0)


def build_redis(config: Settings) -> aioredis.Redis:
    """Redis 클라이언트. 운영 · 로컬 Redis 는 비밀번호를 요구한다(requirepass)"""
    return aioredis.Redis(host=config.redis_host, port=config.redis_port,
                          password=config.redis_password or None, decode_responses=True)


def build_chat_service(config: Settings, redis, http: httpx.AsyncClient, openai: OpenAI) -> ChatService:
    model = ChatModel(
        openai,
        router_model=config.openai_model_router,
        main_model=config.openai_model_main,
        router_timeout=config.router_timeout_seconds,
        answer_timeout=config.answer_timeout_seconds,
        max_answer_tokens=config.max_answer_tokens,
    )
    return ChatService(
        tools=MarketTools(MarketClient(http)),
        store=RedisConversationStore(
            redis,
            ttl_seconds=config.conversation_ttl_hours * 3600,
            max_turns=config.history_turns,
            answer_chars=config.history_answer_chars,
        ),
        quota=RateLimiter(redis, limit=config.daily_limit, reject_limit=config.daily_reject_limit),
        router=Router(model, build_skills(config.ohlc_lookback_days, config.dividend_lookback_years)),
        answerer=Answerer(model, history_turns=config.history_turns),
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis = build_redis(settings)
    http = httpx.AsyncClient(base_url=settings.market_data_base_url, timeout=settings.market_timeout_seconds)
    openai = build_openai(settings)
    app.state.verify_token = JwksVerifier(settings.keycloak_jwks_url)
    app.state.chat_service = build_chat_service(settings, redis, http, openai)
    yield
    await http.aclose()
    await redis.aclose()
    openai.close()


app = FastAPI(title="ai-chat-service", lifespan=lifespan)
register_error_handlers(app)
app.include_router(chat_router)


@app.get("/health")
def health():
    return {"status": "UP"}
