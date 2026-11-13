package com.muscat.backtest.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.constants.BacktestConstants.RequestLimit;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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

@Schema(description = "투자 시뮬레이션 요청")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {

  @Schema(description = "종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "매수일", example = "2024-01-15", required = true)
  @NotNull(message = "매수일은 필수입니다")
  private LocalDate purchaseDate;

  @Schema(description = "매도일 (null이거나 오늘 이후면 오늘 평가)", example = "2024-12-31")
  private LocalDate saleDate;

  @Schema(description = "투자 금액 (주식 수와 둘 중 하나)", example = "1000000.00")
  @Positive(message = "투자 금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  @Schema(description = "살 주식 수 (정수 1 ~ 1,000,000, 투자 금액과 둘 중 하나, 산 날(휴장이면 다음 거래일) 종가 × 주식 수 + 수수료를 그 날짜 환율로 원화)", example = "3")
  @Min(value = 1, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  @Max(value = RequestLimit.MAX_SHARES, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  private Integer sharesPerPurchase;

  // 매매 수수료 설정 (선택적, 기본값 0%)
  @Schema(description = "매매 수수료율 (0.25% = 0.0025)", example = "0.0025")
  @DecimalMin(value = "0", message = "수수료율은 0 이상 1 미만이어야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "수수료율은 0 이상 1 미만이어야 합니다")
  @Builder.Default
  private BigDecimal tradingFeeRate = BigDecimal.ZERO; // 매매수수료율 (0.25% = 0.0025)

  @Schema(description = "토큰의 이메일로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  @Positive(message = "매수 환율은 0보다 커야 합니다")
  private BigDecimal purchaseFxRate;  // 매수 시 환율 (수동)

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  @Positive(message = "현재 환율은 0보다 커야 합니다")
  private BigDecimal currentFxRate;  // 현재 환율 (수동)

  @Schema(description = "배당금 재투자 여부", example = "false")
  @Builder.Default
  private Boolean reinvestDividends = false;  // 배당금 자동 재투자 여부

  @Schema(description = "배당 원천징수세율 (미국 원천징수 15% = 0.15, 미적용 시 0)", example = "0.15")
  @Builder.Default
  private BigDecimal dividendTaxRate = BigDecimal.ZERO;  // 배당 원천징수 세율

  @Schema(description = "양도소득세 계산 · 납부 여부 (결제된 해(2024-05-28 전 T+2, 그 뒤 T+1)마다 원화 실현 이익에서 연 250만원 공제 뒤 22% 를 다음 해 5월 1일 이후 첫 거래일에 판 돈에서 냄, 비우면 끔)", example = "true")
  private Boolean capitalGainsTax;

  @Schema(description = "기간이 끝나면 (HOLD 끝까지 보유, SELL_AT_END 평가일까지 마지막 거래일 종가에 전량 매도, 비우면 HOLD)", example = "HOLD")
  private PeriodEnd periodEnd;

  @Schema(description = "연말 정리 (양도세를 켰을 때만, 해마다 결제(2024-05-28 전 T+2, 그 뒤 T+1)가 그해 안에 끝나는 마지막 거래일에 그해 원화 실현 이익이 연 250만원 공제에 맞도록 이익 난 보유분이나 손실 난 보유분을 팔고 같은 값에 다시 삼, 비우면 끔)", example = "true")
  private Boolean yearEndHarvest;

  // 평가일(매도일이 없거나 오늘 이후면 오늘)
  public LocalDate valuationDate(LocalDate today) {
    return saleDate != null && saleDate.isBefore(today) ? saleDate : today;
  }

  // 투자 금액 · 주식 수 가운데 하나만
  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "투자 금액과 주식 수 가운데 하나만 넣어 주세요")
  public boolean isAmountOrShares() {
    return (investmentAmount == null) != (sharesPerPurchase == null);
  }

  // 연말 정리를 켜거나 끄고 기간 끝에 파는 사본(이력 주인은 비움)
  public SimulationRequest yearEndVariant(boolean harvest) {
    return toBuilder().yearEndHarvest(harvest).periodEnd(PeriodEnd.SELL_AT_END).userId(null).build();
  }
}
