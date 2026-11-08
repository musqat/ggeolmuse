package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// 비교 분석 계산을 담당하는 클래스
@UtilityClass
public class ComparisonCalculator {

    // 비교 분석 결과 계산(매수 0회 항목은 순위 · 통계에서 빼고 목록 끝에)
    public static ComparisonCalculationResult calculate(List<ComparisonItem> items) {

        // 정렬 및 순위 매기기
        items.sort(Comparator.comparing(ComparisonCalculator::notInvested)
            .thenComparing(ComparisonItem::getTotalReturnPercent, Comparator.reverseOrder()));
        int rank = 0;
        for (ComparisonItem item : items) {
            item.setRank(notInvested(item) ? null : ++rank);
        }

        List<ComparisonItem> ranked = items.subList(0, rank);
        if (ranked.isEmpty()) {
            return ComparisonCalculationResult.builder().build();
        }
        List<BigDecimal> returns = ranked.stream()
            .map(ComparisonItem::getTotalReturnPercent)
            .toList();

        BigDecimal averageReturn = BacktestCalculationUtils.calculateAverageReturn(returns);
        BigDecimal medianReturn = BacktestCalculationUtils.calculateMedian(returns);

        // 최고/최저 성과
        ComparisonItem bestPerformer = ranked.getFirst();
        ComparisonItem worstPerformer = ranked.getLast();

        return ComparisonCalculationResult.builder()
            .bestPerformer(bestPerformer)
            .worstPerformer(worstPerformer)
            .averageReturn(averageReturn)
            .medianReturn(medianReturn)
            .build();
    }

    // 투자금 0 인 매수 0회 항목
    private static boolean notInvested(ComparisonItem item) {
        return item.getTotalInvested() != null && item.getTotalInvested().signum() == 0;
    }
}
