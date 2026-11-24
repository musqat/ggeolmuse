package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.constants.BacktestConstants.RequestLimit;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.enums.type.TaxSale;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Schema(description = "조건부 매수 투자 전략 요청")
@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class ConditionalStrategyRequest extends RequestOptions implements StrategyRequest {

  @Schema(description = "종목 코드", example = "AAPL", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "전략 시작일", example = "2024-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "전략 종료일", example = "2024-09-18", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "토큰의 이메일로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "조건이 맞는 날마다 넣는 회당 금액 (KRW, 주식 수와 둘 중 하나)", example = "500000.00")
  @Positive(message = "회당 금액은 0보다 커야 합니다")
  private BigDecimal amountPerPurchase;

  @Schema(description = "조건이 맞는 날마다 사는 주식 수 (정수 1 ~ 1,000,000, 회당 금액과 둘 중 하나, 매수가 × 주식 수 + 수수료를 매수일 환율로 원화)", example = "1")
  @Min(value = 1, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  @Max(value = RequestLimit.MAX_SHARES, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  private Integer sharesPerPurchase;

  @Schema(description = "예산: 넣는 돈 합의 한도 (KRW, 비우면 제한 없음)", example = "4000000.00")
  @Positive(message = "예산은 0보다 커야 합니다")
  private BigDecimal totalInvestment;

  @Schema(description = "전 거래일 종가 대비 방향(DROP 하락 · RISE 상승 · BOTH 둘 다, 비우면 하락)", example = "DROP", defaultValue = "DROP")
  @Builder.Default
  private PriceDirection direction = PriceDirection.DROP;

  @Schema(description = "전 거래일 종가 대비 장중 하락률 · 상승률 (예: 5% = 0.05)", example = "0.05", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "하락률 · 상승률 조건은 필수입니다")
  @Positive(message = "하락률 · 상승률은 0보다 커야 합니다")
  private BigDecimal dropPercentage;

  @Schema(description = "익절 비율: 보유분 평균 단가(수수료 포함)보다 이만큼 오르면 전량 매도 (10% = 0.1, 비우면 안 함)", example = "0.1")
  private BigDecimal takeProfitRate;

  @Schema(description = "손절 비율: 보유분 평균 단가(수수료 포함)보다 이만큼 내리면 전량 매도 (10% = 0.1, 비우면 안 함)", example = "0.1")
  private BigDecimal stopLossRate;

  @Schema(description = "트레일링 스탑 하락 비율: 종가가 기준가(사이클 첫 매수가에서 시작한 종가 고점, 판 뒤에는 판 값)보다 이만큼 내리면 팜 (10% = 0.1, 0 초과 1 미만, 비우면 안 함)", example = "0.1")
  private BigDecimal trailingStopRate;

  @Schema(description = "트레일링 스탑 팔 비율: 종가가 기준가보다 하락 비율만큼 내릴 때마다 보유의 이만큼 팜 (50% = 0.5, 0 초과 1 이하, 비우면 전량, 하락 비율과 함께)", example = "0.5")
  private BigDecimal trailingSellRate;

  @Schema(description = "부분 익절 단계: 수익률이 낮은 단계부터 사이클에 한 번씩, 하루에 한 단계 보유의 팔 비율만큼 팜 (5단계까지, 비우면 안 함)")
  private List<PartialTakeProfit> partialTakeProfits;

  @Schema(description = "최대 매수 횟수 (비우면 제한 없음, 예산과 먼저 닿는 쪽에서 멈춤)", example = "10")
  @Positive(message = "최대 매수 횟수는 0보다 커야 합니다")
  private Integer maxPurchases;

  @Schema(description = "양도세 낼 현금이 모자랄 때 (NONE 현금이 생길 때 냄, SELL 납부일 종가에 모자란 만큼 팔아 냄, 비우면 NONE)", example = "SELL")
  private TaxSale taxSale;

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

  // 익절 · 손절 · 트레일링 스탑 · 부분 익절 가운데 하나라도 켰는지
  public boolean sells() {
    return takeProfitRate != null || stopLossRate != null || trailingStopRate != null
      || (partialTakeProfits != null && !partialTakeProfits.isEmpty());
  }

  // 회당 금액 · 주식 수 표시("100000원" · "2주")
  public String perPurchase() {
    return sharesPerPurchase != null ? sharesPerPurchase + "주" : amountPerPurchase + "원";
  }

  @Override
  public ConditionalStrategyRequest yearEndVariant(boolean harvest) {
    return toBuilder().yearEndHarvest(harvest).periodEnd(PeriodEnd.SELL_AT_END).userId(null).build();
  }
}
