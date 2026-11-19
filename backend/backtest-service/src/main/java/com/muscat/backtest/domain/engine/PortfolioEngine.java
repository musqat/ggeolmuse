package com.muscat.backtest.domain.engine;

import com.muscat.backtest.domain.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.ExecutionTiming;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.service.BacktestDataUtils;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.PortfolioStrategy;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.domain.strategy.DcaSchedule;
import com.muscat.backtest.domain.strategy.SignalHolding;
import com.muscat.backtest.domain.strategy.TakeProfitStopLoss;
import com.muscat.backtest.domain.port.MarketDataPort;
import com.muscat.backtest.domain.dto.market.DividendHistoryDto;
import com.muscat.backtest.domain.dto.market.DividendHistoryDto.DividendPayment;
import com.muscat.backtest.domain.dto.market.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 포트폴리오 엔진: 여러 종목을 현금 하나(USD)로 날마다 신호 줄 시가 매도 → 양도세 납부(모자라면 판매 방식대로 매도) → 배당 → 신호 줄 시가 매수 → 익절 · 손절 → 이동평균 · 밴드 줄 신호 매매 → 회차 매수 → 리밸런싱 → 연말 정리 → 기간 끝 매도 → 평가 순서로 돌림
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioEngine {

  private final MarketDataPort marketData;

  /** 받은 세후 배당(USD)과 배당락일 환율 */
  public record Dividend(String symbol, LocalDate exDate, BigDecimal amountUsd, BigDecimal fxRate) {}

  /** 요청 순서 줄마다 전략 · 체결 시점(이동평균 · 밴드 줄만) · 줄 금액 · 평가일 보유 주식 수 · 종가(USD) · 평가 원화 */
  public record Position(String symbol, PortfolioStrategyType strategyType, ExecutionTiming executionTiming,
                         BigDecimal plannedKrw, BigDecimal shares, BigDecimal closeUsd, BigDecimal valueKrw) {}

  /** 기간 첫 거래일 · 평가일 · 거래 · 배당 · 줄마다 보유 · 합계 · 일별 가치와 지표 · 양도세 납부 · 기본 환율 날 · 배당 못 받은 종목 */
  public record Result(LocalDate startDate, LocalDate valuationDate, List<StrategyTransaction> transactions,
                       List<Dividend> dividends, List<Position> positions, BigDecimal totalInvestedKrw,
                       BigDecimal cashUsd, BigDecimal cashKrw, BigDecimal stockValueKrw, BigDecimal totalAssetKrw,
                       BigDecimal currentFxRate, StrategyPerformance.Result performance,
                       CapitalGainsTaxCalculator.Payments taxPayments, List<LocalDate> defaultFxDates,
                       List<String> dividendUnavailableSymbols) {}

  // 줄마다 일봉 · 회차 · 배당을 받아 줄들의 쓸 수 있는 일봉 날짜를 합친 날마다 돌리고 평가일 환율로 평가
  public Result run(PortfolioRequest request) {
    LocalDate today = LocalDate.now();
    LocalDate valuationDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();
    log.info("포트폴리오 실행 시작: {}개 종목 · {} · {} ~ {}", request.getAssets().size(),
      PortfolioMethod.orLumpSum(request.getMethod()), request.getStartDate(), valuationDate);

    List<PortfolioRun.Line> lines = request.getAssets().stream().map(asset -> line(request, asset, valuationDate)).toList();
    NavigableSet<LocalDate> dates = PortfolioRun.dates(lines);
    FxRates fxRates = request.getPurchaseFxRate() == null
      ? BacktestDataUtils.buildDailyFxRateMap(marketData, dates.first(), valuationDate)
      : FxRates.EMPTY;
    FxRateDto currentFx = request.getCurrentFxRate() != null
      ? new FxRateDto(valuationDate, request.getCurrentFxRate())
      : BacktestDataUtils.getFxRateAt(marketData, valuationDate);

    Set<LocalDate> harvestDates = YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest())
      ? YearEndHarvest.dates(dates, request.getStartDate(), valuationDate)
      : Set.of();
    PortfolioRun run = new PortfolioRun(request, lines, fxRates, valuationDate, harvestDates);
    dates.forEach(run::day);
    // 마지막 일봉 뒤 평가일까지 배당락일이 온 배당은 현금으로
    run.receiveDividends(valuationDate, false, Map.of());
    Result result = run.finish(dates.first(), currentFx);
    log.info("포트폴리오 실행 완료: 거래 {}건, 넣은 돈 {}원, 총 자산 {}원", result.transactions().size(),
      result.totalInvestedKrw(), result.totalAssetKrw());
    return result;
  }

  // 줄의 전략 · 기간 안 쓸 수 있는 일봉 · 매수 회차 · 배당락일 순서 배당 · 익절 · 손절 · 신호(일봉이 없으면 DATA_NOT_FOUND)
  private PortfolioRun.Line line(PortfolioRequest request, PortfolioAsset asset, LocalDate valuationDate) {
    String symbol = asset.normalizedSymbol();
    PortfolioStrategyType type = request.strategyOf(asset);
    LocalDate start = request.getStartDate();
    int window = signalWindow(type, asset.strategy());
    Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(marketData, symbol,
      window > 0 ? start.minusDays(SignalHolding.lookbackDays(window)) : start, valuationDate);
    NavigableMap<LocalDate, OHLCPriceDto> candles = new TreeMap<>();
    priceMap.values().stream()
      .filter(PriceLookup::isUsable)
      .filter(candle -> !candle.date().isBefore(start))
      .forEach(candle -> candles.put(candle.date(), candle));
    if (candles.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, symbol + " 의 기간 안 시세가 없습니다");
    }
    DividendHistoryDto history = BacktestDataUtils.getDividendHistory(marketData, symbol, request.getStartDate(),
      valuationDate);
    List<DividendPayment> dividends = history.getDividends().stream()
      .filter(dividend -> dividend.getExDate() != null)
      .sorted(Comparator.comparing(DividendPayment::getExDate))
      .toList();
    SignalHolding signal = signal(type, asset.strategy(), priceMap, start);
    boolean nextOpen = signal != null && ExecutionTiming.nextDayOpen(asset.strategy().executionTiming());
    return new PortfolioRun.Line(symbol, type, request.amountOf(asset), candles,
      signal == null ? rounds(request, type, candles, valuationDate) : Map.of(), dividends, history.isUnavailable(),
      TakeProfitStopLoss.of(asset.takeProfitRate(), asset.stopLossRate()), signal, nextOpen, asset.taxSaleOrder());
  }

  // 신호 줄의 신호 기간(이동평균 교차는 긴 기간, 밴드는 평균 기간, 한 번에 사기 · 적립식 줄은 0)
  private static int signalWindow(PortfolioStrategyType type, PortfolioStrategy strategy) {
    return switch (type) {
      case MA_CROSS -> strategy.longWindowOrDefault();
      case MA_BAND -> strategy.bandWindowOrDefault();
      case LUMP_SUM, DCA -> 0;
    };
  }

  // 시작일 전 신호 기간만큼 더 받은 일봉의 이동평균 교차 · 밴드 신호(한 번에 사기 · 적립식 줄은 null)
  private static SignalHolding signal(PortfolioStrategyType type, PortfolioStrategy strategy,
                                      Map<LocalDate, OHLCPriceDto> priceMap, LocalDate start) {
    return switch (type) {
      case MA_CROSS -> SignalHolding.maCross(priceMap, strategy.shortWindowOrDefault(), strategy.longWindowOrDefault(),
        start);
      case MA_BAND -> SignalHolding.maBand(priceMap, strategy.bandWindowOrDefault(), strategy.upperRateOrDefault(),
        strategy.lowerRateOrDefault(), start);
      case LUMP_SUM, DCA -> null;
    };
  }

  // 사는 거래일 → 계획일(한 번에 사기 줄은 기간 안 첫 거래일 하나, 적립식 줄은 요청의 주기로 쓸 거래일이 있는 회차)
  private static Map<LocalDate, LocalDate> rounds(PortfolioRequest request, PortfolioStrategyType type,
                                                  NavigableMap<LocalDate, OHLCPriceDto> candles, LocalDate valuationDate) {
    if (type == PortfolioStrategyType.LUMP_SUM) {
      return Map.of(candles.firstKey(), candles.firstKey());
    }
    LocalDate first = request.getFirstPurchaseDate() != null ? request.getFirstPurchaseDate() : request.getStartDate();
    int purchaseDay = request.getPurchaseDay() != null ? request.getPurchaseDay() : first.getDayOfMonth();
    int interval = request.getInvestmentInterval() != null ? request.getInvestmentInterval() : 1;
    Map<LocalDate, LocalDate> rounds = new TreeMap<>();
    DcaSchedule.rounds(candles, DcaFrequency.orMonthly(request.getFrequency()), first, valuationDate, purchaseDay,
        interval).stream()
      .filter(round -> round.trade() != null)
      .forEach(round -> rounds.putIfAbsent(round.trade().date(), round.planned()));
    return rounds;
  }
}
