package com.muscat.trade.domain.dto.response;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "거래 내역 정보")
public record TradeResponseDto(
  @Schema(description = "거래 ID", example = "1")
  Long id, // 거래 ID

  @Schema(description = "계좌 ID", example = "12345678")
  Long accountId, // 계좌 ID

  @Schema(description = "종목 심볼", example = "AAPL")
  String symbol, // 종목 심볼

  @Schema(description = "거래 유형", example = "BUY", allowableValues = {"BUY", "SELL"})
  TradeType tradeType, // 거래 유형 (BUY/SELL)

  @Schema(description = "거래 수량", example = "50.00")
  BigDecimal quantity, // 거래 수량

  @Schema(description = "체결가", example = "165.75")
  BigDecimal price, // 체결가

  @Schema(description = "총 거래금액", example = "8287.50")
  BigDecimal totalAmount, // 총 거래금액

  @Schema(description = "수수료", example = "4.14")
  BigDecimal fee, // 수수료

  @Schema(description = "거래일", example = "2024-09-18")
  LocalDate tradeDate, // 거래일

  @Schema(description = "실행일시", example = "2024-09-18T14:30:00")
  LocalDateTime executedAt // 실행일시
) {

  // Entity to DTO 변환
  public static TradeResponseDto from(Trade trade) {
    return new TradeResponseDto(
      trade.getId(),
      trade.getAccountId(),
      trade.getSymbol(),
      trade.getTradeType(),
      trade.getQuantity(),
      trade.getPrice(),
      trade.getTotalAmount(),
      trade.getFee(),
      trade.getTradeDate(),
      trade.getExecutedAt()
    );
  }
}
