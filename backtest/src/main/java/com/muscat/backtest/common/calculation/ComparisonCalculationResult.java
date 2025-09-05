package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.model.ComparisonItem;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.Map;

// 비교 분석 계산 결과를 담는 값 객체
@Getter
@Builder
public class ComparisonCalculationResult {
    
    private final ComparisonItem bestPerformer;
    private final ComparisonItem worstPerformer;
    private final BigDecimal averageReturn;
    private final BigDecimal medianReturn;
    private final BigDecimal performanceSpread;
    private final String summary;
    private final Map<String, Object> analysisDetails;
}