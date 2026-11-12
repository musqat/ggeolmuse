package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.domain.strategy.DcaSchedule;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto.DividendPayment;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 포트폴리오 엔진: 여러 종목을 현금 하나(USD)로 날마다 양도세 납부 → 배당 → 회차 매수 → 기간 끝 매도 → 평가 순서로 돌림
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioEngine {

  private final MarketDataClientWrapper marketData;

  /** 받은 세후 배당(USD)과 배당락일 환율 */
  public record Dividend(String symbol, LocalDate exDate, BigDecimal amountUsd, BigDecimal fxRate) {}

  /** 요청 순서 줄마다 줄 금액 · 평가일 보유 주식 수 · 종가(USD) · 평가 원화 */
  public record Position(String symbol, BigDecimal plannedKrw, BigDecimal shares, BigDecimal closeUsd,
                         BigDecimal valueKrw) {}

  /** 기간 첫 거래일 · 평가일 · 거래 · 배당 · 줄마다 보유 · 합계 · 일별 가치와 지표 · 양도세 납부 · 기본 환율 날 */
  public record Result(LocalDate startDate, LocalDate valuationDate, List<StrategyTransaction> transactions,
                       List<Dividend> dividends, List<Position> positions, BigDecimal totalInvestedKrw,
                       BigDecimal cashUsd, BigDecimal cashKrw, BigDecimal stockValueKrw, BigDecimal totalAssetKrw,
                       BigDecimal currentFxRate, StrategyPerformance.Result performance,
                       CapitalGainsTaxCalculator.Payments taxPayments, List<LocalDate> defaultFxDates) {}

  // 줄마다 일봉 · 회차 · 배당을 받아 줄들의 쓸 수 있는 일봉 날짜를 합친 날마다 돌리고 평가일 환율로 평가
  public Result run(PortfolioRequest request) {
    LocalDate today = LocalDate.now();
    LocalDate valuationDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();
    log.info("포트폴리오 실행 시작: {}개 종목 · {} · {} ~ {}", request.getAssets().size(),
      PortfolioMethod.orLumpSum(request.getMethod()), request.getStartDate(), valuationDate);

    List<Line> lines = request.getAssets().stream().map(asset -> line(request, asset, valuationDate)).toList();
    NavigableSet<LocalDate> dates = new TreeSet<>();
    lines.forEach(line -> dates.addAll(line.candles.keySet()));
    FxRates fxRates = request.getPurchaseFxRate() == null
      ? BacktestDataUtils.buildDailyFxRateMap(marketData, dates.first(), valuationDate)
      : FxRates.EMPTY;
    FxRateDto currentFx = request.getCurrentFxRate() != null
      ? new FxRateDto(valuationDate, request.getCurrentFxRate())
      : BacktestDataUtils.getFxRateAt(marketData, valuationDate);

    Run run = new Run(request, lines, fxRates, valuationDate);
    dates.forEach(run::day);
    // 마지막 일봉 뒤 평가일까지 배당락일이 온 배당은 현금으로
    run.receiveDividends(valuationDate, false);
    Result result = run.finish(dates.first(), currentFx);
    log.info("포트폴리오 실행 완료: 거래 {}건, 넣은 돈 {}원, 총 자산 {}원", result.transactions().size(),
      result.totalInvestedKrw(), result.totalAssetKrw());
    return result;
  }

  // 줄의 기간 안 쓸 수 있는 일봉 · 매수 회차 · 배당락일 순서 배당(일봉이 없으면 DATA_NOT_FOUND)
  private Line line(PortfolioRequest request, PortfolioAsset asset, LocalDate valuationDate) {
    String symbol = asset.normalizedSymbol();
    NavigableMap<LocalDate, OHLCPriceDto> candles = new TreeMap<>();
    BacktestDataUtils.buildPriceMap(marketData, symbol, request.getStartDate(), valuationDate).values().stream()
      .filter(PriceLookup::isUsable)
      .forEach(candle -> candles.put(candle.date(), candle));
    if (candles.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, symbol + " 의 기간 안 시세가 없습니다");
    }
    List<DividendPayment> dividends = BacktestDataUtils.getDividendHistory(marketData, symbol, request.getStartDate(),
        valuationDate).getDividends().stream()
      .filter(dividend -> dividend.getExDate() != null)
      .sorted(Comparator.comparing(DividendPayment::getExDate))
      .toList();
    return new Line(symbol, request.amountOf(asset), candles, rounds(request, candles, valuationDate), dividends);
  }

  // 사는 거래일 → 계획일(한 번에 사기는 기간 안 첫 거래일 하나, 적립식은 쓸 거래일이 있는 회차)
  private static Map<LocalDate, LocalDate> rounds(PortfolioRequest request,
    NavigableMap<LocalDate, OHLCPriceDto> candles, LocalDate valuationDate) {
    if (PortfolioMethod.orLumpSum(request.getMethod()) == PortfolioMethod.LUMP_SUM) {
      return Map.of(candles.firstKey(), candles.firstKey());
    }
    LocalDate first = request.getFirstPurchaseDate() != null ? request.getFirstPurchaseDate() : request.getStartDate();
    int purchaseDay = request.getPurchaseDay() != null ? request.getPurchaseDay() : first.getDayOfMonth();
    int interval = request.getInvestmentInterval() != null ? request.getInvestmentInterval() : 1;
    Map<LocalDate, LocalDate> rounds = new TreeMap<>();
    DcaSchedule.rounds(candles, frequency(request), first, valuationDate, purchaseDay, interval).stream()
      .filter(round -> round.trade() != null)
      .forEach(round -> rounds.putIfAbsent(round.trade().date(), round.planned()));
    return rounds;
  }

  // 적립식 주기(비우면 매달)
  private static DcaFrequency frequency(PortfolioRequest request) {
    return request.getFrequency() != null ? request.getFrequency() : DcaFrequency.MONTHLY;
  }

  // 종목 줄 하나의 일봉 · 회차 · 배당과 보유 주식 · 원가(USD, 수수료 포함) · 그 날짜까지 마지막 종가
  private static final class Line {
    private final String symbol;
    private final BigDecimal plannedKrw;
    private final NavigableMap<LocalDate, OHLCPriceDto> candles;
    private final Map<LocalDate, LocalDate> rounds;
    private final List<DividendPayment> dividends;
    private int nextDividend;
    private BigDecimal shares = BigDecimal.ZERO;
    private BigDecimal cost = BigDecimal.ZERO;
    private BigDecimal lastClose;

    private Line(String symbol, BigDecimal plannedKrw, NavigableMap<LocalDate, OHLCPriceDto> candles,
      Map<LocalDate, LocalDate> rounds, List<DividendPayment> dividends) {
      this.symbol = symbol;
      this.plannedKrw = plannedKrw;
      this.candles = candles;
      this.rounds = rounds;
      this.dividends = dividends;
    }

    // 보유 × 마지막 종가(USD, 첫 일봉 전은 0)
    private BigDecimal valueUsd() {
      return lastClose == null ? BigDecimal.ZERO : shares.multiply(lastClose);
    }
  }

  // 평가일 전 하루의 반올림 전 가치(USD)와 앞 날 다음부터 그 날짜까지의 입금(USD)
  private record Day(LocalDate date, BigDecimal rawUsd, BigDecimal depositUsd) {}

  // 한 번 돌리는 동안의 공용 현금(USD) · 거래 · 배당 · 넣은 돈 · 일별 가치
  private static final class Run {
    private final PortfolioRequest request;
    private final List<Line> lines;
    private final FxRates fxRates;
    private final LocalDate valuationDate;
    private final CapitalGainsTaxPayer taxPayer;
    private final List<StrategyTransaction> transactions = new ArrayList<>();
    private final List<Dividend> dividends = new ArrayList<>();
    private final List<Day> days = new ArrayList<>();
    private BigDecimal cash = BigDecimal.ZERO;
    private BigDecimal invested = BigDecimal.ZERO;
    private BigDecimal pendingDepositUsd = BigDecimal.ZERO;
    private boolean bought;

    private Run(PortfolioRequest request, List<Line> lines, FxRates fxRates, LocalDate valuationDate) {
      this.request = request;
      this.lines = lines;
      this.fxRates = fxRates;
      this.valuationDate = valuationDate;
      this.taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
    }

    // 양도세 납부 → 배당 → 회차 매수 → 기간 끝 매도 → 평가(평가일은 마무리에서)
    private void day(LocalDate date) {
      BigDecimal fx = fxOn(date);
      lines.forEach(line -> {
        OHLCPriceDto candle = line.candles.get(date);
        if (candle != null) {
          line.lastClose = PriceLookup.effectiveClose(candle);
        }
      });
      StrategyTransaction payment = taxPayer.pay(date, transactions, cash, fx, fxRates.isDefault(date));
      if (payment != null) {
        payment.setReason(TradeReason.TAX_PAYMENT);
        transactions.add(payment);
        cash = cash.subtract(payment.getTaxPaid());
      }
      receiveDividends(date, Boolean.TRUE.equals(request.getReinvestDividends()));
      for (Line line : lines) {
        LocalDate planned = line.rounds.get(date);
        if (planned != null) {
          buy(line, date, planned, fx);
        }
      }
      if (PeriodEnd.sells(request.getPeriodEnd())) {
        lines.stream().filter(line -> date.equals(line.candles.lastKey())).forEach(line -> sellAtEnd(line, date, fx));
      }
      if (bought && date.isBefore(valuationDate)) {
        BigDecimal rawUsd = lines.stream().map(Line::valueUsd).reduce(cash, BigDecimal::add);
        days.add(new Day(date, rawUsd, pendingDepositUsd));
        pendingDepositUsd = BigDecimal.ZERO;
      }
    }

    // 배당락일이 그 날짜까지 온 배당을 전날까지 든 주식으로 받아 세후 달러를 현금에, 재투자면 그 날짜 그 종목 종가로 삼
    private void receiveDividends(LocalDate date, boolean reinvest) {
      for (Line line : lines) {
        while (line.nextDividend < line.dividends.size()
          && !line.dividends.get(line.nextDividend).getExDate().isAfter(date)) {
          DividendPayment payment = line.dividends.get(line.nextDividend++);
          if (payment.getAmount() == null) {
            continue;
          }
          BigDecimal gross = payment.getAmount().multiply(line.shares).setScale(2, RoundingMode.HALF_UP);
          BigDecimal afterTax = gross.subtract(withholding(gross));
          if (!Decimals.isPositive(afterTax)) {
            continue;
          }
          LocalDate exDate = payment.getExDate();
          BigDecimal fx = fxOn(exDate);
          dividends.add(new Dividend(line.symbol, exDate, afterTax, fx));
          cash = cash.add(afterTax);
          OHLCPriceDto candle = line.candles.get(date);
          if (reinvest && candle != null) {
            reinvest(line, exDate, candle, afterTax, fx);
          }
        }
      }
    }

    // 배당 원천징수(USD 두 자리, 세율이 없으면 0)
    private BigDecimal withholding(BigDecimal gross) {
      BigDecimal rate = request.getDividendTaxRate();
      return rate != null && Decimals.isPositive(rate)
        ? gross.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    }

    // 받은 배당 전부로 그 일봉 종가에 삼(수수료 없음)
    private void reinvest(Line line, LocalDate exDate, OHLCPriceDto candle, BigDecimal usd, BigDecimal fx) {
      BigDecimal price = PriceLookup.effectiveClose(candle);
      StrategyTransaction tx = StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(TradeReason.DIVIDEND_REINVESTMENT)
        .date(exDate)
        .actualDate(candle.date())
        .price(price)
        .shares(usd.divide(price, 8, RoundingMode.HALF_UP))
        .amount(BigDecimal.ZERO)
        .cashUsed(usd)
        .fxRate(fx)
        .defaultFxDate(fxRates.isDefault(exDate) ? exDate : null)
        .trigger(TransactionTrigger.DIVIDEND_REINVESTMENT)
        .build();
      record(line, tx);
    }

    // 줄 금액만큼 그 날짜 종가에 삼, 공용 현금을 먼저 쓰고 모자란 만큼 새 돈(원 단위)
    private void buy(Line line, LocalDate date, LocalDate planned, BigDecimal fx) {
      boolean lumpSum = PortfolioMethod.orLumpSum(request.getMethod()) == PortfolioMethod.LUMP_SUM;
      BigDecimal price = PriceLookup.effectiveClose(line.candles.get(date));
      StrategyTransaction.StrategyTransactionBuilder builder = StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(lumpSum ? TradeReason.BUY : TradeReason.DCA_BUY)
        .date(planned)
        .actualDate(date)
        .price(price)
        .fxRate(fx)
        .defaultFxDate(fxRates.isDefault(date) ? date : null)
        .trigger(lumpSum ? TransactionTrigger.LUMP_SUM_PURCHASE : frequency(request).trigger());
      BigDecimal amount = line.plannedKrw;
      if (!Decimals.isPositive(cash)) {
        RoundPurchase purchase = RoundPurchase.byAmount(amount, price, fx, request.getTradingFeeRate());
        record(line, builder.shares(purchase.shares()).fee(purchase.feeUsd()).amount(purchase.amountKrw()).build());
        return;
      }
      BigDecimal needUsd = amount.divide(fx, 8, RoundingMode.HALF_UP);
      BigDecimal cashUsed = cash.min(needUsd);
      BigDecimal deposit = cash.compareTo(needUsd) >= 0
        ? BigDecimal.ZERO
        : MoneyUtils.roundKrw(amount.subtract(cash.multiply(fx))).max(BigDecimal.ZERO);
      BigDecimal spendUsd = cashUsed.add(deposit.divide(fx, 8, RoundingMode.HALF_UP));
      BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(spendUsd, request.getTradingFeeRate());
      record(line, builder.shares(spendUsd.subtract(fee).divide(price, 8, RoundingMode.HALF_UP)).fee(fee)
        .amount(deposit).cashUsed(cashUsed).build());
    }

    // 매수 거래를 남기고 공용 현금 · 줄 보유 · 원가 · 넣은 돈에 반영
    private void record(Line line, StrategyTransaction tx) {
      transactions.add(tx);
      if (tx.getCashUsed() != null) {
        cash = cash.subtract(tx.getCashUsed());
      }
      line.shares = line.shares.add(tx.getShares());
      line.cost = line.cost.add(tx.costUsd());
      invested = invested.add(tx.getAmount());
      pendingDepositUsd = pendingDepositUsd.add(tx.getAmount().divide(tx.getFxRate(), MathContext.DECIMAL64));
      bought = true;
    }

    // 줄의 마지막 거래일 종가에 남은 주식 전량을 팔고 판 돈을 공용 현금에 넣음(남은 주식이 없으면 그대로)
    private void sellAtEnd(Line line, LocalDate date, BigDecimal fx) {
      StrategyTransaction sale = PeriodEndSale.of(line.shares, line.candles, valuationDate, line.lastClose,
        request.getTradingFeeRate(), fx);
      if (sale == null) {
        return;
      }
      sale.setSymbol(line.symbol);
      sale.setReason(TradeReason.PERIOD_END_SALE);
      sale.setRealizedProfit(sale.getProceeds().subtract(line.cost));
      sale.setDefaultFxDate(Boolean.TRUE.equals(request.getCapitalGainsTax()) && fxRates.isDefault(date) ? date : null);
      transactions.add(sale);
      cash = cash.add(sale.getProceeds());
      line.shares = BigDecimal.ZERO;
      line.cost = BigDecimal.ZERO;
    }

    // 그 날짜 환율(수동 환율이 있으면 그 값)
    private BigDecimal fxOn(LocalDate date) {
      return request.getPurchaseFxRate() != null ? request.getPurchaseFxRate() : fxRates.get(date);
    }

    // 평가일 환율로 줄마다 평가 · 현금 · 총 자산을 내고 날마다 가치(그 날짜 환율)와 시간가중 지표를 냄
    private Result finish(LocalDate startDate, FxRateDto currentFx) {
      BigDecimal rate = currentFx.rate();
      List<Position> positions = lines.stream()
        .map(line -> new Position(line.symbol, line.plannedKrw, line.shares, line.lastClose,
          MoneyUtils.roundKrw(line.valueUsd().multiply(rate))))
        .toList();
      BigDecimal stockValueKrw = positions.stream().map(Position::valueKrw).reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal cashKrw = MoneyUtils.roundKrw(cash.multiply(rate));
      BigDecimal totalAssetKrw = stockValueKrw.add(cashKrw);
      List<LocalDate> defaultFxDates = Stream.concat(transactions.stream().map(StrategyTransaction::getDefaultFxDate),
          Stream.of(currentFx.defaulted() ? currentFx.date() : null))
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
      return new Result(startDate, valuationDate, List.copyOf(transactions), List.copyOf(dividends), positions,
        invested, cash, cashKrw, stockValueKrw, totalAssetKrw, rate, performance(totalAssetKrw, rate),
        taxPayer.payments(), defaultFxDates);
    }

    // 산 날부터 일별 가치 · 지표(산 것이 없으면 일별 가치 없음 · 지표 null)
    private StrategyPerformance.Result performance(BigDecimal totalAssetKrw, BigDecimal rate) {
      if (!bought) {
        return new StrategyPerformance.Result(List.of(), null, null);
      }
      Function<LocalDate, BigDecimal> fxPath = StrategyFinalizer.fxPath(transactions, valuationDate,
        request.getPurchaseFxRate(), request.getCurrentFxRate(), rate, fxRates);
      List<StrategyPerformance.Point> points = new ArrayList<>();
      for (Day day : days) {
        BigDecimal fx = fxPath.apply(day.date());
        BigDecimal valueUsd = MoneyUtils.roundUsd(day.rawUsd());
        points.add(new StrategyPerformance.Point(new DailyValue(day.date(), valueUsd, DailyValue.toKrw(valueUsd, fx)),
          day.rawUsd(), day.depositUsd(), fx));
      }
      BigDecimal lastRawUsd = totalAssetKrw.divide(rate, MathContext.DECIMAL64);
      points.add(new StrategyPerformance.Point(
        new DailyValue(valuationDate, lastRawUsd.setScale(2, RoundingMode.HALF_UP), totalAssetKrw), lastRawUsd,
        pendingDepositUsd, rate));
      BigDecimal firstFx = transactions.stream()
        .filter(tx -> Decimals.isPositive(tx.getAmount()))
        .findFirst()
        .map(StrategyTransaction::getFxRate)
        .orElse(rate);
      return StrategyPerformance.measure(points, firstFx, request.getStartDate());
    }
  }
}
