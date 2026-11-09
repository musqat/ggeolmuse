package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 시뮬레이션 계산 결과
public record SimulationCalculationResult(
    BigDecimal investmentKrw,     // 투자 원금(주식 수로 샀으면 산 날 비용, KRW)
    BigDecimal purchasePriceUsd,
    BigDecimal shares,
    BigDecimal currentPriceUsd,
    BigDecimal currentValueUsd,
    BigDecimal currentValueKrw,
    BigDecimal stockReturn,
    BigDecimal stockReturnPercent,
    BigDecimal purchaseFxRate,
    BigDecimal currentFxRate,
    BigDecimal fxReturn,
    BigDecimal fxReturnPercent,
    BigDecimal totalDividends,
    BigDecimal dividendYield,
    BigDecimal tradingFee,
    BigDecimal remainingCash,
    BigDecimal totalAssetKrw,
    BigDecimal totalReturnKrw,
    BigDecimal totalReturnPercent,
    BigDecimal dividendsReinvested,
    List<Reinvestment> reinvestments,
    List<HoldingStep> holdingSteps,
    BigDecimal buyCostUsd,        // 산 주식의 원가(주식 수 × 매수가 + 수수료, USD)
    StrategyTransaction endSale   // 기간 끝 매도(끝까지 보유면 null)
) {

  /** 배당락일과 다시 산 세후 배당(USD) */
  public record Reinvestment(LocalDate exDate, BigDecimal amountUsd) {}

  // 배당 재투자 날짜 목록
  public List<LocalDate> dividendReinvestDates() {
    return reinvestments.stream().map(Reinvestment::exDate).toList();
  }
}
