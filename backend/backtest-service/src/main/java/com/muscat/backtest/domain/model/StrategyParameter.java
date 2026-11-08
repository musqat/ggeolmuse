package com.muscat.backtest.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.StrategyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

// 전략별 파라미터 정보
@Data
public class StrategyParameter {

  @NotNull(message = "전략 타입은 필수입니다")
  private StrategyType strategyType;
  private String name;

  // 적립식 · 조건부용
  @JsonAlias("monthlyAmount")
  @Positive(message = "회당 금액은 0보다 커야 합니다")
  private BigDecimal amountPerPurchase;

  // 적립식 투자 전략용
  private DcaFrequency frequency;           // 비우면 매달
  private LocalDate firstPurchaseDate;      // 비우면 시작일
  @Min(value = 1, message = "투자일은 1-31 사이여야 합니다")
  @Max(value = 31, message = "투자일은 1-31 사이여야 합니다")
  private Integer purchaseDay;              // 비우면 첫 매수일의 날짜
  @Min(value = 1, message = "투자 주기는 1-12개월 사이여야 합니다")
  @Max(value = 12, message = "투자 주기는 1-12개월 사이여야 합니다")
  private Integer investmentInterval;       // 투자 주기 (개월, null이면 1)

  // 조건부 매수용
  private BigDecimal dropPercentage;
  private PriceDirection direction;         // 비우면 하락
  @Positive(message = "최대 매수 횟수는 0보다 커야 합니다")
  private Integer maxPurchases;             // 비우면 제한 없음
  private BigDecimal takeProfitRate;        // 비우면 익절 안 함
  private BigDecimal stopLossRate;          // 비우면 손절 안 함

  // 일시불용 (Simulation)
  private LocalDate purchaseDate;

  // 적립식 · 조건부는 회당 금액 필수
  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "적립식 · 조건부 전략에는 회당 금액을 넣어 주세요")
  public boolean isAmountForPurchases() {
    return strategyType == null || strategyType == StrategyType.SIMPLE || amountPerPurchase != null;
  }
}
