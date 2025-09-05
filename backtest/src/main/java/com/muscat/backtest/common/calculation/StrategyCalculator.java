package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.infra.client.MarketDataClient.FxRate;
import com.muscat.backtest.infra.client.dto.StockPriceDto;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.util.List;

// 전략 실행 결과 계산을 담당하는 클래스
@UtilityClass
public class StrategyCalculator {
    
    // 전략 실행 결과를 계산합니다
    public static StrategyCalculationResult calculate(
            List<StrategyTransaction> transactions,
            BigDecimal totalInvested,
            BigDecimal totalShares,
            BigDecimal totalFxRateSum,
            StockPriceDto currentPrice,
            FxRate currentFxRate) {
        
        BigDecimal averagePrice = BacktestCalculationUtils.calculateAveragePrice(
            totalInvested, totalFxRateSum, totalShares);
        BigDecimal averageFxRate = BacktestCalculationUtils.calculateAverageFxRate(
            totalFxRateSum, transactions.size());

        BigDecimal currentValue = totalShares.multiply(currentPrice.getCurrentPrice());
        BigDecimal currentValueKrw = BacktestCalculationUtils.convertUsdToKrw(
            currentValue, currentFxRate.rate());

        BigDecimal totalInvestedUsd = BacktestCalculationUtils.convertKrwToUsd(
            totalInvested, averageFxRate);
        BigDecimal totalReturnUsd = currentValue.subtract(totalInvestedUsd);
        BigDecimal totalReturnPercent = BacktestCalculationUtils.calculatePercentageReturn(
            currentValue, totalInvestedUsd);
        BigDecimal totalReturnKrw = currentValueKrw.subtract(totalInvested);

        BigDecimal fxReturn = currentFxRate.rate().subtract(averageFxRate);
        BigDecimal fxReturnPercent = BacktestCalculationUtils.calculatePercentageReturn(
            currentFxRate.rate(), averageFxRate);
        
        return StrategyCalculationResult.builder()
            .totalInvested(totalInvested)
            .totalShares(totalShares)
            .averagePrice(averagePrice)
            .averageFxRate(averageFxRate)
            .currentValue(currentValue)
            .currentValueKrw(currentValueKrw)
            .totalReturnUsd(totalReturnUsd)
            .totalReturnPercent(totalReturnPercent)
            .totalReturnKrw(totalReturnKrw)
            .fxReturn(fxReturn)
            .fxReturnPercent(fxReturnPercent)
            .currentFxRate(currentFxRate.rate())
            .build();
    }
}