package com.muscat.backtest.domain.model;

import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.StrategyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

// 전략별 파라미터 정보
@Data
public class StrategyParameter {

  @NotNull(message = "전략 타입은 필수입니다")
  private StrategyType strategyType;
  private String name;

  // 적립식 투자 전략용
  private BigDecimal monthlyAmount;
  @Min(value = 1, message = "투자일은 1-28 사이여야 합니다")
  @Max(value = 28, message = "투자일은 1-28 사이여야 합니다")
  private Integer purchaseDay;
  @Min(value = 1, message = "투자 주기는 1-12개월 사이여야 합니다")
  @Max(value = 12, message = "투자 주기는 1-12개월 사이여야 합니다")
  private Integer investmentInterval;       // 투자 주기 (개월, null이면 1)
  private BigDecimal totalInvestmentLimit;  // 총 투자 금액 한도

  // 조건부 매수용
  private BigDecimal totalInvestment;
  private BigDecimal dropPercentage;
  private PriceDirection direction;         // 비우면 하락
  private Integer maxPurchases;
  private BigDecimal takeProfitRate;        // 비우면 익절 안 함
  private BigDecimal stopLossRate;          // 비우면 손절 안 함

  // 일시불용 (Simulation)
  private LocalDate purchaseDate;
}