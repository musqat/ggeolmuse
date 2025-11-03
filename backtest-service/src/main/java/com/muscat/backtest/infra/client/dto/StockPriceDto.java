package com.muscat.backtest.infra.client.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockPriceDto {
    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal previousClose;
    private BigDecimal change;
    private BigDecimal changePercent;
    private LocalDateTime lastUpdated;
    private boolean available;
    private String assetType;
}