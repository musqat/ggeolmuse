package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.commonlib.dto.StockPriceDto;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
            BigDecimal dividendsReinvested,
            BigDecimal dividendTaxRate) {
        
        // 매도 · 판 돈 매수가 없으면 입금 기준 평균 단가, 있으면 지금 보유분 원가
        boolean traded = transactions.stream().anyMatch(tx -> tx.isSell() || tx.getCashUsed() != null);
        BigDecimal averagePrice = traded
            ? holdingAverage(transactions)
            : BacktestCalculationUtils.calculateAveragePrice(totalInvested, totalFxRateSum, totalShares);
        BigDecimal cashBalance = transactions.stream()
            .map(StrategyTransaction::cashChange)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal realizedProfit = transactions.stream()
            .map(StrategyTransaction::getRealizedProfit)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageFxRate = BacktestCalculationUtils.calculateAverageFxRate(
            totalFxRateSum, totalInvested);

        // MoneyUtils를 사용한 정확한 백테스트 계산
        BigDecimal currentValue = MoneyUtils.multiply(totalShares, currentPrice.currentPrice());
        currentValue = MoneyUtils.roundUsd(currentValue);
        
        // 전량 매도로 보유가 없으면 0
        BigDecimal currentValueKrw = Decimals.isPositive(currentValue)
            ? MoneyUtils.convertUsdToKrw(currentValue, currentFxRate.rate())
            : BigDecimal.ZERO;

        BigDecimal totalInvestedUsd = MoneyUtils.convertKrwToUsd(totalInvested, averageFxRate);
        
        // 수익률 계산 - MoneyUtils의 백분율 계산 사용
        BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(totalInvestedUsd, currentValue);
        BigDecimal totalReturnKrw = MoneyUtils.subtract(currentValueKrw, totalInvested);

        BigDecimal fxReturn = MoneyUtils.subtract(currentFxRate.rate(), averageFxRate);
        BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(averageFxRate, currentFxRate.rate());

        // 재투자했으면 배당이 이미 주식에, 안 했으면 현금으로
        boolean reinvested = dividendsReinvested != null
            && dividendsReinvested.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCashDividend = !reinvested
            && totalDividends != null && totalDividends.compareTo(BigDecimal.ZERO) > 0;

        // 현금으로 받는 배당도 원천징수 차감(재투자 경로는 DividendReinvestor 가 이미 뗌)
        BigDecimal cashDividendKrw = BigDecimal.ZERO;
        if (hasCashDividend) {
            BigDecimal afterTax = totalDividends;
            if (dividendTaxRate != null && Decimals.isPositive(dividendTaxRate)) {
                afterTax = totalDividends.subtract(totalDividends.multiply(dividendTaxRate));
            }
            cashDividendKrw = MoneyUtils.convertUsdToKrw(afterTax, currentFxRate.rate());
        }

        BigDecimal cashKrw = Decimals.isPositive(cashBalance)
            ? MoneyUtils.convertUsdToKrw(cashBalance, currentFxRate.rate())
            : BigDecimal.ZERO;
        BigDecimal totalAssetKrw = currentValueKrw.add(cashDividendKrw).add(cashKrw);

        // 총 수익 = 주식 가치(+현금 배당) - 투자금
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
            .totalAssetKrw(totalAssetKrw)
            .cashBalance(cashBalance)
            .realizedProfit(realizedProfit)
            .totalReturnPercent(adjustedTotalReturnPercent)
            .totalReturnKrw(adjustedTotalReturnKrw)
            .fxReturn(fxReturn)
            .fxReturnPercent(fxReturnPercent)
            .currentFxRate(currentFxRate.rate())
            .defaultFxValuationDate(currentFxRate.defaulted() ? currentFxRate.date() : null)
            .build();
    }

    // 지금 보유분의 평균 원가(USD, 매수는 입금 달러 + 쓴 판 돈, 매도는 판 비율만큼 원가를 덜어 냄, 보유가 없으면 null)
    private static BigDecimal holdingAverage(List<StrategyTransaction> transactions) {
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (StrategyTransaction tx : transactions) {
            if (tx.isSell()) {
                if (shares.signum() > 0) {
                    cost = cost.subtract(cost.multiply(tx.getShares().negate()).divide(shares, 8, RoundingMode.HALF_UP));
                }
            } else {
                if (Decimals.isPositive(tx.getAmount())) {
                    cost = cost.add(tx.getAmount().divide(tx.getFxRate(), 8, RoundingMode.HALF_UP));
                }
                if (tx.getCashUsed() != null) {
                    cost = cost.add(tx.getCashUsed());
                }
            }
            shares = shares.add(tx.getShares());
        }
        return shares.signum() > 0 ? cost.divide(shares, 8, RoundingMode.HALF_UP) : null;
    }
}