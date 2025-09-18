package com.muscat.trade.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingCapacityResponseDto {

    private String symbol;
    private LocalDate tradeDate;
    private BigDecimal currentPrice;
    private BigDecimal availableBalance;
    private BigDecimal maxShares;
    private BigDecimal totalValue;
    private String currency;

    // 매도용 추가 필드
    private BigDecimal currentHoldings;
    private BigDecimal maxSellableShares;
}