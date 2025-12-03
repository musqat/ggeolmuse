package com.muscat.backtest.domain.model;

// 매수 결정 정보
public record PurchaseDecision(
    boolean shouldBuy,
    String trigger
) {
}
