package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;

// 투자 전략 인터페이스
public interface InvestmentStrategy {

  // DCA 전략을 실행합니다
  default StrategyResponse executeDca(DcaStrategyRequest request) {
    throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
        "이 전략은 DCA 요청을 지원하지 않습니다");
  }

  // 조건부 매수 전략을 실행합니다
  default StrategyResponse executeConditional(ConditionalStrategyRequest request) {
    throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
        "이 전략은 조건부 매수 요청을 지원하지 않습니다");
  }

  // 이 전략이 지원하는 전략 타입을 반환합니다
  StrategyType getStrategyType();

}