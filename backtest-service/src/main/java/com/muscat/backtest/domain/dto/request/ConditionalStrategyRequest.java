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
public class ConditionalStrategyRequest {

  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  private String userId;

  // 조건부 매수 전략 전용 필드들
  @NotNull(message = "총 투자금액은 필수입니다")
  @Positive(message = "총 투자금액은 0보다 커야 합니다")
  private BigDecimal totalInvestment;     // 총 투자예산

  @NotNull(message = "하락률 조건은 필수입니다")
  @Positive(message = "하락률은 0보다 커야 합니다")
  private BigDecimal dropPercentage;      // 하락률 조건 (예: 5% 하락시 매수)

  @Positive(message = "최대 매수 횟수는 0보다 커야 합니다")
  private Integer maxPurchases;           // 최대 매수 횟수
}