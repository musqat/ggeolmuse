package com.muscat.backtest.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HoldingDto(
  String holdingId,
  String accountId,
  String symbol,
  BigDecimal totalQuantity,
  BigDecimal avgPurchasePrice,
  BigDecimal totalInvestedAmount,
  BigDecimal totalDividends,
  LocalDate lastDividendCalculated,
  LocalDateTime createdAt
) {

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
