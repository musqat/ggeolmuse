package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

// 비교 분석 계산을 담당하는 클래스
@UtilityClass
public class ComparisonCalculator {
    
    // 비교 분석 결과 계산
    public static ComparisonCalculationResult calculate(List<ComparisonItem> items) {
        
        // 정렬 및 순위 매기기
        items.sort((a, b) -> b.getTotalReturnPercent().compareTo(a.getTotalReturnPercent()));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }

        // 통계 계산
        BigDecimal averageReturn = BacktestCalculationUtils.calculateAverageReturn(
            items.stream()
                .map(ComparisonItem::getTotalReturnPercent)
                .collect(Collectors.toList()));

        BigDecimal medianReturn = BacktestCalculationUtils.calculateMedian(
            items.stream()
                .map(ComparisonItem::getTotalReturnPercent)
                .collect(Collectors.toList()));

        // 최고/최저 성과
        ComparisonItem bestPerformer = items.getFirst();
        ComparisonItem worstPerformer = items.get(items.size() - 1);

        
        return ComparisonCalculationResult.builder()
            .bestPerformer(bestPerformer)
            .worstPerformer(worstPerformer)
            .averageReturn(averageReturn)
            .medianReturn(medianReturn)
            .build();
    }
}