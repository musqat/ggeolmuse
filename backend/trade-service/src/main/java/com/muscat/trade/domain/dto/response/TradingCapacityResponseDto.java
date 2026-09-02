package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "거래 가능 수량 정보")
public record TradingCapacityResponseDto(
  @Schema(description = "종목 심볼", example = "AAPL")
  String symbol,

  @Schema(description = "거래 날짜", example = "2024-09-18")
  LocalDate tradeDate,

  @Schema(description = "현재가", example = "165.75")
  BigDecimal currentPrice,

  @Schema(description = "사용 가능 잔고", example = "50000.00")
  BigDecimal availableBalance,

  @Schema(description = "최대 매수 가능 주식 수", example = "301.65")
  BigDecimal maxShares,

  @Schema(description = "총 투자 가능 금액", example = "49998.75")
  BigDecimal totalValue,

  @Schema(description = "통화", example = "USD")
  String currency,

  // 매도용 추가 필드
  @Schema(description = "현재 보유 주식 수", example = "100.00")
  BigDecimal currentHoldings,

  @Schema(description = "최대 매도 가능 주식 수", example = "100.00")
  BigDecimal maxSellableShares
) {

}
