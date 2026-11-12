package com.muscat.backtest.common.validation;

import com.muscat.backtest.common.constants.BacktestConstants.RequestLimit;
import com.muscat.backtest.common.constants.BacktestConstants.SellRule;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.PartialTakeProfit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 백테스트 전략 요청의 공통 검증(null · 심볼 · 지수 · 기간 · 주식 수 · 트레일링 스탑 · 부분 익절 · 첫 매수일)
 */
public final class BacktestRequestValidator {

  // 지수 심볼 앞말(^GSPC · ^NDX)
  private static final String INDEX_PREFIX = "^";

  private BacktestRequestValidator() {}

  /** 요청 객체 null 검사 */
  public static void requireNonNull(Object request) {
    if (request == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_REQUEST_NULL);
    }
  }

  /** 심볼 null/공백 · 지수 검사 */
  public static void requireSymbol(String symbol) {
    if (symbol == null || symbol.trim().isEmpty()) {
      throw new BacktestException(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }
    requireTradable(symbol);
  }

  /** 지수 심볼 검사(비우면 통과) */
  public static void requireTradable(String symbol) {
    if (isIndex(symbol)) {
      throw new BacktestException(BacktestResponse.INDEX_NOT_TRADABLE);
    }
  }

  /** 지수 심볼인지(비우면 아님) */
  public static boolean isIndex(String symbol) {
    return symbol != null && symbol.trim().startsWith(INDEX_PREFIX);
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

  /** 주식 수 1 ~ 상한 검사(비우면 통과) */
  public static void requireShares(Integer shares) {
    if (shares != null && (shares < 1 || shares > RequestLimit.MAX_SHARES)) {
      throw new BacktestException(BacktestResponse.INVALID_SHARES_PER_PURCHASE);
    }
  }

  /** 트레일링 스탑 하락 비율 0 초과 1 미만 · 팔 비율 0 초과 1 이하(하락 비율과 함께) 검사(둘 다 비우면 통과) */
  public static void requireTrailingStop(BigDecimal stopRate, BigDecimal sellRate) {
    boolean stopOutside = stopRate != null && (stopRate.signum() <= 0 || stopRate.compareTo(BigDecimal.ONE) >= 0);
    boolean sellOutside = sellRate != null
      && (stopRate == null || sellRate.signum() <= 0 || sellRate.compareTo(BigDecimal.ONE) > 0);
    if (stopOutside || sellOutside) {
      throw new BacktestException(BacktestResponse.INVALID_TRAILING_STOP);
    }
  }

  /** 부분 익절 단계 수 · 수익률 0 초과 오름차순 · 팔 비율 0 초과 1 이하 검사(비우면 통과) */
  public static void requirePartialTakeProfits(List<PartialTakeProfit> steps) {
    if (steps == null) {
      return;
    }
    boolean outside = steps.size() > SellRule.MAX_PARTIAL_TAKE_PROFITS
      || steps.stream().anyMatch(step -> step == null || step.gainRate() == null || step.sellRate() == null
        || step.gainRate().signum() <= 0 || step.sellRate().signum() <= 0 || step.sellRate().compareTo(BigDecimal.ONE) > 0)
      || !PartialTakeProfit.ascending(steps);
    if (outside) {
      throw new BacktestException(BacktestResponse.INVALID_PARTIAL_TAKE_PROFIT);
    }
  }

  /** 첫 매수일 시작일 ~ 종료일 검사(비우면 통과) */
  public static void requireFirstPurchaseInPeriod(LocalDate firstPurchaseDate, LocalDate startDate,
    LocalDate endDate) {
    if (firstPurchaseDate != null
      && (firstPurchaseDate.isBefore(startDate) || firstPurchaseDate.isAfter(endDate))) {
      throw new BacktestException(BacktestResponse.INVALID_FIRST_PURCHASE_DATE);
    }
  }
}
