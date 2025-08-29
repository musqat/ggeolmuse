package com.muscat.trade.domain.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class DividendRecord {
    private String userId;
    private String accountId;
    private String symbol;
    private BigDecimal dividendPerShare;
    private BigDecimal totalQuantity;
    private BigDecimal totalDividend;
    private LocalDate dividendDate;
    private LocalDate processedAt;
    
    public DividendRecord(String userId, String accountId, String symbol, 
                         BigDecimal dividendPerShare, BigDecimal totalQuantity,
                         BigDecimal totalDividend, LocalDate dividendDate, LocalDate processedAt) {
        this.userId = userId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.dividendPerShare = dividendPerShare;
        this.totalQuantity = totalQuantity;
        this.totalDividend = totalDividend;
        this.dividendDate = dividendDate;
        this.processedAt = processedAt;
    }
}