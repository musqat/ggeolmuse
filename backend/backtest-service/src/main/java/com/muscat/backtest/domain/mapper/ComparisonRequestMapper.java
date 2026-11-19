package com.muscat.backtest.domain.mapper;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.model.StrategyParameter;
import java.math.BigDecimal;

// 비교 요청 만들기: 종목 비교 · 전략 비교 요청의 기간 · 금액 · 공통 옵션과 칩 값으로 단순 시뮬레이션 · 전략 요청을 지음
public final class ComparisonRequestMapper {

  private ComparisonRequestMapper() {}

  // 종목 비교의 한 종목: 시작일에 사서 종료일에 파는 단순 시뮬레이션 요청
  public static SimulationRequest symbolSimulation(SymbolComparisonRequest request, String symbol) {
    return simulationWithOptions(request)
      .symbol(symbol)
      .purchaseDate(request.getStartDate())
      .build();
  }

  // 전략 비교의 단순 칩: 비교 종목을 칩의 매수일에 사는 단순 시뮬레이션 요청
  public static SimulationRequest simpleSimulation(StrategyComparisonRequest request, StrategyParameter strategy) {
    return simulationWithOptions(request)
      .symbol(request.getSymbol())
      .purchaseDate(strategy.getPurchaseDate())
      .build();
  }

  // 비교 요청의 기간 · 금액 · 공통 옵션과 전략 칸으로 만든 전략 요청(비교할 수 없는 전략이면 STRATEGY_TYPE_MISMATCH)
  public static StrategyRequest strategyRequest(StrategyComparisonRequest request, StrategyParameter strategy) {
    StrategyType strategyType = strategy.getStrategyType();
    if (strategyType == StrategyType.DCA) {
      return dca(request, strategy);
    }
    if (strategyType == StrategyType.CONDITIONAL_PURCHASE) {
      return conditional(request, strategy);
    }
    if (strategyType == StrategyType.MA_CROSS) {
      return maCross(request, strategy);
    }
    if (strategyType == StrategyType.MA_BAND) {
      return maBand(request, strategy);
    }
    throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH);
  }

  // 비교 요청의 매도일(종료일) · 투자금 · 수수료 · 환율 · 배당 · 양도세 · 기간 끝 옵션으로 채운 단순 시뮬레이션 요청
  private static SimulationRequest.SimulationRequestBuilder<?, ?> simulationWithOptions(BaseComparisonRequest request) {
    return SimulationRequest.builder()
      .saleDate(request.getEndDate())
      .investmentAmount(request.getInvestmentAmount())
      .userId(request.getUserId())
      .tradingFeeRate(orZero(request.getTradingFeeRate()))
      .purchaseFxRate(request.getPurchaseFxRate())
      .currentFxRate(request.getCurrentFxRate())
      .reinvestDividends(reinvests(request))
      .dividendTaxRate(orZero(request.getDividendTaxRate()))
      .capitalGainsTax(request.getCapitalGainsTax())
      .periodEnd(request.getPeriodEnd());
  }

  // 비교할 적립식 요청(예산은 비교 투자 금액)
  private static DcaStrategyRequest dca(StrategyComparisonRequest request, StrategyParameter strategy) {
    return DcaStrategyRequest.builder()
      .symbol(request.getSymbol())
      .startDate(request.getStartDate())
      .endDate(request.getEndDate())
      .userId(request.getUserId())
      .amountPerPurchase(strategy.getAmountPerPurchase())
      .frequency(strategy.getFrequency())
      .firstPurchaseDate(strategy.getFirstPurchaseDate())
      .purchaseDay(strategy.getPurchaseDay())
      .investmentInterval(strategy.getInvestmentInterval() != null ? strategy.getInvestmentInterval() : 1)
      .totalInvestmentLimit(request.getInvestmentAmount())
      .purchaseFxRate(request.getPurchaseFxRate())
      .currentFxRate(request.getCurrentFxRate())
      .reinvestDividends(reinvests(request))
      .dividendTaxRate(orZero(request.getDividendTaxRate()))
      .tradingFeeRate(orZero(request.getTradingFeeRate()))
      .capitalGainsTax(request.getCapitalGainsTax())
      .periodEnd(request.getPeriodEnd())
      .build();
  }

  // 비교할 조건부 매수 요청(예산은 비교 투자 금액)
  private static ConditionalStrategyRequest conditional(StrategyComparisonRequest request,
                                                        StrategyParameter strategy) {
    boolean sells = strategy.getTakeProfitRate() != null || strategy.getStopLossRate() != null;
    return ConditionalStrategyRequest.builder()
      .symbol(request.getSymbol())
      .startDate(request.getStartDate())
      .endDate(request.getEndDate())
      .userId(request.getUserId())
      .amountPerPurchase(strategy.getAmountPerPurchase())
      .totalInvestment(request.getInvestmentAmount())
      .dropPercentage(strategy.getDropPercentage())
      .direction(strategy.getDirection())
      .maxPurchases(strategy.getMaxPurchases())
      .takeProfitRate(strategy.getTakeProfitRate())
      .stopLossRate(strategy.getStopLossRate())
      .purchaseFxRate(request.getPurchaseFxRate())
      .currentFxRate(request.getCurrentFxRate())
      // 익절 · 손절을 켠 칩만 재투자 끔
      .reinvestDividends(!sells && reinvests(request))
      .dividendTaxRate(orZero(request.getDividendTaxRate()))
      .tradingFeeRate(orZero(request.getTradingFeeRate()))
      .capitalGainsTax(request.getCapitalGainsTax())
      .periodEnd(request.getPeriodEnd())
      .build();
  }

  // 비교할 이동평균 교차 요청(원금은 비교 투자 금액)
  private static MaCrossStrategyRequest maCross(StrategyComparisonRequest request, StrategyParameter strategy) {
    return MaCrossStrategyRequest.builder()
      .symbol(request.getSymbol())
      .startDate(request.getStartDate())
      .endDate(request.getEndDate())
      .userId(request.getUserId())
      .investmentAmount(request.getInvestmentAmount())
      .shortWindow(strategy.getShortWindow())
      .longWindow(strategy.getLongWindow())
      .purchaseFxRate(request.getPurchaseFxRate())
      .currentFxRate(request.getCurrentFxRate())
      // 교차마다 전량 팔아 재투자 끔
      .reinvestDividends(false)
      .dividendTaxRate(orZero(request.getDividendTaxRate()))
      .tradingFeeRate(orZero(request.getTradingFeeRate()))
      .capitalGainsTax(request.getCapitalGainsTax())
      .periodEnd(request.getPeriodEnd())
      .build();
  }

  // 비교할 이동평균 밴드 요청(원금은 비교 투자 금액)
  private static MaBandStrategyRequest maBand(StrategyComparisonRequest request, StrategyParameter strategy) {
    return MaBandStrategyRequest.builder()
      .symbol(request.getSymbol())
      .startDate(request.getStartDate())
      .endDate(request.getEndDate())
      .userId(request.getUserId())
      .investmentAmount(request.getInvestmentAmount())
      .bandWindow(strategy.getBandWindow())
      .upperRate(strategy.getUpperRate())
      .lowerRate(strategy.getLowerRate())
      .purchaseFxRate(request.getPurchaseFxRate())
      .currentFxRate(request.getCurrentFxRate())
      // 밴드 아래에서 전량 팔아 재투자 끔
      .reinvestDividends(false)
      .dividendTaxRate(orZero(request.getDividendTaxRate()))
      .tradingFeeRate(orZero(request.getTradingFeeRate()))
      .capitalGainsTax(request.getCapitalGainsTax())
      .periodEnd(request.getPeriodEnd())
      .build();
  }

  // 빈 값은 재투자 안 함
  private static boolean reinvests(BaseComparisonRequest request) {
    return Boolean.TRUE.equals(request.getReinvestDividends());
  }

  // 빈 값은 0(builder 에 null 을 넣으면 @Builder.Default 가 덮임)
  private static BigDecimal orZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
