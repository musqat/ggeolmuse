from dataclasses import replace

import pytest

from app.core.analysis.indicators import compute_indicators, ma_cross, rsi
from tests.core.fakes import line_bars, make_bars

# 기준값: klinecharts 9.8.12 계산 함수를 node 로 같은 캔들(make_bars 300개)에 돌린 마지막 값
CHART = {
    "ma": {5: 111.66800000000008, 20: 110.28949999999993, 60: 115.62966666666662,
           120: 115.57025, 200: 112.57964999999992},
    "ema": {12: 110.92206839451298, 26: 111.71765300585383},
    "boll": (113.70094822619362, 110.28949999999993, 106.87805177380625),
    "rsi": 44.15430267062316,
    "macd": (-0.7955846113408569, -1.2288517168218485, 0.8665342109619831),
    "kdj": (58.17572790481399, 64.50399892461085, 45.519185865220265),
    "volume_ma": {5: 1304976.6, 10: 1299707.0, 20: 1262740.4},
}


def test_matches_chart_values():
    s = compute_indicators(make_bars(300))

    assert s.close == 109.43
    assert s.bars == 300
    assert s.ma == pytest.approx(CHART["ma"], abs=1e-9)
    assert s.ema == pytest.approx(CHART["ema"], abs=1e-9)
    assert (s.bollinger.upper, s.bollinger.middle, s.bollinger.lower) == pytest.approx(CHART["boll"], abs=1e-9)
    assert s.rsi == pytest.approx(CHART["rsi"], abs=1e-9)
    assert (s.macd.dif, s.macd.dea, s.macd.bar) == pytest.approx(CHART["macd"], abs=1e-9)
    assert (s.kdj.k, s.kdj.d, s.kdj.j) == pytest.approx(CHART["kdj"], abs=1e-9)
    assert s.volume_ma == pytest.approx(CHART["volume_ma"], abs=1e-6)


def test_short_history_skips_what_it_cannot_compute():
    s = compute_indicators(make_bars(30))

    assert set(s.ma) == {5, 20}
    assert set(s.ema) == {12, 26}
    assert s.macd is None  # DIF 26개 + DEA 9개
    assert s.ma_cross is None
    assert s.bollinger is not None and s.rsi is not None and s.kdj is not None


def test_too_few_bars_returns_none():
    assert compute_indicators(make_bars(4)) is None


def test_rsi_is_100_without_decline():
    # 차트(klinecharts)는 이 경우 0 을 그린다
    assert rsi([float(c) for c in range(100, 120)]) == 100.0


def test_rsi_is_0_without_rise():
    assert rsi([float(c) for c in range(120, 100, -1)]) == 0.0


def test_percent_b_is_position_in_band():
    s = compute_indicators(make_bars(300))
    b = s.bollinger
    assert b.percent_b == pytest.approx((s.close - b.lower) / (b.upper - b.lower))


def test_golden_cross_counts_days_since():
    closes = [100.0] * 60 + [101.0, 102.0, 103.0]
    cross = ma_cross(closes)
    assert (cross.kind, cross.days_ago) == ("golden", 2)


def test_dead_cross_counts_days_since():
    closes = [100.0] * 60 + [99.0, 98.0]
    cross = ma_cross(closes)
    assert (cross.kind, cross.days_ago) == ("dead", 1)


def test_uses_close_price_not_dividend_adjusted():
    bars = [replace(b, adj_close=90.0) for b in line_bars([100.0] * 30)]
    assert compute_indicators(bars).ma[20] == 100.0
