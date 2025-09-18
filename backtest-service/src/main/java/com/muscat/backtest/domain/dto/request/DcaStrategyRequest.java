package com.muscat.backtest.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DcaStrategyRequest {

  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  private String userId;

  // DCA 전략 전용 필드들
  @NotNull(message = "월 투자금액은 필수입니다")
  @Positive(message = "월 투자금액은 0보다 커야 합니다")
  private BigDecimal monthlyAmount;        // 월 투자금액

  @NotNull(message = "투자일은 필수입니다")
  @Positive(message = "투자일은 1 이상이어야 합니다")
  private Integer purchaseDay;             // 매월 몇일에 투자할지
}