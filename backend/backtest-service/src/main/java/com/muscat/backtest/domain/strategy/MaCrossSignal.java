package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.domain.dto.response.SignalChart;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 이동평균 교차 신호: 짧은 선이 긴 선을 아래에서 위로 넘은 날(골든 크로스) 사고 위에서 아래로 넘은 날(데드 크로스) 팜
record MaCrossSignal(SignalPrices prices, int shortWindow, int longWindow) implements TradeSignal {

  @Override
  public Action at(int i) {
    if (i < longWindow) {
      return null;
    }
    SignalChart.CrossType cross = cross(i);
    if (cross == SignalChart.CrossType.GOLDEN) {
      return Action.BUY;
    }
    return cross == SignalChart.CrossType.DEAD ? Action.SELL : null;
  }

  // i 번째 일봉의 교차(앞 일봉 짧은 선 - 긴 선 ≤ 0 · 그 날 > 0 이면 골든, 앞 ≥ 0 · 그 날 < 0 이면 데드, i 는 긴 기간 이상)
  private SignalChart.CrossType cross(int i) {
    int before = side(i - 1);
    int today = side(i);
    if (before <= 0 && today > 0) {
      return SignalChart.CrossType.GOLDEN;
    }
    return before >= 0 && today < 0 ? SignalChart.CrossType.DEAD : null;
  }

  // i 번째 일봉에서 짧은 선 - 긴 선의 부호(두 합에 서로의 기간을 곱해 나눗셈 없이 견줌)
  private int side(int i) {
    return prices.sum(i, shortWindow).multiply(BigDecimal.valueOf(longWindow))
      .compareTo(prices.sum(i, longWindow).multiply(BigDecimal.valueOf(shortWindow)));
  }

  // 시작일부터 일봉마다 수정 종가 · 두 평균과 교차한 날
  @Override
  public SignalChart chart(String symbol, LocalDate start) {
    List<SignalChart.Point> points = new ArrayList<>();
    List<SignalChart.Cross> crosses = new ArrayList<>();
    for (int i = 0; i < prices.candles().size(); i++) {
      LocalDate date = prices.candles().get(i).date();
      if (date.isBefore(start)) {
        continue;
      }
      points.add(SignalChart.Point.averages(date, MoneyUtils.roundUsd(prices.value(i)),
        prices.average(i, shortWindow, BigDecimal.ONE), prices.average(i, longWindow, BigDecimal.ONE)));
      SignalChart.CrossType type = i >= longWindow ? cross(i) : null;
      if (type != null) {
        crosses.add(new SignalChart.Cross(date, type));
      }
    }
    return new SignalChart(symbol, points, crosses);
  }

  @Override
  public String buyTrigger() {
    return TransactionTrigger.GOLDEN_CROSS;
  }

  @Override
  public String sellTrigger() {
    return TransactionTrigger.DEAD_CROSS;
  }
}
