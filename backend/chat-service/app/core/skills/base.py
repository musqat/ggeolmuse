"""스킬 계약"""
from __future__ import annotations

from typing import Protocol

from app.core.models import Evidence
from app.core.tools.market import MarketTools


class Skill(Protocol):
    name: str  # 라우터가 고르는 값
    description: str  # 라우터 프롬프트의 한 줄
    guide: str  # 답변기 시스템 프롬프트에 붙는 해석 지침

    async def gather(self, symbol: str, tools: MarketTools) -> Evidence | None: ...
