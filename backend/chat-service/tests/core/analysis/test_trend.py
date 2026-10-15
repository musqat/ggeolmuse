from dataclasses import replace
from datetime import date

import pytest

from app.core.analysis.trend import compute_trend
from tests.core.fakes import line_bars


def test_price_and_total_returns_are_separate():
    # 마지막 날만 110, 그 전은 100. 배당 조정 종가는 그 전이 90
    bars = [replace(b, adj_close=90.0) for b in line_bars([100.0] * 253)] + line_bars([110.0])
    s = compute_trend(bars)

    assert [r.label for r in s.returns] == ["1주", "1달", "3달", "6달", "1년"]
    assert all(r.price == pytest.approx(10.0) for r in s.returns)
    assert all(r.total == pytest.approx((110 / 90 - 1) * 100) for r in s.returns)


def test_year_range_uses_last_252_bars():
    bars = line_bars([500.0] + [100.0] * 251 + [110.0])  # 맨 앞 500 은 252개 밖
    s = compute_trend(bars)
    assert (s.year_range.high, s.year_range.low) == (111.0, 99.0)


def test_drawdown_from_running_peak():
    closes = [100.0] * 100 + [120.0] + [110.0] * 99 + [90.0] + [95.0] * 51
    bars = line_bars(closes, start=date(2025, 1, 1))
    s = compute_trend(bars)

    assert s.drawdown.percent == pytest.approx(-25.0)
    assert s.drawdown.peak_date == bars[100].date
    assert s.drawdown.trough_date == bars[200].date


def test_flat_prices_have_zero_volatility():
    s = compute_trend(line_bars([100.0] * 253))
    assert s.volatility == pytest.approx(0.0)


def test_volatility_needs_252_daily_returns():
    assert compute_trend(line_bars([100.0] * 252)).volatility is None
    assert compute_trend(line_bars([100.0] * 253)).volatility is not None


def test_short_history_keeps_what_it_can():
    s = compute_trend(line_bars([100.0] * 10))

    assert [r.label for r in s.returns] == ["1주"]
    assert s.year_range is None and s.drawdown is None and s.volatility is None


def test_too_few_bars_returns_none():
    assert compute_trend(line_bars([100.0] * 5)) is None
