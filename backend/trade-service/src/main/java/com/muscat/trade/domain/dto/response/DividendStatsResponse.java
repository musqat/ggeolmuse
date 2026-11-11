package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Schema(description = "배당 탭(배당락일이 기간 안인 배당, 원화는 배당락일 환율)")
public record DividendStatsResponse(
  @Schema(description = "세후 원화 합", example = "85000")
  BigDecimal netKrw,

  @Schema(description = "세후 USD 합", example = "63.20")
  BigDecimal netUsd,

  @Schema(description = "세전 USD 합", example = "74.35")
  BigDecimal grossUsd,

  @Schema(description = "원천징수 세금 USD 합", example = "11.15")
  BigDecimal taxUsd,

  @Schema(description = "배당 건수", example = "7")
  int payoutCount,

  @Schema(description = "배당이 있는 달마다 합(오래된 달부터)")
  List<MonthlyDividend> monthly,

  @Schema(description = "종목마다 합(세후 원화 큰 것부터)")
  List<SymbolDividend> symbols,

  @Schema(description = "배당 내역(최근 배당락일부터)")
  List<Payout> payouts
) {

  @Schema(description = "달 하나의 배당 합")
  public record MonthlyDividend(
    @Schema(description = "배당락일의 달", type = "string", example = "2024-09")
    YearMonth month,

    @Schema(description = "세후 USD 합", example = "12.40")
    BigDecimal netUsd,

    @Schema(description = "세후 원화 합", example = "16500")
    BigDecimal netKrw
  ) {
  }

  @Schema(description = "종목 하나의 배당 합")
  public record SymbolDividend(
    @Schema(description = "종목", example = "KO")
    String symbol,

    @Schema(description = "세후 USD 합", example = "30.10")
    BigDecimal netUsd,

    @Schema(description = "세후 원화 합", example = "40100")
    BigDecimal netKrw,

    @Schema(description = "배당 건수", example = "3")
    int payoutCount,

    @Schema(description = "세후 원화 ÷ 전체 세후 원화 × 100(전체가 0 이면 0)", example = "47.18")
    BigDecimal sharePercent
  ) {
  }

  @Schema(description = "계좌 · 종목 · 배당락일 하나의 배당")
  public record Payout(
    @Schema(description = "배당락일", example = "2024-09-13")
    LocalDate exDate,

    @Schema(description = "계좌 ID", example = "3")
    Long accountId,

    @Schema(description = "종목", example = "KO")
    String symbol,

    @Schema(description = "배당락일 전날 들고 있던 수량", example = "10.000000")
    BigDecimal shares,

    @Schema(description = "주당 배당(USD)", example = "0.485000")
    BigDecimal dividendPerShareUsd,

    @Schema(description = "세전 배당(USD)", example = "4.85")
    BigDecimal grossUsd,

    @Schema(description = "원천징수 세금(USD)", example = "0.73")
    BigDecimal taxUsd,

    @Schema(description = "세후 배당(USD)", example = "4.12")
    BigDecimal netUsd,

    @Schema(description = "배당락일 이하 가장 늦은 환율(없으면 null)", example = "1330.50")
    BigDecimal fxRate,

    @Schema(description = "세후 배당을 배당락일 환율로 바꾼 원화(환율이 없으면 0)", example = "5482")
    BigDecimal netKrw
  ) {
  }
}
