package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.BacktestResponseCode;
import com.muscat.backtest.common.enums.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;

// 투자 전략 인터페이스
public interface InvestmentStrategy {

  // 투자 전략을 실행합니다
  StrategyResponse execute(StrategyRequest request);

  // 이 전략이 지원하는 전략 타입을 반환합니다
  StrategyType getStrategyType();

  // 요청 파라미터가 이 전략 실행에 유효한지 검증합니다
  default void validateRequest(StrategyRequest request) {
    if (request == null) {
      throw new BacktestException(BacktestResponseCode.STRATEGY_REQUEST_NULL);
    }
    if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
      throw new BacktestException(BacktestResponseCode.STRATEGY_SYMBOL_REQUIRED);
    }
    if (request.getStartDate() == null) {
      throw new BacktestException(BacktestResponseCode.STRATEGY_START_DATE_REQUIRED);
    }
    if (request.getEndDate() == null) {
      throw new BacktestException(BacktestResponseCode.STRATEGY_END_DATE_REQUIRED);
    }
    if (request.getStartDate().isAfter(request.getEndDate())) {
      throw new BacktestException(BacktestResponseCode.STRATEGY_DATE_RANGE_INVALID);
    }
    if (!getStrategyType().equals(request.getStrategyType())) {
      throw new BacktestException(BacktestResponseCode.STRATEGY_TYPE_MISMATCH);
    }
  }
}