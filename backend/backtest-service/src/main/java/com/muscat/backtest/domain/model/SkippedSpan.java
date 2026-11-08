package com.muscat.backtest.domain.model;

import com.muscat.backtest.common.enums.type.SkipReason;
import java.time.LocalDate;

// 처음 사지 못한 날짜부터 이어진 미매수 회차 수와 이유
public record SkippedSpan(LocalDate firstDate, int rounds, SkipReason reason) {
}
