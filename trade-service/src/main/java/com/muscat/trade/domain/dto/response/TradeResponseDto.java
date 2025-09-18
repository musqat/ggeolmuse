package com.muscat.trade.domain.dto.response;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class TradeResponseDto {

  private String tradeId; // 거래 ID
  private String accountId; // 계좌 ID
  private String symbol; // 종목 심볼
  private TradeType tradeType; // 거래 유형 (BUY/SELL)
  private BigDecimal quantity; // 거래 수량
  private BigDecimal price; // 체결가
  private BigDecimal totalAmount; // 총 거래금액
  private BigDecimal fee; // 수수료
  private LocalDate tradeDate; // 거래일
  private LocalDateTime executedAt; // 실행일시

  // Entity to DTO 변환
  public static TradeResponseDto from(Trade trade) {
    return TradeResponseDto.builder()
        .tradeId(trade.getTradeId())
        .accountId(String.valueOf(trade.getAccountId()))
        .symbol(trade.getSymbol())
        .tradeType(trade.getTradeType())
        .quantity(trade.getQuantity())
        .price(trade.getPrice())
        .totalAmount(trade.getTotalAmount())
        .fee(trade.getFee())
        .tradeDate(trade.getTradeDate())
        .executedAt(trade.getExecutedAt())
        .build();
  }
}