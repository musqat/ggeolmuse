package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.type.ComparisonType;
import com.muscat.backtest.domain.model.ComparisonItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Schema(description = "비교 분석 결과")
@Data
@Builder
public class ComparisonResponse {

  @Schema(description = "비교 유형", example = "STRATEGIES")
  private ComparisonType comparisonType;
  @Schema(description = "비교 시작일", example = "2024-01-01")
  private LocalDate startDate;
  @Schema(description = "비교 종료일", example = "2024-09-18")
  private LocalDate endDate;
  @Schema(description = "투자 금액", example = "1000000.00")
  private BigDecimal investmentAmount;

  // 비교 결과 리스트
  @Schema(description = "비교 항목 목록")
  private List<ComparisonItem> items;

  // 순위 정보
  @Schema(description = "최고 수익률 항목")
  private ComparisonItem bestPerformer;      // 최고 수익률
  @Schema(description = "최저 수익률 항목")
  private ComparisonItem worstPerformer;     // 최저 수익률

  // 전체 통계
  @Schema(description = "평균 수익률", example = "15.25")
  private BigDecimal averageReturn;          // 평균 수익률
  @Schema(description = "중간값 수익률", example = "12.80")
  private BigDecimal medianReturn;           // 중간값 수익률
  @Schema(description = "비교 요약 메시지", example = "DCA 전략이 가장 우수한 성과를 보였습니다")
  private String summary;                    // 비교 요약

  // 상세 분석
  @Schema(description = "상세 분석 데이터")
  private Map<String, Object> analysisDetails;

}