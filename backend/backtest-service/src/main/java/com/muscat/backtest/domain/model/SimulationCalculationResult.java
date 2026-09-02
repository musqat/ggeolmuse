package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 시뮬레이션 계산 결과
public record SimulationCalculationResult(
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
    List<LocalDate> dividendReinvestDates
) {
}
