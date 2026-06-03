from __future__ import annotations

import pandas as pd


def _to_dataframe(ohlc: list[dict]) -> pd.DataFrame:
    df = pd.DataFrame(ohlc)
    df["close"] = df["adjustedClose"].fillna(df["closePrice"]).astype(float)
    df["high"] = df["highPrice"].astype(float)
    df["low"] = df["lowPrice"].astype(float)
    df["volume"] = df["volume"].astype(float)
    return df


def _rsi(close: pd.Series, length: int = 14) -> pd.Series:
    """Wilder RSI. 손실 0이면 RSI=100, 이득 0이면 RSI=0."""
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    avg_gain = gain.ewm(alpha=1 / length, min_periods=length, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1 / length, min_periods=length, adjust=False).mean()
    rsi = 100 - (100 / (1 + avg_gain / avg_loss))
    # avg_loss==0 (손실 없음) → RSI=100, avg_gain==0 (이득 없음) → RSI=0
    rsi = rsi.where(avg_loss != 0, 100.0)
    rsi = rsi.where(~((avg_loss == 0) & (avg_gain == 0)), float("nan"))
    return rsi


def _macd_hist(close: pd.Series) -> pd.Series:
    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    macd_line = ema12 - ema26
    signal = macd_line.ewm(span=9, adjust=False).mean()
    return macd_line - signal


def _bollinger(close: pd.Series, length: int = 20, mult: float = 2.0):
    ma = close.rolling(length).mean()
    std = close.rolling(length).std()
    return ma + mult * std, ma - mult * std


def _stoch_k(high: pd.Series, low: pd.Series, close: pd.Series, length: int = 14):
    lowest = low.rolling(length).min()
    highest = high.rolling(length).max()
    rng = (highest - lowest).replace(0, float("nan"))
    return (close - lowest) / rng * 100


def build_indicator_summary(ohlc: list[dict]) -> str | None:
    """OHLC 리스트 → LLM 주입용 압축 지표 요약. 데이터 부족 시 None."""
    if not ohlc or len(ohlc) < 35:
        return None

    df = _to_dataframe(ohlc)
    close = df["close"]
    last = float(close.iloc[-1])

    parts: list[str] = []

    # RSI(14)
    rsi = _rsi(close, 14).dropna()
    if not rsi.empty:
        r = float(rsi.iloc[-1])
        zone = "과매수" if r >= 70 else "과매도" if r <= 30 else "중립"
        parts.append(f"RSI {r:.0f}({zone})")

    # 이동평균 MA20 vs MA60 + 골든/데드크로스
    ma20 = close.rolling(20).mean()
    ma60 = close.rolling(60).mean() if len(close) >= 60 else None
    if ma60 is not None and not ma60.dropna().empty:
        diff = ma20 - ma60
        rel = "MA20>MA60" if diff.iloc[-1] > 0 else "MA20<MA60"
        cross = _detect_cross(diff)
        parts.append(rel + (f" {cross}" if cross else ""))
    elif not ma20.dropna().empty:
        rel = "종가>MA20" if last > ma20.iloc[-1] else "종가<MA20"
        parts.append(rel)

    # MACD 히스토그램
    hist = _macd_hist(close).dropna()
    if not hist.empty:
        h = float(hist.iloc[-1])
        parts.append("MACD 양전환" if h > 0 else "MACD 음전환")

    # 볼린저밴드 위치
    upper, lower = _bollinger(close, 20)
    if not upper.dropna().empty and not lower.dropna().empty:
        u = float(upper.dropna().iloc[-1])
        lo = float(lower.dropna().iloc[-1])
        if last >= u * 0.98:
            parts.append("BB 상단 근접")
        elif last <= lo * 1.02:
            parts.append("BB 하단 근접")
        else:
            parts.append("BB 중앙권")

    # 스토캐스틱 %K
    stoch = _stoch_k(df["high"], df["low"], close).dropna()
    if not stoch.empty:
        k = float(stoch.iloc[-1])
        parts.append(f"Stoch%K {k:.0f}")

    # 거래량 추세 (최근 5일 평균 vs 20일 평균)
    vol = df["volume"]
    if len(vol) >= 20:
        v5 = vol.tail(5).mean()
        v20 = vol.tail(20).mean()
        if v20 > 0:
            pct = (v5 / v20 - 1) * 100
            parts.append(f"거래량 평균대비 {pct:+.0f}%")

    # 기간 등락률 (20일)
    if len(close) >= 21:
        ago = float(close.iloc[-21])
        if ago > 0:
            chg = (last / ago - 1) * 100
            parts.append(f"20일 {chg:+.1f}%")

    # 지지/저항 (최근 60일 고저)
    window = close.tail(60)
    parts.append(f"최근저점 {window.min():.2f}/고점 {window.max():.2f}")

    return ", ".join(parts)


def _detect_cross(diff: pd.Series, lookback: int = 10) -> str | None:
    """MA 차이 시계열에서 최근 lookback일 내 교차 감지."""
    d = diff.dropna()
    if len(d) < 2:
        return None
    recent = d.tail(lookback).reset_index(drop=True)
    for i in range(len(recent) - 1, 0, -1):
        prev, cur = recent[i - 1], recent[i]
        if prev <= 0 < cur:
            days = len(recent) - 1 - i
            return f"골든크로스 {days}일전"
        if prev >= 0 > cur:
            days = len(recent) - 1 - i
            return f"데드크로스 {days}일전"
    return None
