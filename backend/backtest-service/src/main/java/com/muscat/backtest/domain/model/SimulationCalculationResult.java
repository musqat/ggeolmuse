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
    List<LocalDate> dividendReinvestDates,
    List<HoldingStep> holdingSteps,
    List<StrategyTransaction> taxTransactions,  // 양도세 계산 거래(산 것 · 재투자 · 연말 정리 · 기간 끝 매도)
    List<StrategyTransaction> transactions      // 연말 정리 거래와 기간 끝 매도(연말 정리를 끄면 null)
) {
}
