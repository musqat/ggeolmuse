package com.muscat.trade.infra.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPriceDto {

  private String symbol;
  private BigDecimal currentPrice;
  private BigDecimal previousClose;
  private BigDecimal changePercent;
  private Long volume;
  private LocalDateTime lastUpdated;

  // OHLC 데이터 필드
  private LocalDate date;
  private BigDecimal openPrice;
  private BigDecimal highPrice;
  private BigDecimal lowPrice;
  private BigDecimal closePrice;
  private BigDecimal adjustedClose;
  private String currency;
  private Boolean available;
  private String assetType;
}