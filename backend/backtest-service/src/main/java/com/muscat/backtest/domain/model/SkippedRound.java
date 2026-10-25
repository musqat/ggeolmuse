package com.muscat.backtest.domain.model;

import com.muscat.backtest.common.enums.type.SkipReason;
import java.time.LocalDate;

// 사지 못한 회차(계획된 매수일)와 이유
public record SkippedRound(LocalDate date, SkipReason reason) {
}
