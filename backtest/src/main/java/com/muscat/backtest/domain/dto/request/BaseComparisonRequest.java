package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public abstract class BaseComparisonRequest {

  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @NotNull(message = "투자금액은 필수입니다")
  @Positive(message = "투자금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  private String userId;

  public abstract ComparisonType getComparisonType();
}