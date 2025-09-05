package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.StrategyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StrategyRequest {

  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @NotNull(message = "전략 타입은 필수입니다")
  private StrategyType strategyType;

  private String userId;

  // 적립식 투자 전략용 필드들
  @Positive(message = "월 투자금액은 0보다 커야 합니다")
  private BigDecimal monthlyAmount;        // 월 투자금액
  private Integer investmentDay;           // 매월 몇일에 투자할지

  // 조건부 매수 전략용 필드들
  @Positive(message = "총 투자금액은 0보다 커야 합니다")
  private BigDecimal totalInvestment;     // 총 투자예산
  private BigDecimal dropPercentage;      // 하락률 조건 (예: 5% 하락시 매수)
  private Integer maxPurchases;           // 최대 매수 횟수

}