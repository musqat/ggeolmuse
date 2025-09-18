package com.muscat.backtest.infra.client.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TradeResponseDto {
    private Long tradeId;
    private String userId;
    private Long accountId;
    private String symbol;
    private String tradeType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private LocalDate tradeDate;
    private LocalDateTime createdAt;
}