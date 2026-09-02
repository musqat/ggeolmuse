package com.muscat.backtest.domain.model;

import java.math.BigDecimal;

//투자 파라미터
public record InvestmentParams(
    BigDecimal amountPerPurchase,
    int maxPurchases
) {
}
