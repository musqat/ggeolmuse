"""기술 지표 계산(식은 프론트 차트 klinecharts 9.8.12 와 같음)"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from math import sqrt

from app.core.models import Bar

MA_PERIODS = (5, 20, 60, 120, 200)
EMA_PERIODS = (12, 26)
VOLUME_MA_PERIODS = (5, 10, 20)


@dataclass(frozen=True)
class Bollinger:
    upper: float
    middle: float
    lower: float
    percent_b: float | None


@dataclass(frozen=True)
class Macd:
    dif: float
    dea: float
    bar: float


@dataclass(frozen=True)
class Kdj:
    k: float
    d: float
    j: float


@dataclass(frozen=True)
class Cross:
    kind: str  # golden · dead
    days_ago: int


@dataclass(frozen=True)
class IndicatorSnapshot:
    as_of: date
    bars: int
    close: float
    ma: dict[int, float]
    ema: dict[int, float]
    bollinger: Bollinger | None
    rsi: float | None
    macd: Macd | None
    kdj: Kdj | None
    volume: float
    volume_ma: dict[int, float]
    ma_cross: Cross | None


def compute_indicators(bars: list[Bar]) -> IndicatorSnapshot | None:
    """마지막 캔들 기준 지표(가장 짧은 이동평균도 못 구하면 None)"""
    if len(bars) < MA_PERIODS[0]:
        return None
    closes = [b.close for b in bars]
    volumes = [b.volume for b in bars]
    return IndicatorSnapshot(
        as_of=bars[-1].date,
        bars=len(bars),
        close=closes[-1],
        ma={p: sma(closes, p)[-1] for p in MA_PERIODS if len(closes) >= p},
        ema={p: ema(closes, p)[-1] for p in EMA_PERIODS if len(closes) >= p},
        bollinger=bollinger(closes),
        rsi=rsi(closes),
        macd=macd(closes),
        kdj=kdj([b.high for b in bars], [b.low for b in bars], closes),
        volume=volumes[-1],
        volume_ma={p: sma(volumes, p)[-1] for p in VOLUME_MA_PERIODS if len(volumes) >= p},
        ma_cross=ma_cross(closes),
    )


def sma(values: list[float], period: int) -> list[float | None]:
    """단순 이동평균(앞 period - 1 개는 None)"""
    out: list[float | None] = []
    total = 0.0
    for i, value in enumerate(values):
        total += value
        if i >= period - 1:
            out.append(total / period)
            total -= values[i - (period - 1)]
        else:
            out.append(None)
    return out


def ema(values: list[float], period: int) -> list[float | None]:
    """첫 값은 period 개 단순 평균, 그 뒤 (2 × 값 + (period − 1) × 직전) / (period + 1)"""
    out: list[float | None] = []
    total = 0.0
    prev = 0.0
    for i, value in enumerate(values):
        total += value
        if i < period - 1:
            out.append(None)
            continue
        prev = total / period if i == period - 1 else (2 * value + (period - 1) * prev) / (period + 1)
        out.append(prev)
    return out


def bollinger(closes: list[float], period: int = 20, width: float = 2.0) -> Bollinger | None:
    """중간은 단순 평균, 폭은 모표준편차(÷ period)"""
    if len(closes) < period:
        return None
    middle = sma(closes, period)[-1]
    deviation = sqrt(sum((c - middle) ** 2 for c in closes[-period:]) / period)
    upper = middle + width * deviation
    lower = middle - width * deviation
    percent_b = (closes[-1] - lower) / (upper - lower) if upper > lower else None
    return Bollinger(upper=upper, middle=middle, lower=lower, percent_b=percent_b)


def rsi(closes: list[float], period: int = 14) -> float | None:
    """최근 period 개 상승폭 합과 하락폭 합의 비율(하락이 없으면 100, 차트는 0)"""
    if len(closes) < period:
        return None
    up = down = 0.0
    value = 0.0
    for i, close in enumerate(closes):
        diff = close - (closes[i - 1] if i > 0 else close)
        if diff > 0:
            up += diff
        else:
            down += abs(diff)
        if i >= period - 1:
            value = 100.0 if down == 0 else 100 - 100 / (1 + up / down)
            ago = closes[i - (period - 1)]
            ago_diff = ago - (closes[i - period] if i - period >= 0 else ago)
            if ago_diff > 0:
                up -= ago_diff
            else:
                down -= abs(ago_diff)
    return value


def macd(closes: list[float], short: int = 12, long: int = 26, signal: int = 9) -> Macd | None:
    """EMA 는 단순 평균으로 시작, DEA 첫 값은 DIF signal 개 평균, 막대는 2 × (DIF − DEA)"""
    longest = max(short, long)
    if len(closes) < longest + signal - 1:
        return None
    total = 0.0
    ema_short = ema_long = dif = dea = 0.0
    dif_sum = 0.0
    for i, close in enumerate(closes):
        total += close
        if i >= short - 1:
            ema_short = total / short if i == short - 1 else (2 * close + (short - 1) * ema_short) / (short + 1)
        if i >= long - 1:
            ema_long = total / long if i == long - 1 else (2 * close + (long - 1) * ema_long) / (long + 1)
        if i >= longest - 1:
            dif = ema_short - ema_long
            dif_sum += dif
            if i == longest + signal - 2:
                dea = dif_sum / signal
            elif i > longest + signal - 2:
                dea = (dif * 2 + dea * (signal - 1)) / (signal + 1)
    return Macd(dif=dif, dea=dea, bar=(dif - dea) * 2)


def kdj(highs: list[float], lows: list[float], closes: list[float],
        n: int = 9, m1: int = 3, m2: int = 3) -> Kdj | None:
    """RSV 를 K = (2 × 직전 K + RSV) / 3, D = (2 × 직전 D + K) / 3 로 평활(처음 K · D 는 50)"""
    if len(closes) < n:
        return None
    k = d = 50.0
    for i in range(n - 1, len(closes)):
        high = max(highs[i - n + 1:i + 1])
        low = min(lows[i - n + 1:i + 1])
        span = high - low
        rsv = (closes[i] - low) / (span if span != 0 else 1) * 100
        k = ((m1 - 1) * k + rsv) / m1
        d = ((m2 - 1) * d + k) / m2
    return Kdj(k=k, d=d, j=3.0 * k - 2.0 * d)


def ma_cross(closes: list[float], short: int = 20, long: int = 60, lookback: int = 10) -> Cross | None:
    """최근 lookback 일 안의 단기 · 장기 이동평균 교차(가장 최근 것 하나)"""
    diffs = [s - l for s, l in zip(sma(closes, short), sma(closes, long))
             if s is not None and l is not None]
    recent = diffs[-lookback:]
    for i in range(len(recent) - 1, 0, -1):
        prev, cur = recent[i - 1], recent[i]
        if prev <= 0 < cur:
            return Cross(kind="golden", days_ago=len(recent) - 1 - i)
        if prev >= 0 > cur:
            return Cross(kind="dead", days_ago=len(recent) - 1 - i)
    return None
