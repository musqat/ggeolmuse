package com.muscat.backtest.domain.strategy;

import java.time.LocalDate;

// 다음 거래일 시가에 체결할 신호(신호 난 날 · 매수인지)
public record SignalOrder(LocalDate signalDate, boolean buy) {}
