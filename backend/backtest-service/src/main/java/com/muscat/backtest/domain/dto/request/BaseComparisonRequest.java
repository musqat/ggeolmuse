package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.ComparisonType;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Schema(description = "비교 분석 기본 요청")
@Data
public abstract class BaseComparisonRequest {

  @Schema(description = "백테스트 시작일", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "백테스트 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "투자 금액 (종목 비교는 종목마다, 전략 비교는 전략마다 원금: 단순 투자금 · 적립식 · 조건부 예산 · 이동평균 교차 원금)", example = "1000000.00", required = true)
  @NotNull(message = "투자금액은 필수입니다")
  @Positive(message = "투자금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  @Schema(description = "토큰의 subject 로 채움", accessMode = Schema.AccessMode.READ_ONLY)
  private String userId;

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  @Positive(message = "매수 환율은 0보다 커야 합니다")
  private BigDecimal purchaseFxRate;  // 매수 시 환율 (수동)

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  @Positive(message = "현재 환율은 0보다 커야 합니다")
  private BigDecimal currentFxRate;  // 현재 환율 (수동)

  @Schema(description = "배당금 재투자 여부 (null이면 끔)", example = "false")
  private Boolean reinvestDividends;

  @Schema(description = "매매 수수료율 (0.25% = 0.0025, null이면 0)", example = "0.0025")
  @DecimalMin(value = "0", message = "수수료율은 0 이상 1 미만이어야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "수수료율은 0 이상 1 미만이어야 합니다")
  private BigDecimal tradingFeeRate;

  @Schema(description = "배당 원천징수 세율 (15% = 0.15, null이면 0)", example = "0.15")
  private BigDecimal dividendTaxRate;

  @Schema(description = "양도소득세 계산 · 납부 여부 (항목마다 판 해의 원화 실현 이익에서 연 250만원 공제 뒤 22% 를 다음 해 5월 1일 이후 첫 거래일에 판 돈에서 냄, 비우면 끔)", example = "true")
  private Boolean capitalGainsTax;

  @Schema(description = "기간이 끝나면 (HOLD 끝까지 보유, SELL_AT_END 항목마다 마지막 거래일 종가에 남은 주식 전량 매도, 비우면 HOLD)", example = "HOLD")
  private PeriodEnd periodEnd;

  public abstract ComparisonType getComparisonType();
}