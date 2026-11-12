package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Locale;

// 포트폴리오 종목 줄
@Schema(description = "포트폴리오 종목 줄")
public record PortfolioAsset(
  @Schema(description = "종목 코드 (지수는 안 됨, 대문자로 같은 종목은 한 번만)", example = "SPY", required = true)
  String symbol,

  @Schema(description = "금액 (AMOUNT 에서만, KRW, 적립식 줄은 회당 금액)", example = "6000000")
  BigDecimal amount,

  @Schema(description = "비율 (WEIGHT 에서만, %, 줄 합 100)", example = "60")
  BigDecimal weightPercent,

  @Schema(description = "익절 비율: 그 종목 평균 단가(수수료 포함)보다 장중에 이만큼 오르면 전량 매도 (10% = 0.1, 비우면 안 함)", example = "0.3")
  BigDecimal takeProfitRate,

  @Schema(description = "손절 비율: 그 종목 평균 단가(수수료 포함)보다 장중에 이만큼 내리면 전량 매도 (10% = 0.1, 1 미만, 비우면 안 함)", example = "0.1")
  BigDecimal stopLossRate,

  @Schema(description = "줄 전략 (비우면 요청의 매수 방식)")
  PortfolioStrategy strategy
) {

  // 앞뒤 공백을 뺀 대문자 종목 코드
  public String normalizedSymbol() {
    return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
  }
}
