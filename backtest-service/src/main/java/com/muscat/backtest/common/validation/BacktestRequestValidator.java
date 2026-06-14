package com.muscat.backtest.common.validation;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import java.time.LocalDate;

/**
 * 백테스트 전략 요청의 공통 검증. DCA/조건부 등이 중복하던 null/심볼/기간 검사를 단일화
 */
public final class BacktestRequestValidator {

  private BacktestRequestValidator() {}

  /** 요청 객체 null 검사 */
  public static void requireNonNull(Object request) {
    if (request == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_REQUEST_NULL);
    }
  }

  /** 심볼 null/공백 검사 */
  public static void requireSymbol(String symbol) {
    if (symbol == null || symbol.trim().isEmpty()) {
      throw new BacktestException(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }
  }

  /** 시작일/종료일 null + 역전 검사 */
  public static void requireDateRange(LocalDate startDate, LocalDate endDate) {
    if (startDate == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    }
    if (endDate == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    }
    if (startDate.isAfter(endDate)) {
      throw new BacktestException(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }
  }
}
