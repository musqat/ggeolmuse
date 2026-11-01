"""기간 수익률 · 52주 범위 · 최대 낙폭 · 변동성"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from math import sqrt
from statistics import stdev

from app.core.models import Bar

PERIODS = (("1주", 5), ("1달", 21), ("3달", 63), ("6달", 126), ("1년", 252))
YEAR = 252


@dataclass(frozen=True)
class PeriodReturn:
    label: str
    price: float | None  # %, 분할만 조정한 종가
    total: float | None  # %, 배당까지 조정한 종가


@dataclass(frozen=True)
class YearRange:
    high: float
    low: float


@dataclass(frozen=True)
class Drawdown:
    percent: float  # 0 이하 %
    peak_date: date
    trough_date: date


@dataclass(frozen=True)
class TrendSnapshot:
    as_of: date
    bars: int
    close: float
    returns: tuple[PeriodReturn, ...]
    year_range: YearRange | None
    drawdown: Drawdown | None
    volatility: float | None  # 연율 %


def compute_trend(bars: list[Bar]) -> TrendSnapshot | None:
    """마지막 캔들 기준 추이(가장 짧은 기간 수익률도 못 구하면 None)"""
    if len(bars) <= PERIODS[0][1]:
        return None
    last = bars[-1]
    returns = tuple(
        PeriodReturn(label, change(bars[-1 - n].close, last.close), change(bars[-1 - n].adj_close, last.adj_close))
        for label, n in PERIODS
        if len(bars) > n
    )
    year = bars[-YEAR:] if len(bars) >= YEAR else None
    return TrendSnapshot(
        as_of=last.date,
        bars=len(bars),
        close=last.close,
        returns=returns,
        year_range=YearRange(max(b.high for b in year), min(b.low for b in year)) if year else None,
        drawdown=_drawdown(year) if year else None,
        volatility=_volatility(bars[-(YEAR + 1):]) if len(bars) > YEAR else None,
    )


def change(start: float | None, end: float | None) -> float | None:
    """값이 비었거나 시작 값이 0 이하면 None"""
    if start is None or end is None or start <= 0:
        return None
    return (end / start - 1) * 100


def _drawdown(bars: list[Bar]) -> Drawdown:
    """그때까지의 최고 종가 대비 가장 크게 떨어진 비율"""
    peak = bars[0]
    worst = Drawdown(0.0, bars[0].date, bars[0].date)
    for bar in bars:
        if bar.close > peak.close:
            peak = bar
        percent = change(peak.close, bar.close)
        if percent is not None and percent < worst.percent:
            worst = Drawdown(percent, peak.date, bar.date)
    return worst


def _volatility(bars: list[Bar]) -> float:
    """일간 수익률 표본표준편차 × √252, %"""
    daily = [b.close / a.close - 1 for a, b in zip(bars, bars[1:])]
    return stdev(daily) * sqrt(YEAR) * 100
