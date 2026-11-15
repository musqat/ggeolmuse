package com.muscat.backtest.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.muscat.backtest.common.constants.BacktestConstants.RequestLimit;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Schema(description = "투자 시뮬레이션 요청")
@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class SimulationRequest extends RequestOptions {

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

  @Schema(description = "살 주식 수 (정수 1 ~ 1,000,000, 투자 금액과 둘 중 하나, 산 날(휴장이면 다음 거래일) 종가 × 주식 수 + 수수료를 같은 날 환율로 원화)", example = "3")
  @Min(value = 1, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  @Max(value = RequestLimit.MAX_SHARES, message = "주식 수는 1 ~ 1,000,000 사이여야 합니다")
  private Integer sharesPerPurchase;

  @Schema(description = "토큰의 이메일로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

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
