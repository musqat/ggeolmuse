"""chat-service 안쪽에서 쓰는 데이터와 도메인 예외"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class Bar:
    """일봉. close 는 분할만 조정한 종가(차트와 같음), adj_close 는 배당까지 조정한 종가(없으면 None)"""
    date: date
    open: float
    high: float
    low: float
    close: float
    adj_close: float | None
    volume: float


@dataclass(frozen=True)
class Quote:
    """현재가. active 가 None 이면 상장 여부를 모름"""
    symbol: str
    name: str | None
    price: float
    as_of: date
    active: bool | None


@dataclass(frozen=True)
class Dividend:
    ex_date: date
    amount: float
    currency: str


@dataclass(frozen=True)
class Evidence:
    """답변기에 넘기는 자료. lines 는 프롬프트에 한 줄씩 들어간다"""
    lines: tuple[str, ...]
    as_of: date


@dataclass(frozen=True)
class Completion:
    text: str
    tokens: int


@dataclass(frozen=True)
class Turn:
    question: str
    answer: str
    skill: str


@dataclass(frozen=True)
class Conversation:
    id: str
    user_id: str
    symbol: str
    name: str | None
    turns: tuple[Turn, ...] = ()

    @property
    def last_turn(self) -> Turn | None:
        return self.turns[-1] if self.turns else None


class SymbolNotFound(Exception):
    """형식이 틀렸거나, 시세가 없거나, 상장폐지된 종목"""


class ConversationNotFound(Exception):
    """없는 · 만료된 · 다른 사용자의 대화"""


class QuotaExceeded(Exception):
    """오늘 한도를 다 씀"""


class MarketUnavailable(Exception):
    """시세 API 5xx · 시간 초과 · 연결 실패"""


class AiUnavailable(Exception):
    """OpenAI 오류 · 시간 초과 · 속도 제한"""


class AuthUnavailable(Exception):
    """인증 서버에서 공개키를 못 받음"""
