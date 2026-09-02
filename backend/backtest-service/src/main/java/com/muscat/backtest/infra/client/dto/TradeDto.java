package com.muscat.backtest.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TradeDto {

  private String tradeId;
  private String accountId;
  private String symbol;
  private String tradeType; // BUY, SELL
  private BigDecimal quantity;
  private BigDecimal price;
  private BigDecimal totalAmount;
  private BigDecimal fee;
  private LocalDate tradeDate;
  private LocalDateTime executedAt;
}
