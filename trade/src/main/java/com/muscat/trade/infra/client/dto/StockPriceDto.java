package com.muscat.trade.infra.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class StockPriceDto {
  
  private String symbol;
  private BigDecimal currentPrice;
  private BigDecimal previousClose;
  private BigDecimal changePercent;
  private Long volume;
  private LocalDateTime lastUpdated;
}