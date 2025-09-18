package com.muscat.backtest.infra.client.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TradeRequestDto {
    private String symbol;
    private BigDecimal quantity;
    private LocalDate tradeDate;
    private String priceType;
    private BigDecimal manualPrice;
}