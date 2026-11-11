package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.PeriodEnd;
import java.math.BigDecimal;

// 신호에 따라 가진 돈 전부로 사고파는 전략(이동평균 교차 · 밴드) 요청의 공통 칸
public interface SignalStrategyRequest extends StrategyRequest {

  BigDecimal getInvestmentAmount();

  BigDecimal getPurchaseFxRate();

  BigDecimal getCurrentFxRate();

  Boolean getReinvestDividends();

  BigDecimal getTradingFeeRate();

  BigDecimal getDividendTaxRate();

  Boolean getCapitalGainsTax();

  PeriodEnd getPeriodEnd();

  String getSignalSymbol();

  BigDecimal getTrailingStopRate();

  BigDecimal getTrailingSellRate();

  // 신호를 보는 종목(비우거나 매매 종목과 같으면 매매 종목)
  default String signalSymbolOrDefault() {
    String signal = getSignalSymbol() == null ? "" : getSignalSymbol().trim();
    return signal.isEmpty() || signal.equalsIgnoreCase(getSymbol()) ? getSymbol() : signal;
  }
}
