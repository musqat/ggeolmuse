"""배당 요약(주기는 배당락일 간격으로, 금액 기준이 바뀐 것 같으면 그 값은 뺌)"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from app.core.models import Dividend

# 배당락일 간격 중앙값의 위 경계(일), 1년 횟수, 이름
FREQUENCIES = ((45, 12, "월"), (135, 4, "분기"), (270, 2, "반기"), (450, 1, "연"))
YEAR = timedelta(days=365)


@dataclass(frozen=True)
class DividendSnapshot:
    recent: tuple[Dividend, ...]  # 최신 먼저, 최대 4건
    frequency: str | None  # 월 · 분기 · 반기 · 연
    year_total: float | None  # 최근 1년 치 합계
    yield_percent: float | None  # 1년 치 합계 ÷ 현재가 × 100
    previous_total: float | None  # 그 앞 1년 치 합계
    change_percent: float | None
    stopped: bool  # 마지막 배당락일이 1년보다 오래됨
    base_changed: bool  # 이웃한 두 건 금액이 절반 아래 · 두 배 위
    base_changed_in_year: bool  # 그 두 건이 1년 치 안에 있음


def summarize_dividends(dividends: list[Dividend], price: float, today: date) -> DividendSnapshot:
    """1년 치는 주기를 알면 최근 1년 횟수만큼, 모르면 최근 365일 안의 배당"""
    divs = sorted(dividends, key=lambda d: d.ex_date)
    if not divs:
        return DividendSnapshot((), None, None, None, None, None, False, False, False)
    per_year, frequency = _frequency(divs)
    jumps = [i for i in range(1, len(divs)) if _jump(divs[i - 1].amount, divs[i].amount)]
    stopped = divs[-1].ex_date <= today - YEAR
    if per_year:
        year_start = len(divs) - per_year if len(divs) >= per_year else None
        previous = divs[-2 * per_year:-per_year] if len(divs) >= 2 * per_year else []
    else:
        recent = [d for d in divs if d.ex_date > today - YEAR]
        year_start = len(divs) - len(recent) if recent else None
        previous = []
    base_changed_in_year = year_start is not None and any(i > year_start for i in jumps)
    year_total = None
    if year_start is not None and not stopped and not base_changed_in_year:
        year_total = sum(d.amount for d in divs[year_start:])
    previous_total = sum(d.amount for d in previous) if previous and not jumps else None
    change_percent = None
    if year_total is not None and previous_total:
        change_percent = (year_total / previous_total - 1) * 100
    return DividendSnapshot(
        recent=tuple(reversed(divs[-4:])),
        frequency=frequency,
        year_total=year_total,
        yield_percent=year_total / price * 100 if year_total is not None and price > 0 else None,
        previous_total=previous_total if change_percent is not None else None,
        change_percent=change_percent,
        stopped=stopped,
        base_changed=bool(jumps),
        base_changed_in_year=base_changed_in_year,
    )


def _frequency(divs: list[Dividend]) -> tuple[int | None, str | None]:
    """최근 배당락일 간격(최대 8개)의 중앙값으로 정한 1년 횟수"""
    tail = divs[-9:]
    gaps = sorted((b.ex_date - a.ex_date).days for a, b in zip(tail, tail[1:]))
    if not gaps:
        return None, None
    median = gaps[len(gaps) // 2]
    for limit, per_year, label in FREQUENCIES:
        if median <= limit:
            return per_year, label
    return None, None


def _jump(before: float, after: float) -> bool:
    if before <= 0 or after <= 0:
        return False
    ratio = after / before
    return ratio < 0.5 or ratio > 2
