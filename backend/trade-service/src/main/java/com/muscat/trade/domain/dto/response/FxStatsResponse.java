package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "환율 탭(환전한 원금의 환율 차, 수익 흐름에는 들지 않음)")
public record FxStatsResponse(
  @Schema(description = "환율 효과(기간 끝) − 환율 효과(기간 시작 전날), 효과는 그 날짜까지 환전마다 "
    + "환전 달러(판 달러는 음수) × (그 날짜 환율 − 환전 환율)의 합", example = "152000")
  BigDecimal fxEffectKrw,

  @Schema(description = "기간 끝 환율(없으면 null)", example = "1385.20")
  BigDecimal currentRate,

  @Schema(description = "환전 달러(판 달러는 음수) × 환전 환율 합 ÷ 환전 달러 합(달러 합이 0 이하면 null)",
    example = "1310.45")
  BigDecimal averageRate,

  @Schema(description = "기간 끝까지 원화→달러로 받은 달러 − 달러→원화로 판 달러(0 이하면 0)", example = "2034.50")
  BigDecimal heldUsd,

  @Schema(description = "기간 안 원화→달러 환전에 낸 원화 합", example = "3000000")
  BigDecimal krwToUsdKrw,

  @Schema(description = "기간 안 달러→원화 환전에 판 달러 합", example = "300.00")
  BigDecimal usdToKrwUsd,

  @Schema(description = "기간 안 환전 수", example = "4")
  int exchangeCount,

  @Schema(description = "기간 안 환전(최근부터)")
  List<Exchange> exchanges
) {

  public enum Direction {
    KRW_TO_USD,
    USD_TO_KRW
  }

  @Schema(description = "환전 한 건")
  public record Exchange(
    @Schema(description = "환전 날짜", example = "2024-09-02")
    LocalDate date,

    @Schema(description = "계좌 ID", example = "3")
    Long accountId,

    @Schema(description = "방향", example = "KRW_TO_USD")
    Direction direction,

    @Schema(description = "원화(원화→달러는 낸 원화, 달러→원화는 받은 원화)", example = "1300000")
    BigDecimal krwAmount,

    @Schema(description = "달러(원화→달러는 받은 달러, 달러→원화는 판 달러)", example = "1000.00")
    BigDecimal usdAmount,

    @Schema(description = "환전 환율", example = "1300.00")
    BigDecimal rate,

    @Schema(description = "환전 달러(판 달러는 음수) × (기간 끝 환율 − 환전 환율)", example = "85200")
    BigDecimal effectKrw
  ) {
  }
}
