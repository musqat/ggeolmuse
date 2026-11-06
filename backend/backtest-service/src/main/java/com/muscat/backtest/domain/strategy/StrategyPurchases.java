package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 전략이 산 거래 · 사지 못한 회차와 기간 일봉 · 날마다 환율(수동 환율이면 빈 맵)
record StrategyPurchases(List<StrategyTransaction> transactions, List<SkippedRound> skippedRounds,
                         Map<LocalDate, OHLCPriceDto> priceMap, FxRates dailyFx) {
}
