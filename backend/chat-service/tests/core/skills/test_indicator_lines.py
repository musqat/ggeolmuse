from app.core.analysis.indicators import compute_indicators
from app.core.skills.indicators import indicator_lines
from tests.core.fakes import make_bars


def test_lines_name_chart_indicators():
    lines = indicator_lines(compute_indicators(make_bars(300)))

    text = "\n".join(lines)
    assert lines[0] == "기준일 2025-10-27 종가 109.43 (최근 300거래일)"
    for name in ("MA5", "MA200", "EMA12", "볼린저(20, 2)", "RSI(14): 44.2 (중립)", "MACD(12, 26, 9)",
                 "스토캐스틱(KDJ 9, 3, 3)", "거래량"):
        assert name in text
    assert "계산 못 한 값" not in text


def test_lines_name_missing_values():
    lines = indicator_lines(compute_indicators(make_bars(30)))
    assert lines[-1] == "계산 못 한 값: MA60, MA120, MA200, MACD (데이터 30거래일)"
