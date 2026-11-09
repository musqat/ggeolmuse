package com.muscat.backtest.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

// 매도 실현 이익의 연도별 예상 양도소득세와 세후 평가
@Schema(description = "예상 양도소득세(판 해마다 원화 실현 이익 · 공제 · 세금)와 세후 평가")
public record CapitalGainsTax(
  @Schema(description = "판 거래가 있는 해마다 실현 이익 · 공제 · 세금(해 순서, 판 거래가 없으면 빈 목록)")
  List<Year> years,
  @Schema(description = "양도세 합 (KRW)", example = "330000")
  BigDecimal totalTaxKrw,
  @Schema(description = "세후 평가 (총 자산 - 양도세 합, KRW)", example = "9670000")
  BigDecimal afterTaxValueKrw,
  @Schema(description = "세후 수익률 (%, 총 수익률과 같은 기준 금액 대비)", example = "20.88")
  BigDecimal afterTaxReturnPercent
) {

  @Schema(description = "한 해 예상 양도세")
  public record Year(
    @Schema(description = "연도", example = "2024")
    int year,
    @Schema(description = "1월 ~ 12월 원화 실현 이익 합 (손실은 그해 이익과 상계, KRW)", example = "4000000")
    BigDecimal realizedGainKrw,
    @Schema(description = "기본 공제 (이익만큼 연 250만원까지, 이익이 없으면 0, KRW)", example = "2500000")
    BigDecimal deductionKrw,
    @Schema(description = "양도세 ((실현 이익 - 공제) × 22%, 원 미만 버림, KRW)", example = "330000")
    BigDecimal taxKrw
  ) {}
}
