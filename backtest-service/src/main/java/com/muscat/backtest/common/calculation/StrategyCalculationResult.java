package com.muscat.backtest.common.calculation;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

// 전략 계산 결과를 담는 값 객체
@Getter
@Builder
public class StrategyCalculationResult {
    
    private final BigDecimal totalInvested;
    private final BigDecimal totalShares;
    private final BigDecimal averagePrice;
    private final BigDecimal averageFxRate;
    private final BigDecimal currentValue;
    private final BigDecimal currentValueKrw;
    private final BigDecimal totalReturnUsd;
    private final BigDecimal totalReturnPercent;
    private final BigDecimal totalReturnKrw;
    private final BigDecimal fxReturn;
    private final BigDecimal fxReturnPercent;
    private final BigDecimal currentFxRate;
}