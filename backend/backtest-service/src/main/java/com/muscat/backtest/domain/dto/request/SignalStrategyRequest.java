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
}
