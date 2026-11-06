package com.muscat.backtest.domain.model;

import java.math.BigDecimal;

// 매수 결정 정보
public record PurchaseDecision(
    boolean shouldBuy,
    BigDecimal price,
    String trigger
) {
}
