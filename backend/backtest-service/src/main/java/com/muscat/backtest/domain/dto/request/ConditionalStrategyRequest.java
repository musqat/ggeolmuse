package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.constants.BacktestConstants.RequestLimit;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

@Schema(description = "조건부 매수 투자 전략 요청")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionalStrategyRequest implements StrategyRequest {

  @Schema(description = "종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "전략 시작일", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "전략 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "토큰의 이메일로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "조건이 맞는 날마다 넣는 회당 금액 (KRW, 주식 수와 둘 중 하나)", example = "500000.00")
  @Positive(message = "회당 금액은 0보다 커야 합니다")
  private BigDecimal amountPerPurchase;

  @Schema(description = "조건이 맞는 날마다 사는 주식 수 (정수 1 ~ 1,000,000, 회당 금액과 둘 중 하나, 매수가 × 주식 수 + 수수료를 그 날짜 환율로 원화)", example = "1")
  @Min(value = 1, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  @Max(value = RequestLimit.MAX_SHARES, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  private Integer sharesPerPurchase;

  @Schema(description = "예산: 넣는 돈 합의 한도 (KRW, 비우면 제한 없음)", example = "4000000.00")
  @Positive(message = "예산은 0보다 커야 합니다")
  private BigDecimal totalInvestment;

  @Schema(description = "전 거래일 종가 대비 방향(DROP 하락 · RISE 상승 · BOTH 둘 다, 비우면 하락)", example = "DROP", defaultValue = "DROP")
  @Builder.Default
  private PriceDirection direction = PriceDirection.DROP;

  @Schema(description = "전 거래일 종가 대비 장중 하락률 · 상승률 (예: 5% = 0.05)", example = "0.05", required = true)
  @NotNull(message = "하락률 · 상승률 조건은 필수입니다")
  @Positive(message = "하락률 · 상승률은 0보다 커야 합니다")
  private BigDecimal dropPercentage;

  @Schema(description = "익절 비율: 보유분 평균 단가(수수료 포함)보다 이만큼 오르면 전량 매도 (10% = 0.1, 비우면 안 함)", example = "0.1")
  @Positive(message = "익절 비율은 0보다 커야 합니다")
  private BigDecimal takeProfitRate;

  @Schema(description = "손절 비율: 보유분 평균 단가(수수료 포함)보다 이만큼 내리면 전량 매도 (10% = 0.1, 비우면 안 함)", example = "0.1")
  @Positive(message = "손절 비율은 0보다 커야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "손절 비율은 1보다 작아야 합니다")
  private BigDecimal stopLossRate;

  @Schema(description = "최대 매수 횟수 (비우면 제한 없음, 예산과 먼저 닿는 쪽에서 멈춤)", example = "10")
  @Positive(message = "최대 매수 횟수는 0보다 커야 합니다")
  private Integer maxPurchases;

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

  @Schema(description = "예상 양도소득세 계산 여부 (판 해마다 원화 실현 이익에서 연 250만원 공제 뒤 22%, 비우면 끔)", example = "true")
  private Boolean capitalGainsTax;

  // 이력 저장 JSON 에는 넣지 않음
  @JsonIgnore
  @Override
  public StrategyType getStrategyType() {
    return StrategyType.CONDITIONAL_PURCHASE;
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