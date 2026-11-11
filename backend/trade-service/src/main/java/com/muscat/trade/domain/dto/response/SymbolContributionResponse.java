package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "종목별 기여(기간 동안 늘어난 원화 수익을 종목마다 나눈 값, 기간 끝은 기간 안 마지막 수익 흐름 점 날짜이고 없으면 오늘)")
public record SymbolContributionResponse(
  @Schema(description = "종목 합계 원화 합", example = "1250000")
  BigDecimal totalKrw,

  @Schema(description = "기간 안 칸 하나라도 0 이 아니거나 기간 끝에 들고 있는 종목(합계 원화 큰 것부터)")
  List<SymbolContribution> symbols
) {

  @Schema(description = "종목 하나의 기간 기여(칸마다 기간 끝 값 − 기간 시작 전 값, 각자 그 날짜 환율 · 시작 전날 환율이 없으면 기간 끝 환율)")
  public record SymbolContribution(
    @Schema(description = "종목", example = "AAPL")
    String symbol,

    @Schema(description = "실현 손익 원화의 기간 변화", example = "46834")
    BigDecimal realizedKrw,

    @Schema(description = "평가 손익 원화의 기간 변화", example = "190680")
    BigDecimal unrealizedKrw,

    @Schema(description = "받은 배당(세후) 원화의 기간 변화", example = "0")
    BigDecimal dividendKrw,

    @Schema(description = "뺀 수수료 원화의 기간 변화", example = "1200")
    BigDecimal feeKrw,

    @Schema(description = "실현 + 평가 + 배당 − 수수료", example = "236314")
    BigDecimal totalKrw,

    @Schema(description = "합계 ÷ 모든 종목 합계 절댓값 합 × 100(부호 그대로, 절댓값 합이 0 이면 0)", example = "62.50")
    BigDecimal sharePercent,

    @Schema(description = "기간 끝에 들고 있음", example = "true")
    boolean held
  ) {
  }
}
