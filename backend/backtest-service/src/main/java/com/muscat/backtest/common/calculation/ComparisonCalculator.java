package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 비교 분석 계산을 담당하는 클래스
@UtilityClass
public class ComparisonCalculator {
    
    // 비교 분석 결과를 계산합니다
    public static ComparisonCalculationResult calculate(List<ComparisonItem> items, String analysisType) {
        
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
        
        BigDecimal performanceSpread = bestPerformer.getTotalReturnPercent()
            .subtract(worstPerformer.getTotalReturnPercent());

        // 요약 생성
        String summary = String.format("%s 결과: 최고 %s ($%.2f, %.2f%%), 최저 %s ($%.2f, %.2f%%), 평균 수익률 %.2f%%",
            analysisType,
            bestPerformer.getName(), bestPerformer.getTotalReturn(), bestPerformer.getTotalReturnPercent(),
            worstPerformer.getName(), worstPerformer.getTotalReturn(), worstPerformer.getTotalReturnPercent(),
            averageReturn);

        // 상세 분석 데이터
        Map<String, Object> analysisDetails = new HashMap<>();
        analysisDetails.put("totalItems", items.size());
        analysisDetails.put("analysisType", analysisType);
        analysisDetails.put("performanceSpread", performanceSpread);
        
        return ComparisonCalculationResult.builder()
            .bestPerformer(bestPerformer)
            .worstPerformer(worstPerformer)
            .averageReturn(averageReturn)
            .medianReturn(medianReturn)
            .performanceSpread(performanceSpread)
            .summary(summary)
            .analysisDetails(analysisDetails)
            .build();
    }
}