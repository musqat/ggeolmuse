package com.muscat.backtest.infra.client.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OHLCPriceDto {
    private String symbol;
    private LocalDate date;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal adjustedClose;  // 배당/주식분할 반영된 보정 종가
    private Long volume;
    private boolean available;
}