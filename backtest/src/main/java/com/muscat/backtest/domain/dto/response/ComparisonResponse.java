package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.ComparisonType;
import com.muscat.backtest.domain.model.ComparisonItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComparisonResponse {

  private ComparisonType comparisonType;
  private LocalDate startDate;
  private LocalDate endDate;
  private BigDecimal investmentAmount;

  // 비교 결과 리스트
  private List<ComparisonItem> items;

  // 순위 정보
  private ComparisonItem bestPerformer;      // 최고 수익률
  private ComparisonItem worstPerformer;     // 최저 수익률

  // 전체 통계
  private BigDecimal averageReturn;          // 평균 수익률
  private BigDecimal medianReturn;           // 중간값 수익률
  private String summary;                    // 비교 요약

  // 상세 분석
  private Map<String, Object> analysisDetails;

}