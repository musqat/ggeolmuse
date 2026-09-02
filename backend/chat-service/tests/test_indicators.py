from app.indicators import build_indicator_summary


def _series(closes, volumes=None):
    """종가 리스트로 OHLC dict 리스트 생성 (테스트용)."""
    volumes = volumes or [1_000_000] * len(closes)
    out = []
    for i, c in enumerate(closes):
        out.append({
            "date": f"2026-01-{i + 1:02d}",
            "openPrice": c,
            "highPrice": c + 1,
            "lowPrice": c - 1,
            "closePrice": c,
            "adjustedClose": c,
            "volume": volumes[i],
        })
    return out


def test_returns_none_when_too_few_points():
    assert build_indicator_summary(_series([100, 101])) is None


def test_summary_contains_core_indicators():
    # 꾸준히 상승하는 60일 시계열
    closes = [100 + i for i in range(60)]
    summary = build_indicator_summary(_series(closes))
    assert summary is not None
    # 핵심 지표 키워드가 요약에 포함되어야 함
    assert "RSI" in summary
    assert "MA" in summary
    assert "MACD" in summary
    assert "거래량" in summary


def test_uptrend_detected_as_positive_change():
    closes = [100 + i for i in range(60)]
    summary = build_indicator_summary(_series(closes))
    # 60일 우상향이면 기간 등락률이 양수로 표기
    assert "+" in summary
