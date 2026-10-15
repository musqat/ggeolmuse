"""core 테스트용 고정 캔들"""
from __future__ import annotations

from datetime import date, timedelta

from app.core.models import Bar

TODAY = date(2026, 10, 16)


def make_bars(n: int = 300, start: date = date(2025, 1, 1)) -> list[Bar]:
    """정수(센트)로 만든 고정 캔들. 차트 기준값을 뽑은 node 스크립트와 같은 식"""
    seed = 20261016
    price = 10000

    def next_seed() -> int:
        nonlocal seed
        seed = (seed * 48271) % 2147483647
        return seed

    bars = []
    for i in range(n):
        step = next_seed() % 401 - 200
        open_ = price
        close = max(1000, price + step)
        high = max(open_, close) + next_seed() % 151
        low = min(open_, close) - next_seed() % 151
        volume = 1_000_000 + next_seed() % 500_000
        bars.append(Bar(date=start + timedelta(days=i), open=open_ / 100, high=high / 100, low=low / 100,
                        close=close / 100, adj_close=close / 100, volume=float(volume)))
        price = close
    return bars


def line_bars(closes: list[float], start: date = date(2025, 1, 1)) -> list[Bar]:
    """종가만 정한 캔들. 고가 · 저가는 종가 ± 1, 배당 조정 종가는 종가와 같음"""
    return [Bar(date=start + timedelta(days=i), open=c, high=c + 1, low=c - 1, close=c, adj_close=c,
                volume=1_000_000.0) for i, c in enumerate(closes)]
