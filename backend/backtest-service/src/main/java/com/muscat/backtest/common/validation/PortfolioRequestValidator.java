package com.muscat.backtest.common.validation;

import com.muscat.backtest.common.constants.BacktestConstants.Portfolio;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.PortfolioAllocation;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 포트폴리오 요청 검증(기간 · 종목 줄 · 금액 · 비율 · 익절 · 손절 · 적립식 칸)
 */
public final class PortfolioRequestValidator {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private PortfolioRequestValidator() {}

  /** 기간 → 종목 줄 → 금액 · 비율 → 줄마다 익절 · 손절 → 적립식 칸(적립식만) 차례로 검사 */
  public static void validate(PortfolioRequest request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    requireAssets(request.getAssets());
    requireAmounts(request);
    request.getAssets().forEach(asset ->
      BacktestRequestValidator.requireSellRates(asset.takeProfitRate(), asset.stopLossRate()));
    if (PortfolioMethod.orLumpSum(request.getMethod()) == PortfolioMethod.DCA) {
      requireSchedule(request);
    }
  }

  /** 1 ~ 10개 줄, 줄마다 종목 코드(비면 STRATEGY_SYMBOL_REQUIRED), 대문자로 겹치지 않고 지수가 아님 */
  private static void requireAssets(List<PortfolioAsset> assets) {
    if (assets == null || assets.isEmpty() || assets.size() > Portfolio.MAX_ASSETS
      || assets.stream().anyMatch(Objects::isNull)) {
      throw new BacktestException(BacktestResponse.INVALID_PORTFOLIO);
    }
    Set<String> seen = new HashSet<>();
    for (PortfolioAsset asset : assets) {
      String symbol = asset.normalizedSymbol();
      if (symbol == null || symbol.isEmpty()) {
        throw new BacktestException(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
      }
      if (BacktestRequestValidator.isIndex(symbol) || !seen.add(symbol)) {
        throw new BacktestException(BacktestResponse.INVALID_PORTFOLIO);
      }
    }
  }

  /** 금액 방식은 줄마다 금액 0 초과 · 비율 비움, 비율 방식은 총액 0 초과 · 줄마다 비율 0 초과 · 줄 금액 1원 이상 · 합 100(±0.01) */
  private static void requireAmounts(PortfolioRequest request) {
    List<PortfolioAsset> assets = request.getAssets();
    boolean valid = PortfolioAllocation.orAmount(request.getAllocation()) == PortfolioAllocation.AMOUNT
      ? assets.stream().allMatch(asset -> positive(asset.amount()) && asset.weightPercent() == null)
      : positive(request.getTotalAmount())
        && assets.stream().allMatch(asset -> positive(asset.weightPercent()) && positive(request.amountOf(asset)))
        && assets.stream().map(PortfolioAsset::weightPercent).reduce(BigDecimal.ZERO, BigDecimal::add)
          .subtract(HUNDRED).abs().compareTo(Portfolio.WEIGHT_TOLERANCE) <= 0;
    if (!valid) {
      throw new BacktestException(BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
    }
  }

  /** 투자일 1 ~ 31 · 투자 주기 1 ~ 12 · 첫 매수일 기간 안(비우면 통과) */
  private static void requireSchedule(PortfolioRequest request) {
    Integer day = request.getPurchaseDay();
    if (day != null && (day < 1 || day > 31)) {
      throw new BacktestException(BacktestResponse.INVALID_PURCHASE_DAY);
    }
    Integer interval = request.getInvestmentInterval();
    if (interval != null && (interval < 1 || interval > 12)) {
      throw new BacktestException(BacktestResponse.INVALID_INVESTMENT_INTERVAL);
    }
    BacktestRequestValidator.requireFirstPurchaseInPeriod(request.getFirstPurchaseDate(), request.getStartDate(),
      request.getEndDate());
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }
}
