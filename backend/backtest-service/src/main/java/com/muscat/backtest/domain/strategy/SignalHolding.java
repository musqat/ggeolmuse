package com.muscat.backtest.domain.strategy;

import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 신호로 정한 보유 여부: 매매 종목 일봉 날짜까지 아직 안 본 신호 일봉을 차례로 보고 매수 신호면 들고 매도 신호면 비움(시작일 전 신호는 보지 않음)
public final class SignalHolding {

  private final TradeSignal signal;
  private final List<OHLCPriceDto> candles;
  private final LocalDate start;
  // 본 신호 일봉 수 · 신호로 정한 보유 여부
  private int seen;
  private boolean wanted;
  // 마지막으로 본 신호가 매수인지 · 신호 밖 규칙으로 다 판 뒤 매수가 아닌 신호를 기다리는지
  private boolean lastBuy;
  private boolean waiting;

  SignalHolding(TradeSignal signal, SignalPrices prices, LocalDate start) {
    this.signal = signal;
    this.candles = prices.candles();
    this.start = start;
  }

  // 일봉(시작일 전 신호 기간만큼 더 받은 것)의 수정 종가 이동평균 교차
  public static SignalHolding maCross(Map<LocalDate, OHLCPriceDto> priceMap, int shortWindow, int longWindow,
    LocalDate start) {
    SignalPrices prices = SignalPrices.of(priceMap);
    return new SignalHolding(new MaCrossSignal(prices, shortWindow, longWindow), prices, start);
  }

  // 일봉(시작일 전 신호 기간만큼 더 받은 것)의 수정 종가 이동평균 밴드
  public static SignalHolding maBand(Map<LocalDate, OHLCPriceDto> priceMap, int window, BigDecimal upperRate,
    BigDecimal lowerRate, LocalDate start) {
    SignalPrices prices = SignalPrices.of(priceMap);
    return new SignalHolding(new MaBandSignal(prices, window, upperRate, lowerRate), prices, start);
  }

  // 신호 기간 거래일이 넉넉히 들어가는 달력 날수
  public static int lookbackDays(int window) {
    return window * 3 / 2 + 14;
  }

  // 그 날짜까지 신호를 보고 들고 있을지
  public boolean wanted(LocalDate date) {
    for (; seen < candles.size() && !candles.get(seen).date().isAfter(date); seen++) {
      TradeSignal.Action action = candles.get(seen).date().isBefore(start) ? null : signal.at(seen);
      lastBuy = action == TradeSignal.Action.BUY;
      if (lastBuy) {
        wanted = wanted || !waiting;
      } else {
        waiting = false;
        wanted = wanted && action != TradeSignal.Action.SELL;
      }
    }
    return wanted;
  }

  // 신호 밖 규칙으로 다 판 뒤: 다음 매수 신호부터, 마지막 신호가 매수였으면 매수가 아닌 신호를 한 번 본 뒤부터 다시 삼
  public void soldOut() {
    wanted = false;
    waiting = lastBuy;
  }

  public String buyTrigger() {
    return signal.buyTrigger();
  }

  public String sellTrigger() {
    return signal.sellTrigger();
  }
}
