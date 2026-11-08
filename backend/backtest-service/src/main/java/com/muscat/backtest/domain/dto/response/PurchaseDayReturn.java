package com.muscat.backtest.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

// 그 날짜에 샀다면 평가일까지의 총 수익률(시뮬레이션과 같은 수수료 · 배당 · 환율 기준)
@Schema(description = "매수일별 수익률")
public record PurchaseDayReturn(
  @Schema(description = "매수일(거래일)", example = "2024-03-04")
  LocalDate date,
  @Schema(description = "원화 총 수익률(%)", example = "12.50")
  BigDecimal returnPercent,
  @Schema(description = "달러 총 수익률(%)", example = "9.80")
  BigDecimal returnPercentUsd
) {
}
