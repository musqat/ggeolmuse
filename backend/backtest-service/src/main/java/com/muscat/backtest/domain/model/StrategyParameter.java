package com.muscat.backtest.domain.model;

import com.muscat.backtest.common.enums.type.StrategyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

// 전략별 파라미터 정보
@Data
public class StrategyParameter {

  private StrategyType strategyType;
  private String name;

  // 적립식 투자 전략용
  private BigDecimal monthlyAmount;
  private Integer purchaseDay;

  // 조건부 매수용
  private BigDecimal totalInvestment;
  private BigDecimal dropPercentage;
  private Integer maxPurchases;

  // 일시불용 (Simulation)
  private LocalDate purchaseDate;
}