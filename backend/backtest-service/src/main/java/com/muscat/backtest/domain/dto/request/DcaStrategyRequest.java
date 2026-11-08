package com.muscat.backtest.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.StrategyType;
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

@Schema(description = "DCA (적립식) 투자 전략 요청")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcaStrategyRequest implements StrategyRequest {

  @Schema(description = "종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "DCA 전략 시작일", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "DCA 전략 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "토큰의 이메일로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  // DCA 전략 전용 필드들
  @Schema(description = "회당 투자 금액 (주식 수와 둘 중 하나, monthlyAmount 로 보내도 받음)", example = "100000.00")
  @JsonAlias("monthlyAmount")
  @Positive(message = "회당 금액은 0보다 커야 합니다")
  private BigDecimal amountPerPurchase;

  @Schema(description = "회당 주식 수 (정수, 회당 금액과 둘 중 하나, 그 날짜 종가 × 주식 수 + 수수료를 그 날짜 환율로 원화)", example = "1")
  @Min(value = 1, message = "주식 수는 1 이상이어야 합니다")
  private Integer sharesPerPurchase;

  @Schema(description = "매수 주기 (DAILY 거래일마다, WEEKLY 첫 매수일의 요일마다, MONTHLY 투자 주기마다 투자일에)", example = "MONTHLY")
  @Builder.Default
  private DcaFrequency frequency = DcaFrequency.MONTHLY;

  @Schema(description = "첫 매수일 (시작일 ~ 종료일, 비우면 시작일, 휴장이면 다음 거래일, 매달은 그 달 안에서)", example = "2024-01-15")
  private LocalDate firstPurchaseDate;

  @Schema(description = "매달 투자할 날짜 (1-31, MONTHLY 에서만, 비우면 첫 매수일의 날짜, 그 달에 없으면 그 달 마지막 거래일)", example = "15")
  @Min(value = 1, message = "투자일은 1-31 사이여야 합니다")
  @Max(value = 31, message = "투자일은 1-31 사이여야 합니다")
  private Integer purchaseDay;

  @Schema(description = "투자 주기 (개월 단위 1-12, MONTHLY 에서만, 1=매달, 3=분기마다)", example = "1")
  @Min(value = 1, message = "투자 주기는 1-12개월 사이여야 합니다")
  @Max(value = 12, message = "투자 주기는 1-12개월 사이여야 합니다")
  @Builder.Default
  private Integer investmentInterval = 1;   // 투자 주기 (기본값: 1개월)

  @Schema(description = "예산 (넘는 회차부터 사지 않고 예산 부족 구간으로 남김, null이면 무제한)", example = "1000000.00")
  @Positive(message = "예산은 0보다 커야 합니다")
  private BigDecimal totalInvestmentLimit;

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  @Positive(message = "매수 환율은 0보다 커야 합니다")
  private BigDecimal purchaseFxRate;  // 매수 시 환율 (수동)

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  @Positive(message = "현재 환율은 0보다 커야 합니다")
  private BigDecimal currentFxRate;  // 현재 환율 (수동)

  @Schema(description = "배당금 재투자 여부", example = "false")
  @Builder.Default
  private Boolean reinvestDividends = false;  // 배당금 자동 재투자 여부

  @Schema(description = "매매 수수료율 (0.25% = 0.0025, 매수마다 차감)", example = "0.0025")
  @DecimalMin(value = "0", message = "수수료율은 0 이상 1 미만이어야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "수수료율은 0 이상 1 미만이어야 합니다")
  @Builder.Default
  private BigDecimal tradingFeeRate = BigDecimal.ZERO;  // 매매 수수료율

  @Schema(description = "배당 원천징수세율 (미국 원천징수 15% = 0.15, 미적용 시 0)", example = "0.15")
  @Builder.Default
  private BigDecimal dividendTaxRate = BigDecimal.ZERO;  // 배당 원천징수 세율

  // 이력 저장 JSON 에는 넣지 않음
  @JsonIgnore
  @Override
  public StrategyType getStrategyType() {
    return StrategyType.DCA;
  }

  // 회당 금액 · 주식 수 가운데 하나만
  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "회당 금액과 주식 수 가운데 하나만 넣어 주세요")
  public boolean isAmountOrShares() {
    return (amountPerPurchase == null) != (sharesPerPurchase == null);
  }

  // 회당 금액 · 주식 수 표시("100000원" · "2주")
  public String perPurchase() {
    return sharesPerPurchase != null ? sharesPerPurchase + "주" : amountPerPurchase + "원";
  }
}