"""주가 추이 스킬. 기간 수익률 · 52주 범위 · 낙폭 · 변동성을 자료로 만든다"""
from __future__ import annotations

from app.core.analysis.trend import PERIODS, TrendSnapshot, change, compute_trend
from app.core.models import Evidence
from app.core.tools.market import MarketTools


def trend_lines(s: TrendSnapshot) -> list[str]:
    """추이 스냅숏 → 자료 줄"""
    lines = [f"기준일 {s.as_of.isoformat()} 종가 {s.close:.2f} (최근 {s.bars}거래일)"]
    parts = [f"{r.label} {r.price:+.1f}% / {r.total:+.1f}%" for r in s.returns]
    lines.append("기간 수익률(가격 / 배당 포함): " + ", ".join(parts))
    if s.year_range:
        high, low = s.year_range.high, s.year_range.low
        lines.append(
            f"52주 최고 {high:.2f}(지금 {change(high, s.close):+.1f}%), "
            f"최저 {low:.2f}(지금 {change(low, s.close):+.1f}%)"
        )
    if s.drawdown:
        d = s.drawdown
        lines.append(
            f"1년 최대 낙폭 {d.percent:.1f}% ({d.peak_date.isoformat()} 고점 → {d.trough_date.isoformat()} 저점)"
        )
    if s.volatility is not None:
        lines.append(f"변동성(1년 일간 수익률, 연율) {s.volatility:.1f}%")
    missing = _missing(s)
    if missing:
        lines.append(f"계산 못 한 값: {', '.join(missing)} (데이터 {s.bars}거래일)")
    return lines


def _missing(s: TrendSnapshot) -> list[str]:
    done = {r.label for r in s.returns}
    missing = [f"{label} 수익률" for label, _ in PERIODS if label not in done]
    optional = (("52주 범위", s.year_range), ("1년 최대 낙폭", s.drawdown), ("변동성", s.volatility))
    return missing + [name for name, value in optional if value is None]


class TrendSkill:
    name = "trend"
    description = "주가 흐름: 기간 수익률(1주~1년), 52주 최고 · 최저, 최대 낙폭, 변동성"
    guide = (
        "자료는 일봉으로 계산한 주가 흐름이다. 가격 수익률은 분할만 조정한 종가, 배당 포함 수익률은 "
        "배당까지 조정한 종가로 계산했다. 묻는 기간에 맞는 값을 골라 답하고, 자료에 없는 기간은 "
        "계산하지 않았다고 말한다."
    )

    def __init__(self, lookback_days: int):
        self._lookback_days = lookback_days

    async def gather(self, symbol: str, tools: MarketTools) -> Evidence | None:
        snapshot = compute_trend(await tools.bars(symbol, self._lookback_days))
        if snapshot is None:
            return None
        return Evidence(lines=tuple(trend_lines(snapshot)), as_of=snapshot.as_of)
