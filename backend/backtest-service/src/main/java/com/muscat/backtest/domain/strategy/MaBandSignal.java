package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.domain.dto.response.SignalChart;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 이동평균 밴드 신호: 수정 종가가 평균 × (1 + 위 비율) 보다 높은 날 사고 평균 × (1 - 아래 비율) 보다 낮은 날 팜(사이는 그대로)
record MaBandSignal(SignalPrices prices, int window, BigDecimal upperRate, BigDecimal lowerRate) implements TradeSignal {

  // 신호 값 × 기간과 합 × (1 ± 비율)을 견줌(나눗셈 없이)
  @Override
  public Action at(int i) {
    if (!prices.filled(i, window)) {
      return null;
    }
    BigDecimal scaled = prices.value(i).multiply(BigDecimal.valueOf(window));
    BigDecimal sum = prices.sum(i, window);
    if (scaled.compareTo(sum.multiply(upperFactor())) > 0) {
      return Action.BUY;
    }
    return scaled.compareTo(sum.multiply(lowerFactor())) < 0 ? Action.SELL : null;
  }

  // 시작일부터 일봉마다 수정 종가 · 평균 · 위 선 · 아래 선(교차 없음)
  @Override
  public SignalChart chart(String symbol, LocalDate start) {
    List<SignalChart.Point> points = new ArrayList<>();
    for (int i = 0; i < prices.candles().size(); i++) {
      LocalDate date = prices.candles().get(i).date();
      if (date.isBefore(start)) {
        continue;
      }
      points.add(SignalChart.Point.band(date, MoneyUtils.roundUsd(prices.value(i)),
        prices.average(i, window, BigDecimal.ONE), prices.average(i, window, upperFactor()),
        prices.average(i, window, lowerFactor())));
    }
    return new SignalChart(symbol, points, List.of());
  }

  @Override
  public String buyTrigger() {
    return TransactionTrigger.BAND_BUY;
  }

  @Override
  public String sellTrigger() {
    return TransactionTrigger.BAND_SELL;
  }

  private BigDecimal upperFactor() {
    return BigDecimal.ONE.add(upperRate);
  }

  private BigDecimal lowerFactor() {
    return BigDecimal.ONE.subtract(lowerRate);
  }
}
