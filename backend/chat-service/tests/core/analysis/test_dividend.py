from datetime import date

import pytest

from app.core.analysis.dividend import summarize_dividends
from app.core.models import Dividend

TODAY = date(2026, 10, 16)


def _divs(*rows):
    return [Dividend(ex_date=date.fromisoformat(d), amount=a, currency="USD") for d, a in rows]


QUARTERLY = _divs(
    ("2023-11-10", 0.24), ("2024-02-09", 0.24), ("2024-05-10", 0.24), ("2024-08-12", 0.24),
    ("2024-11-08", 0.25), ("2025-02-07", 0.25), ("2025-05-12", 0.25), ("2025-08-11", 0.25),
    ("2025-11-10", 0.26), ("2026-02-09", 0.26), ("2026-05-11", 0.26), ("2026-08-10", 0.26),
)


def test_quarterly_year_total_yield_and_change():
    s = summarize_dividends(QUARTERLY, price=104.0, today=TODAY)

    assert s.frequency == "분기"
    assert s.year_total == pytest.approx(1.04)
    assert s.yield_percent == pytest.approx(1.0)
    assert s.previous_total == pytest.approx(1.00)
    assert s.change_percent == pytest.approx(4.0)
    assert [d.ex_date.isoformat() for d in s.recent] == ["2026-08-10", "2026-05-11", "2026-02-09", "2025-11-10"]


def test_year_total_counts_payments_not_days():
    # 365일 안에 분기 배당락일이 다섯 번 들어와도 1년 치는 네 번
    divs = _divs(("2025-07-18", 0.25), ("2025-10-17", 0.25), ("2026-01-16", 0.25),
                 ("2026-04-17", 0.25), ("2026-07-17", 0.25), ("2026-10-16", 0.25))
    assert summarize_dividends(divs, price=100.0, today=TODAY).year_total == pytest.approx(1.0)


def test_split_inside_year_hides_total_and_change():
    divs = _divs(("2025-11-10", 0.88), ("2026-02-09", 0.88), ("2026-05-11", 0.22), ("2026-08-10", 0.22),
                 ("2025-08-11", 0.88))
    s = summarize_dividends(divs, price=50.0, today=TODAY)

    assert s.base_changed and s.base_changed_in_year
    assert s.year_total is None and s.yield_percent is None and s.change_percent is None


def test_split_before_year_keeps_total_but_hides_change():
    divs = _divs(("2024-11-08", 0.88), ("2025-02-07", 0.88), ("2025-05-12", 0.88), ("2025-08-11", 0.88),
                 ("2025-11-10", 0.22), ("2026-02-09", 0.22), ("2026-05-11", 0.22), ("2026-08-10", 0.22))
    s = summarize_dividends(divs, price=50.0, today=TODAY)

    assert s.base_changed and not s.base_changed_in_year
    assert s.year_total == pytest.approx(0.88)
    assert s.change_percent is None


def test_stopped_paying_has_no_year_total():
    divs = _divs(("2024-08-12", 0.25), ("2024-11-08", 0.25), ("2025-02-07", 0.25), ("2025-05-12", 0.25))
    s = summarize_dividends(divs, price=100.0, today=TODAY)
    assert s.stopped and s.year_total is None


def test_monthly_payer():
    divs = _divs(*[(f"{2025 + (m // 12)}-{m % 12 + 1:02d}-15", 0.1) for m in range(9, 21)])
    s = summarize_dividends(divs, price=60.0, today=TODAY)
    assert s.frequency == "월"
    assert s.year_total == pytest.approx(1.2)


def test_single_dividend_uses_last_365_days():
    s = summarize_dividends(_divs(("2026-03-02", 0.5)), price=100.0, today=TODAY)
    assert s.frequency is None
    assert s.year_total == pytest.approx(0.5)
    assert s.change_percent is None


def test_no_dividends():
    s = summarize_dividends([], price=100.0, today=TODAY)
    assert s.recent == () and s.year_total is None
