package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.infra.client.MarketDataClient.FxRate;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.backtest.infra.client.dto.StockPriceDto;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.util.List;

// 전략 실행 결과 계산을 담당하는 클래스
@UtilityClass
public class StrategyCalculator {
    
    // 전략 실행 결과를 계산
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

        // MoneyUtils를 사용한 정확한 백테스트 계산
        BigDecimal currentValue = MoneyUtils.multiply(totalShares, currentPrice.getCurrentPrice());
        currentValue = MoneyUtils.roundUsd(currentValue);
        
        BigDecimal currentValueKrw = MoneyUtils.calculateUsdToKrw(currentValue, currentFxRate.rate());

        BigDecimal totalInvestedUsd = MoneyUtils.calculateKrwToUsd(totalInvested, averageFxRate);
        BigDecimal totalReturnUsd = MoneyUtils.subtract(currentValue, totalInvestedUsd);
        
        // 수익률 계산 - MoneyUtils의 백분율 계산 사용
        BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(totalInvestedUsd, currentValue);
        BigDecimal totalReturnKrw = MoneyUtils.subtract(currentValueKrw, totalInvested);

        BigDecimal fxReturn = MoneyUtils.subtract(currentFxRate.rate(), averageFxRate);
        BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(averageFxRate, currentFxRate.rate());
        
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