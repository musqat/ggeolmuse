package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.PeriodEnd;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// 요청 공통 옵션: 수동 환율 · 배당 재투자 · 수수료율 · 배당 원천징수 · 양도세 · 기간 끝 · 연말 정리
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class RequestOptions {

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  @Positive(message = "매수 환율은 0보다 커야 합니다")
  private BigDecimal purchaseFxRate;

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  @Positive(message = "현재 환율은 0보다 커야 합니다")
  private BigDecimal currentFxRate;

  @Schema(description = "배당금 재투자 여부 (이동평균 교차 · 밴드와 매도 규칙을 켠 조건부 매수는 켜면 400, 끄면 배당은 현금으로 셈)", example = "false")
  @Builder.Default
  private Boolean reinvestDividends = false;

  @Schema(description = "매매 수수료율 (0.25% = 0.0025, 매수 · 매도마다 차감)", example = "0.0025")
  @DecimalMin(value = "0", message = "수수료율은 0 이상 1 미만이어야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "수수료율은 0 이상 1 미만이어야 합니다")
  @Builder.Default
  private BigDecimal tradingFeeRate = BigDecimal.ZERO;

  @Schema(description = "배당 원천징수세율 (미국 원천징수 15% = 0.15, 미적용 시 0)", example = "0.15")
  @DecimalMin(value = "0", message = "배당 세율은 0 이상 1 미만이어야 합니다")
  @DecimalMax(value = "1", inclusive = false, message = "배당 세율은 0 이상 1 미만이어야 합니다")
  @Builder.Default
  private BigDecimal dividendTaxRate = BigDecimal.ZERO;

  @Schema(description = "양도소득세 계산 · 납부 여부 (결제된 해(2024-05-28 전 T+2, 2024-05-28 부터 T+1)마다 원화 실현 이익에서 연 250만원 공제 뒤 22% 를 다음 해 5월 1일 이후 첫 거래일에 판 돈에서 냄, 비우면 끔)", example = "true")
  private Boolean capitalGainsTax;

  @Schema(description = "기간이 끝나면 (HOLD 끝까지 보유, SELL_AT_END 평가일까지 마지막 거래일 종가에 남은 주식 전량 매도, 비우면 HOLD)", example = "HOLD")
  private PeriodEnd periodEnd;

  @Schema(description = "연말 정리 (양도세를 켰을 때만, 해마다 결제(2024-05-28 전 T+2, 2024-05-28 부터 T+1)가 연내에 끝나는 마지막 거래일에 한 해 원화 실현 이익이 연 250만원 공제에 맞도록 이익 난 보유분이나 손실 난 보유분을 팔고 같은 값에 다시 삼, 비우면 끔)", example = "true")
  private Boolean yearEndHarvest;
}
