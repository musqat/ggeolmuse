package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;

// 투자 전략(자기 타입의 요청만 받음)
public interface InvestmentStrategy {

  StrategyType getStrategyType();

  StrategyResponse execute(StrategyRequest request);
}
