package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Locale;

// 포트폴리오 종목 줄
@Schema(description = "포트폴리오 종목 줄")
public record PortfolioAsset(
  @Schema(description = "종목 코드 (지수는 안 됨, 대문자로 같은 종목은 한 번만)", example = "SPY", required = true)
  String symbol,

  @Schema(description = "금액 (AMOUNT 에서만, KRW, 적립식이면 회당 금액)", example = "6000000")
  BigDecimal amount,

  @Schema(description = "비율 (WEIGHT 에서만, %, 줄 합 100)", example = "60")
  BigDecimal weightPercent
) {

  // 앞뒤 공백을 뺀 대문자 종목 코드
  public String normalizedSymbol() {
    return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
  }
}
