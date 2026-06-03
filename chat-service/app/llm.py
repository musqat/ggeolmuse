from __future__ import annotations

from openai import OpenAI

from app.config import settings

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
    "- 제공된 지표 요약 범위 안에서만 한국어로 간결하게 설명하라"
)

_client: OpenAI | None = None


def _get_client() -> OpenAI:
    global _client
    if _client is None:
        _client = OpenAI(api_key=settings.openai_api_key)
    return _client


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


def call_router(message: str) -> str:
    """gpt-4o-mini 종목 추출. JSON 문자열 반환."""
    resp = _get_client().chat.completions.create(
        model=settings.openai_model_router,
        messages=build_router_messages(message),
        temperature=0,
        response_format={"type": "json_object"},
        max_tokens=30,
    )
    return resp.choices[0].message.content or "{}"


def call_analysis(symbol: str, summary: str, question: str) -> str:
    """gpt-4o 분석. 면책 문구 append."""
    resp = _get_client().chat.completions.create(
        model=settings.openai_model_main,
        messages=build_analysis_messages(symbol, summary, question),
        temperature=0.4,
        max_tokens=settings.max_answer_tokens,
    )
    answer = (resp.choices[0].message.content or "").strip()
    return f"{answer}\n\n{DISCLAIMER}"
