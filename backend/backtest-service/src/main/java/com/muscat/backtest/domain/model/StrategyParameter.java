package com.muscat.backtest.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.constants.BacktestConstants.Band;
import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.StrategyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

  // 이동평균 교차용 (거래일)
  @Min(value = MovingAverage.MIN_WINDOW, message = MovingAverage.WINDOW_MESSAGE)
  @Max(value = MovingAverage.MAX_WINDOW, message = MovingAverage.WINDOW_MESSAGE)
  private Integer shortWindow;              // 비우면 20
  @Min(value = MovingAverage.MIN_WINDOW, message = MovingAverage.WINDOW_MESSAGE)
  @Max(value = MovingAverage.MAX_WINDOW, message = MovingAverage.WINDOW_MESSAGE)
  private Integer longWindow;               // 비우면 60

  // 이동평균 밴드용 (거래일 · 소수 비율)
  @Min(value = MovingAverage.MIN_WINDOW, message = Band.MESSAGE)
  @Max(value = MovingAverage.MAX_WINDOW, message = Band.MESSAGE)
  private Integer bandWindow;               // 비우면 200
  @DecimalMin(value = "0", message = Band.MESSAGE)
  @DecimalMax(value = "1", inclusive = false, message = Band.MESSAGE)
  private BigDecimal upperRate;             // 비우면 0.025
  @DecimalMin(value = "0", message = Band.MESSAGE)
  @DecimalMax(value = "1", inclusive = false, message = Band.MESSAGE)
  private BigDecimal lowerRate;             // 비우면 0.025

  // 일시불용 (Simulation)
  private LocalDate purchaseDate;

  // 적립식 · 조건부는 회당 금액 필수
  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "적립식 · 조건부 전략에는 회당 금액을 넣어 주세요")
  public boolean isAmountForPurchases() {
    return strategyType == null || strategyType == StrategyType.SIMPLE || strategyType == StrategyType.MA_CROSS
      || strategyType == StrategyType.MA_BAND || amountPerPurchase != null;
  }

  // 이동평균 교차는 짧은 기간 < 긴 기간(비운 칸은 기본값)
  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = MovingAverage.WINDOW_MESSAGE)
  public boolean isWindowOrder() {
    if (strategyType != StrategyType.MA_CROSS) {
      return true;
    }
    int shortDays = shortWindow != null ? shortWindow : MovingAverage.DEFAULT_SHORT_WINDOW;
    int longDays = longWindow != null ? longWindow : MovingAverage.DEFAULT_LONG_WINDOW;
    return shortDays < longDays;
  }
}
