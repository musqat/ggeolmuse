package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.commonlib.dto.StockPriceDto;
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
            FxRateDto currentFxRate,
            BigDecimal totalDividends,
            BigDecimal dividendsReinvested) {
        
        BigDecimal averagePrice = BacktestCalculationUtils.calculateAveragePrice(
            totalInvested, totalFxRateSum, totalShares);
        BigDecimal averageFxRate = BacktestCalculationUtils.calculateAverageFxRate(
            totalFxRateSum, totalInvested);

        // MoneyUtils를 사용한 정확한 백테스트 계산
        BigDecimal currentValue = MoneyUtils.multiply(totalShares, currentPrice.currentPrice());
        currentValue = MoneyUtils.roundUsd(currentValue);
        
        BigDecimal currentValueKrw = MoneyUtils.convertUsdToKrw(currentValue, currentFxRate.rate());

        BigDecimal totalInvestedUsd = MoneyUtils.convertKrwToUsd(totalInvested, averageFxRate);
        BigDecimal totalReturnUsd = MoneyUtils.subtract(currentValue, totalInvestedUsd);
        
        // 수익률 계산 - MoneyUtils의 백분율 계산 사용
        BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(totalInvestedUsd, currentValue);
        BigDecimal totalReturnKrw = MoneyUtils.subtract(currentValueKrw, totalInvested);

        BigDecimal fxReturn = MoneyUtils.subtract(currentFxRate.rate(), averageFxRate);
        BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(averageFxRate, currentFxRate.rate());

        // DCA/Conditional은 소수점 거래를 하므로 투자금을 전액 사용 (남은 현금 = 0)
        // 각 거래에서 amount만큼 정확히 주식을 매수하므로 잔액이 남지 않음
        BigDecimal remainingCashKrw = BigDecimal.ZERO;

        // 배당금을 원화로 환산
        BigDecimal totalDividendsKrw = totalDividends != null && totalDividends.compareTo(BigDecimal.ZERO) > 0
            ? MoneyUtils.convertUsdToKrw(totalDividends, currentFxRate.rate())
            : BigDecimal.ZERO;

        // 재투자된 배당금이 있으면, 이미 주식 가치에 포함되어 있으므로 배당금을 중복 더하지 않음
        boolean hasReinvested = dividendsReinvested != null && dividendsReinvested.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal dividendsToAdd = hasReinvested ? BigDecimal.ZERO : totalDividendsKrw;

        // 총 자산 = 주식 가치 + 배당금 (재투자 시 배당금 제외)
        BigDecimal totalAssetKrw = currentValueKrw.add(dividendsToAdd);

        // 총 수익 = 주식 가치 + 배당금 - 투자금
        BigDecimal adjustedTotalReturnKrw = MoneyUtils.subtract(totalAssetKrw, totalInvested);
        BigDecimal adjustedTotalReturnPercent = MoneyUtils.calculateReturnRate(totalInvested, totalAssetKrw);

        return StrategyCalculationResult.builder()
            .totalInvested(totalInvested)
            .totalShares(totalShares)
            .averagePrice(averagePrice)
            .averageFxRate(averageFxRate)
            .currentValue(currentValue)
            .currentValueKrw(currentValueKrw)
            .totalDividends(totalDividends != null ? totalDividends : BigDecimal.ZERO)
            .dividendsReinvested(dividendsReinvested != null ? dividendsReinvested : BigDecimal.ZERO)
            .remainingCashKrw(remainingCashKrw)
            .totalAssetKrw(totalAssetKrw)
            .totalReturnUsd(totalReturnUsd)
            .totalReturnPercent(adjustedTotalReturnPercent)
            .totalReturnKrw(adjustedTotalReturnKrw)
            .fxReturn(fxReturn)
            .fxReturnPercent(fxReturnPercent)
            .currentFxRate(currentFxRate.rate())
            .build();
    }
}