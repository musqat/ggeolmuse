from __future__ import annotations

import asyncio
import re

from openai import OpenAI, OpenAIError

from app.core.models import AiUnavailable, Completion


def _strip_markdown(text: str) -> str:
    text = re.sub(r"(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)", r"\1", text)  # *기울임* → 기울임 (제거)
    text = re.sub(r"^#{1,6}\s*", "", text, flags=re.MULTILINE)  # 제목 # 제거
    text = re.sub(r"^\s*[-*]\s+", "", text, flags=re.MULTILINE)  # 리스트 마커 제거
    return text  # **굵게**는 보존

DISCLAIMER = (
    "※ AI가 기술적 지표를 해석한 참고 자료입니다. 투자 조언이 아니며, "
    "투자 판단과 책임은 본인에게 있습니다."
)

SYSTEM_GUARD = (
    "너는 주식의 기술적 지표만 해석하는 보조 도구다. 다음 규칙을 반드시 지켜라:\n"
    "- 목표가, 매수/매도 시점을 단정하지 마라\n"
    "- '오른다/내린다' 확언 금지. '~경향', '~신호로 해석될 수 있다' 식으로 표현\n"
    "- 펀더멘털/실적/뉴스는 데이터가 없으니 언급하지 마라\n"
    "- 수익 보장·확실성 표현 금지\n"
    "- 핵심 지표명·결론만 **굵게**(별표 두 개)로 강조해라. 그 외 마크다운(*, #, 리스트 -)은 쓰지 마라\n"
    "- 제공된 지표 요약 범위 안에서만 한국어로 간결하게 설명하라"
)

def build_router_messages(message: str) -> list[dict]:
    system = (
        "사용자 메시지에서 미국 주식 종목 1개를 식별해 JSON으로만 답하라. "
        '형식: {"symbol": "TICKER"} 또는 종목이 없으면 {"symbol": null}. '
        "회사명(예: 애플)은 티커(AAPL)로 변환. 다른 텍스트 절대 출력 금지."
    )
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": message},
    ]


def build_analysis_messages(symbol: str, summary: str, question: str) -> list[dict]:
    user = (
        f"종목: {symbol}\n"
        f"기술적 지표 요약: {summary}\n"
        f"사용자 질문: {question}\n"
        "위 지표 요약만 근거로 현재 차트 국면을 설명해줘."
    )
    return [
        {"role": "system", "content": SYSTEM_GUARD},
        {"role": "user", "content": user},
    ]


class ChatModel:
    """종목 추출(mini) · 분석(main) OpenAI 호출. 실패는 AiUnavailable"""

    def __init__(self, client: OpenAI, *, router_model: str, main_model: str,
                 router_timeout: float, answer_timeout: float, max_answer_tokens: int):
        self._client = client
        self._router_model = router_model
        self._main_model = main_model
        self._router_timeout = router_timeout
        self._answer_timeout = answer_timeout
        self._max_answer_tokens = max_answer_tokens

    def route(self, message: str) -> str:
        """gpt-4o-mini 종목 추출. JSON 문자열 반환."""
        resp = self._create(
            model=self._router_model,
            messages=build_router_messages(message),
            temperature=0,
            response_format={"type": "json_object"},
            max_tokens=30,
            timeout=self._router_timeout,
        )
        return resp.choices[0].message.content or "{}"

    def analyze(self, symbol: str, summary: str, question: str) -> str:
        """gpt-4o 분석. 면책 문구 append."""
        resp = self._create(
            model=self._main_model,
            messages=build_analysis_messages(symbol, summary, question),
            temperature=0.4,
            max_tokens=self._max_answer_tokens,
            timeout=self._answer_timeout,
        )
        answer = _strip_markdown((resp.choices[0].message.content or "").strip())
        return f"{answer}\n\n{DISCLAIMER}"

    async def choose(self, messages: list[dict]) -> Completion:
        """작은 모델. JSON 한 덩어리로 답한다"""
        return await self._call(
            model=self._router_model,
            messages=messages,
            temperature=0,
            response_format={"type": "json_object"},
            max_tokens=30,
            timeout=self._router_timeout,
        )

    async def answer(self, messages: list[dict]) -> Completion:
        """큰 모델"""
        return await self._call(
            model=self._main_model,
            messages=messages,
            temperature=0.4,
            max_tokens=self._max_answer_tokens,
            timeout=self._answer_timeout,
        )

    async def _call(self, **kwargs) -> Completion:
        resp = await asyncio.to_thread(self._create, **kwargs)
        usage = getattr(resp, "usage", None)
        return Completion(
            text=resp.choices[0].message.content or "",
            tokens=getattr(usage, "total_tokens", 0) or 0,
        )

    def _create(self, **kwargs):
        try:
            return self._client.chat.completions.create(**kwargs)
        except OpenAIError as e:
            raise AiUnavailable(type(e).__name__) from e
