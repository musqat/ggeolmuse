package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "시장 비교(매수마다 체결 금액 + 수수료만큼 SPY 를 사고 매도마다 줄어든 투자금액 ÷ 매도 직전 계좌 투자금액 "
  + "비율만큼 SPY 를 판 원화 수익, 종가만 · 배당 없음)")
public record MarketComparisonResponse(
  @Schema(description = "비교 종목", example = "SPY")
  String symbol,

  @Schema(description = "같은 계좌 · 기간의 수익 흐름 점과 같은 날짜마다 SPY 원화 수익(오래된 날짜부터, "
    + "SPY 종가나 점 날짜 환율을 못 받으면 빈 목록)")
  List<MarketPoint> points,

  @Schema(description = "마지막 점의 SPY 원화 수익(점이 없으면 0)", example = "98000")
  BigDecimal currentProfitKrw
) {

  public static final String SYMBOL = "SPY";

  public static MarketComparisonResponse empty() {
    return new MarketComparisonResponse(SYMBOL, List.of(), BigDecimal.ZERO);
  }

  @Schema(description = "거래일 하나의 SPY 원화 수익")
  public record MarketPoint(
    @Schema(description = "거래일", example = "2024-09-18")
    LocalDate date,

    @Schema(description = "(SPY 수량 × 그 날짜 종가 − SPY 원가 + SPY 실현 손익 누적) × 그 날짜 환율", example = "98000")
    BigDecimal profitKrw
  ) {
  }
}
