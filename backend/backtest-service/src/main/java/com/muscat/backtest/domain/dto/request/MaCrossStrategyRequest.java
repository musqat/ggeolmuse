package com.muscat.backtest.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.enums.type.TaxSale;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "이동평균 교차 투자 전략 요청")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaCrossStrategyRequest implements SignalStrategyRequest {

  @Schema(description = "종목 코드 (사고파는 종목, 지수는 안 됨)", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "전략 시작일 (이 날짜부터 교차를 봄)", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "전략 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "토큰의 subject 로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "원금: 첫 골든 크로스에 전부 넣는 돈 (KRW, 그 뒤 매수는 판 돈 전부)", example = "10000000.00", required = true)
  @NotNull(message = "투자금액은 필수입니다")
  @Positive(message = "투자금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  @Schema(description = "짧은 이동평균 기간 (거래일 2 ~ 250, 긴 기간보다 작게, 비우면 20)", example = "20")
  private Integer shortWindow;

  @Schema(description = "긴 이동평균 기간 (거래일 2 ~ 250, 비우면 60)", example = "60")
  private Integer longWindow;

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

  @Schema(description = "양도세 낼 현금이 모자랄 때 (NONE 현금이 생길 때 냄, SELL 그 날짜 종가에 모자란 만큼 팔아 냄, 비우면 NONE)", example = "SELL")
  private TaxSale taxSale;

  @Schema(description = "신호 종목: 이동평균 교차를 보는 종목 (지수 ^GSPC · ^NDX 도 됨, 비우면 종목 코드)", example = "^GSPC")
  private String signalSymbol;

  @Schema(description = "트레일링 스탑 하락 비율: 종가가 기준가(사이클 첫 매수가에서 시작한 종가 고점, 판 뒤에는 판 값)보다 이만큼 내리면 팜, 다 팔면 다음 골든 크로스에 다시 삼 (10% = 0.1, 0 초과 1 미만, 비우면 안 함)", example = "0.1")
  private BigDecimal trailingStopRate;

  @Schema(description = "트레일링 스탑 팔 비율: 그때마다 보유의 이만큼 팜 (50% = 0.5, 0 초과 1 이하, 비우면 전량, 하락 비율과 함께)", example = "0.5")
  private BigDecimal trailingSellRate;

  @Schema(description = "부분 익절 단계: 수익률이 낮은 단계부터 사이클에 한 번씩, 하루에 한 단계 보유의 팔 비율만큼 팜, 다 팔면 다음 골든 크로스에 다시 삼 (5단계까지, 비우면 안 함)")
  private List<PartialTakeProfit> partialTakeProfits;

  // 이력 저장 JSON 에는 넣지 않음
  @JsonIgnore
  @Override
  public StrategyType getStrategyType() {
    return StrategyType.MA_CROSS;
  }

  // 짧은 기간(비우면 20)
  public int shortWindowOrDefault() {
    return shortWindow != null ? shortWindow : MovingAverage.DEFAULT_SHORT_WINDOW;
  }

  // 긴 기간(비우면 60)
  public int longWindowOrDefault() {
    return longWindow != null ? longWindow : MovingAverage.DEFAULT_LONG_WINDOW;
  }
}
