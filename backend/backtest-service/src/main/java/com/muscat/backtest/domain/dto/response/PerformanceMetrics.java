package com.muscat.backtest.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "기간 수익 · 위험 지표(퍼센트는 소수 둘째 자리, 낼 수 없으면 없음)")
public record PerformanceMetrics(
  @Schema(description = "연평균 수익률(%), 1년 미만이거나 원금을 모두 잃으면 없음", example = "14.86")
  BigDecimal cagr,
  @Schema(description = "최대 낙폭(%), 0 이하", example = "-33.52")
  BigDecimal mdd,
  @Schema(description = "최대 낙폭의 고점 날짜", example = "2020-02-19")
  LocalDate mddPeakDate,
  @Schema(description = "최대 낙폭의 저점 날짜", example = "2020-03-23")
  LocalDate mddTroughDate,
  @Schema(description = "고점 가치로 돌아온 날, 아직이면 없음", example = "2020-08-10")
  LocalDate mddRecoveryDate,
  @Schema(description = "연 변동성(%)", example = "31.20")
  BigDecimal volatility,
  @Schema(description = "샤프 비율(무위험 수익률 연 3%)", example = "0.62")
  BigDecimal sharpe
) {

  public static PerformanceMetrics empty() {
    return new PerformanceMetrics(null, null, null, null, null, null, null);
  }
}
