package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

@Schema(description = "거래 패턴 탭(기간 안 거래일의 완료 체결)")
public record TradingHabitsResponse(
  @Schema(description = "매수 체결 수", example = "12")
  int buyCount,

  @Schema(description = "매도 체결 수", example = "4")
  int sellCount,

  @Schema(description = "체결이 있는 서로 다른 거래일 수", example = "10")
  int tradingDays,

  @Schema(description = "매도마다 판 수량을 계좌 · 종목마다 먼저 산 매수부터 짝지은 수량 가중 평균 보유 일수"
    + "(판 거래일 − 산 거래일, 매도가 없거나 들고 있던 수량이 없는 매도뿐이면 null)", example = "42.5")
  BigDecimal averageHoldingDays,

  @Schema(description = "실현 손익(USD, 소수 둘째 반올림)이 0 보다 큰 매도 수", example = "3")
  int winCount,

  @Schema(description = "실현 손익(USD, 소수 둘째 반올림)이 0 이하인 매도 수", example = "1")
  int lossCount,

  @Schema(description = "수익 낸 매도 수 ÷ 매도 수 × 100(매도가 없으면 null)", example = "75.00")
  BigDecimal winRatePercent,

  @Schema(description = "거래일의 요일마다 체결 수(월요일부터 일요일까지 7개)")
  List<WeekdayTrades> weekdays,

  @Schema(description = "체결 수 많은 종목 5개(같으면 체결 금액 큰 것, 그다음 종목 이름 순)")
  List<SymbolTrades> topSymbols
) {

  @Schema(description = "요일 하나의 체결 수")
  public record WeekdayTrades(
    @Schema(description = "요일", example = "MONDAY")
    DayOfWeek weekday,

    @Schema(description = "체결 수", example = "3")
    int count
  ) {
  }

  @Schema(description = "종목 하나의 체결 수와 체결 금액")
  public record SymbolTrades(
    @Schema(description = "종목", example = "AAPL")
    String symbol,

    @Schema(description = "체결 수", example = "6")
    int tradeCount,

    @Schema(description = "매수 체결 수", example = "4")
    int buyCount,

    @Schema(description = "매도 체결 수", example = "2")
    int sellCount,

    @Schema(description = "수량 × 체결가 합(USD, 수수료 제외)", example = "2450.30")
    BigDecimal amountUsd
  ) {
  }
}
