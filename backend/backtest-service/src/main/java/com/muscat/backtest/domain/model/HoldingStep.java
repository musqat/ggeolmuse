package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// 그 날부터의 보유 주식 수와 현금(USD)
public record HoldingStep(LocalDate from, BigDecimal shares, BigDecimal cashUsd) {
}
