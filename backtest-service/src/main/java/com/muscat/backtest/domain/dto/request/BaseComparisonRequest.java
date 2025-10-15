package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Schema(description = "비교 분석 기본 요청")
@Data
public abstract class BaseComparisonRequest {

  @Schema(description = "백테스트 시작일", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "백테스트 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "투자 금액", example = "1000000.00", required = true)
  @NotNull(message = "투자금액은 필수입니다")
  @Positive(message = "투자금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  @Schema(description = "사용자 아이디", example = "user123")
  private String userId;

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  private BigDecimal purchaseFxRate;  // 매수 시 환율 (수동)

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  private BigDecimal currentFxRate;  // 현재 환율 (수동)

  public abstract ComparisonType getComparisonType();
}