package com.muscat.backtest.infra.client.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class HoldingDto {
    private String holdingId;
    private String accountId;
    private String symbol;
    private BigDecimal totalQuantity;
    private BigDecimal avgPurchasePrice;
    private BigDecimal totalInvestedAmount;
    private BigDecimal totalDividends;
    private LocalDate lastDividendCalculated;
    private LocalDateTime createdAt;
    
    // 백테스트에서 사용할 편의 메서드들
    public BigDecimal getShares() {
        return totalQuantity;
    }
    
    public BigDecimal getAveragePrice() {
        return avgPurchasePrice;
    }
    
    public BigDecimal getTotalInvested() {
        return totalInvestedAmount;
    }
    
    public LocalDate getPurchaseDate() {
        return createdAt != null ? createdAt.toLocalDate() : null;
    }
}