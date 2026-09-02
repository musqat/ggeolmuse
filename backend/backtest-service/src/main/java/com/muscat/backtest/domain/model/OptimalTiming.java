package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// 최적 매매 타이밍 정보
public record OptimalTiming(
    LocalDate buyDate,
    BigDecimal buyPrice,
    LocalDate sellDate,
    BigDecimal sellPrice,
    BigDecimal returnPercent
) {
    public static OptimalTiming empty() {
        return new OptimalTiming(null, null, null, null, null);
    }
}
