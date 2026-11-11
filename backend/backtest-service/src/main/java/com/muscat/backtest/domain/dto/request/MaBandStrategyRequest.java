package com.muscat.backtest.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.constants.BacktestConstants.Band;
import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.StrategyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "이동평균 밴드 투자 전략 요청")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaBandStrategyRequest implements SignalStrategyRequest {

  @Schema(description = "종목 코드 (사고파는 종목, 지수는 안 됨)", example = "SPY", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "전략 시작일 (이 날짜부터 밴드를 봄)", example = "2015-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "전략 종료일", example = "2024-12-31", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "토큰의 subject 로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "원금: 처음 위 선 위로 올라선 날 전부 넣는 돈 (KRW, 그 뒤 매수는 판 돈 전부)", example = "10000000.00", required = true)
  @NotNull(message = "투자금액은 필수입니다")
  @Positive(message = "투자금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  @Schema(description = "밴드 평균 기간 (거래일 2 ~ 250, 비우면 200)", example = "200")
  @Min(value = MovingAverage.MIN_WINDOW, message = Band.MESSAGE)
  @Max(value = MovingAverage.MAX_WINDOW, message = Band.MESSAGE)
  private Integer bandWindow;

  @Schema(description = "위 선 비율: 평균 × (1 + 비율) 보다 높으면 삼 (2.5% = 0.025, 0 이상 1 미만, 비우면 0.025)", example = "0.025")
  @DecimalMin(value = "0", message = Band.MESSAGE)
  @DecimalMax(value = "1", inclusive = false, message = Band.MESSAGE)
  private BigDecimal upperRate;

  @Schema(description = "아래 선 비율: 평균 × (1 - 비율) 보다 낮으면 팜 (2.5% = 0.025, 0 이상 1 미만, 비우면 0.025)", example = "0.025")
  @DecimalMin(value = "0", message = Band.MESSAGE)
  @DecimalMax(value = "1", inclusive = false, message = Band.MESSAGE)
  private BigDecimal lowerRate;

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  @Positive(message = "매수 환율은 0보다 커야 합니다")
  private BigDecimal purchaseFxRate;

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  @Positive(message = "현재 환율은 0보다 커야 합니다")
  private BigDecimal currentFxRate;

  @Schema(description = "배당금 재투자 여부 (켜면 400, 배당은 현금으로 셈)", example = "false")
  @Builder.Default
  private Boolean reinvestDividends = false;

  @Schema(description = "매매 수수료율 (0.25% = 0.0025, 매수 · 매도마다 차감)", example = "0.0025")
  @DecimalMin(value = "0", message = "수수료율은 0 이상 1 미만이어야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "수수료율은 0 이상 1 미만이어야 합니다")
  @Builder.Default
  private BigDecimal tradingFeeRate = BigDecimal.ZERO;

  @Schema(description = "배당 원천징수세율 (미국 원천징수 15% = 0.15, 미적용 시 0)", example = "0.15")
  @Builder.Default
  private BigDecimal dividendTaxRate = BigDecimal.ZERO;

  @Schema(description = "양도소득세 계산 · 납부 여부 (판 해마다 원화 실현 이익에서 연 250만원 공제 뒤 22% 를 다음 해 5월 1일 이후 첫 거래일에 판 돈에서 냄, 비우면 끔)", example = "true")
  private Boolean capitalGainsTax;

  @Schema(description = "기간이 끝나면 (HOLD 끝까지 보유, SELL_AT_END 마지막 거래일 종가에 남은 주식 전량 매도, 비우면 HOLD)", example = "HOLD")
  private PeriodEnd periodEnd;

  @Schema(description = "신호 종목: 밴드를 보는 종목 (지수 ^GSPC · ^NDX 도 됨, 비우면 종목 코드)", example = "^GSPC")
  private String signalSymbol;

  // 이력 저장 JSON 에는 넣지 않음
  @JsonIgnore
  @Override
  public StrategyType getStrategyType() {
    return StrategyType.MA_BAND;
  }

  // 평균 기간(비우면 200)
  public int bandWindowOrDefault() {
    return bandWindow != null ? bandWindow : Band.DEFAULT_WINDOW;
  }

  // 위 선 비율(비우면 0.025)
  public BigDecimal upperRateOrDefault() {
    return upperRate != null ? upperRate : Band.DEFAULT_RATE;
  }

  // 아래 선 비율(비우면 0.025)
  public BigDecimal lowerRateOrDefault() {
    return lowerRate != null ? lowerRate : Band.DEFAULT_RATE;
  }
}
