"""지표 스킬. 차트 "지표 설정" 목록과 같은 지표를 자료로 만든다"""
from __future__ import annotations

from app.core.analysis.indicators import EMA_PERIODS, MA_PERIODS, IndicatorSnapshot


def indicator_lines(s: IndicatorSnapshot) -> list[str]:
    """지표 스냅숏 → 자료 줄"""
    lines = [f"기준일 {s.as_of.isoformat()} 종가 {s.close:.2f} (최근 {s.bars}거래일)"]
    if s.ma:
        parts = [f"MA{p} {v:.2f}(종가 {_gap(s.close, v)})" for p, v in s.ma.items()]
        lines.append("이동평균: " + ", ".join(parts))
    if s.ma_cross:
        kind = "골든크로스" if s.ma_cross.kind == "golden" else "데드크로스"
        lines.append(f"MA20 · MA60: {s.ma_cross.days_ago}일 전 {kind}")
    if s.ema:
        lines.append("EMA: " + ", ".join(f"EMA{p} {v:.2f}" for p, v in s.ema.items()))
    if s.bollinger:
        b = s.bollinger
        percent_b = f", %B {b.percent_b:.2f}" if b.percent_b is not None else ""
        lines.append(f"볼린저(20, 2): 상단 {b.upper:.2f}, 중간 {b.middle:.2f}, 하단 {b.lower:.2f}{percent_b}")
    if s.rsi is not None:
        zone = "과매수" if s.rsi >= 70 else "과매도" if s.rsi <= 30 else "중립"
        lines.append(f"RSI(14): {s.rsi:.1f} ({zone})")
    if s.macd:
        m = s.macd
        lines.append(f"MACD(12, 26, 9): DIF {m.dif:.3f}, DEA {m.dea:.3f}, 막대 {m.bar:.3f}")
    if s.kdj:
        lines.append(f"스토캐스틱(KDJ 9, 3, 3): K {s.kdj.k:.1f}, D {s.kdj.d:.1f}, J {s.kdj.j:.1f}")
    volume = [f"마지막 {s.volume:,.0f}"]
    if s.volume_ma.get(20):
        volume.append(f"20일 평균의 {s.volume / s.volume_ma[20]:.2f}배")
    volume += [f"MA{p} {v:,.0f}" for p, v in s.volume_ma.items()]
    lines.append("거래량: " + ", ".join(volume))
    missing = _missing(s)
    if missing:
        lines.append(f"계산 못 한 값: {', '.join(missing)} (데이터 {s.bars}거래일)")
    return lines


def _gap(close: float, average: float) -> str:
    return f"{(close / average - 1) * 100:+.1f}%"


def _missing(s: IndicatorSnapshot) -> list[str]:
    missing = [f"MA{p}" for p in MA_PERIODS if p not in s.ma]
    missing += [f"EMA{p}" for p in EMA_PERIODS if p not in s.ema]
    optional = (("볼린저", s.bollinger), ("RSI", s.rsi), ("MACD", s.macd), ("스토캐스틱", s.kdj))
    return missing + [name for name, value in optional if value is None]
