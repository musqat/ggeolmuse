package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.util.List;

// 전략이 산 거래와 사지 못한 회차
record StrategyPurchases(List<StrategyTransaction> transactions, List<SkippedRound> skippedRounds) {
}
