package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Schema(description = "수익 탭 자세히(달마다 수익 · 판 거래 · 들고 있는 종목)")
public record ProfitDetailResponse(
  @Schema(description = "기간 안 수익 흐름 점이 있는 달마다 늘어난 원화 수익(오래된 달부터)")
  List<MonthlyProfit> monthly,

  @Schema(description = "판 거래 실현 손익 원화 합", example = "46834")
  BigDecimal realizedKrw,

  @Schema(description = "들고 있는 종목 평가 손익 원화 합", example = "190680")
  BigDecimal unrealizedKrw,

  @Schema(description = "기간 안 거래일의 매도(최근 날짜부터, 같은 날짜는 체결 ID 큰 것부터)")
  List<Sale> sales,

  @Schema(description = "기간 끝(기간 안 마지막 수익 흐름 점 날짜, 없으면 오늘)에 들고 있는 종목(평가 손익 원화 큰 것부터, 전체 계좌는 종목마다 합침)")
  List<Holding> holdings
) {

  @Schema(description = "달 하나의 수익 변화")
  public record MonthlyProfit(
    @Schema(description = "달", type = "string", example = "2024-09")
    YearMonth month,

    @Schema(description = "그 달 마지막 점 수익 − 앞 달 마지막 점 수익(첫 달은 기간 시작 전 마지막 점, 없으면 0)",
      example = "120000")
    BigDecimal profitKrw
  ) {
  }

  @Schema(description = "매도 하나의 실현 손익")
  public record Sale(
    @Schema(description = "체결 ID", example = "41")
    Long tradeId,

    @Schema(description = "계좌 ID", example = "3")
    Long accountId,

    @Schema(description = "거래일", example = "2024-09-18")
    LocalDate date,

    @Schema(description = "종목", example = "AAPL")
    String symbol,

    @Schema(description = "판 수량", example = "2.000000")
    BigDecimal quantity,

    @Schema(description = "체결가(USD)", example = "220.15")
    BigDecimal priceUsd,

    @Schema(description = "판 수량 × 체결가 − 줄어든 투자금액(USD)", example = "35.20")
    BigDecimal realizedUsd,

    @Schema(description = "거래일 이하 가장 늦은 환율(없으면 null)", example = "1330.50")
    BigDecimal fxRate,

    @Schema(description = "실현 손익을 거래일 환율로 바꾼 원화(환율이 없으면 0)", example = "46834")
    BigDecimal realizedKrw,

    @Schema(description = "실현 손익 ÷ 줄어든 투자금액 × 100", example = "8.69")
    BigDecimal returnPercent
  ) {
  }

  @Schema(description = "들고 있는 종목 하나의 평가 손익")
  public record Holding(
    @Schema(description = "종목", example = "AAPL")
    String symbol,

    @Schema(description = "들고 있는 수량", example = "3.000000")
    BigDecimal quantity,

    @Schema(description = "투자금액 ÷ 수량(USD)", example = "180.00")
    BigDecimal averagePriceUsd,

    @Schema(description = "기간 끝 이하 가장 늦은 종가(USD, 못 받으면 null)", example = "225.40")
    BigDecimal closeUsd,

    @Schema(description = "종가 날짜(못 받으면 null)", example = "2024-11-08")
    LocalDate closeDate,

    @Schema(description = "수량 × 종가 − 투자금액(USD, 종가가 없으면 −투자금액)", example = "136.20")
    BigDecimal unrealizedUsd,

    @Schema(description = "평가 손익을 기간 끝 환율로 바꾼 원화", example = "190680")
    BigDecimal unrealizedKrw,

    @Schema(description = "평가 손익 ÷ 투자금액 × 100", example = "25.22")
    BigDecimal returnPercent
  ) {
  }
}
