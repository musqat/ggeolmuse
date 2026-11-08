package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.SkippedSpan;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 전략이 산 거래 · 사지 못한 회차 · 예산 부족 구간(없으면 null)과 기간 일봉 · 날마다 환율(수동 환율이면 빈 맵)
record StrategyPurchases(List<StrategyTransaction> transactions, List<SkippedRound> skippedRounds,
                         Map<LocalDate, OHLCPriceDto> priceMap, FxRates dailyFx, SkippedSpan skippedSpan) {

  // 예산 부족 구간이 없는 전략
  StrategyPurchases(List<StrategyTransaction> transactions, List<SkippedRound> skippedRounds,
                    Map<LocalDate, OHLCPriceDto> priceMap, FxRates dailyFx) {
    this(transactions, skippedRounds, priceMap, dailyFx, null);
  }
}
