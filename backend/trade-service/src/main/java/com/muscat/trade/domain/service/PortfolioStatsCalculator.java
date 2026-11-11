package com.muscat.trade.domain.service;

import static java.math.BigDecimal.ZERO;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.DividendStatsResponse;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.MonthlyDividend;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.Payout;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.SymbolDividend;
import com.muscat.trade.domain.dto.response.FxStatsResponse;
import com.muscat.trade.domain.dto.response.FxStatsResponse.Direction;
import com.muscat.trade.domain.dto.response.FxStatsResponse.Exchange;
import com.muscat.trade.domain.dto.response.MarketComparisonResponse;
import com.muscat.trade.domain.dto.response.MarketComparisonResponse.MarketPoint;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Holding;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.MonthlyProfit;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Sale;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse.SymbolContribution;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse.SymbolTrades;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse.WeekdayTrades;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.infra.client.dto.ExchangeRecordDto;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 계좌 통계 탭 · 시장 비교 계산(완료 체결은 수익 흐름 계산과 같은 순서 · 실현 손익 식, 원화는 그 날짜 환율)
public final class PortfolioStatsCalculator {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int QUANTITY_SCALE = 6;
  private static final int TOP_SYMBOLS = 5;

  // 매도 하나의 실현 손익과 줄어든 투자금액(USD, 반올림 전)
  public record SellResult(Trade trade, BigDecimal realizedUsd, BigDecimal costUsd) {
  }

  // 종목 하나의 그 날짜 수량 · 투자금액과 그 날짜까지 실현 손익 · 세후 배당 · 수수료 누적(USD, 반올림 전)
  public record SymbolTotals(String symbol, BigDecimal quantity, BigDecimal investedUsd, BigDecimal realizedUsd,
    BigDecimal dividendUsd, BigDecimal feeUsd) {

    static SymbolTotals empty(String symbol) {
      return new SymbolTotals(symbol, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    public boolean held() {
      return quantity.signum() > 0;
    }

    SymbolTotals plus(SymbolTotals other) {
      return new SymbolTotals(symbol, quantity.add(other.quantity), investedUsd.add(other.investedUsd),
        realizedUsd.add(other.realizedUsd), dividendUsd.add(other.dividendUsd), feeUsd.add(other.feeUsd));
    }

    // 수량 × 종가 − 투자금액(종가가 없으면 −투자금액)
    BigDecimal unrealizedUsd(BigDecimal close) {
      return (close == null ? ZERO : quantity.multiply(close)).subtract(investedUsd);
    }
  }

  // 한 계좌의 기준 날짜까지 매도(체결 순서)와 종목마다의 값
  public record Replay(List<SellResult> sells, Map<String, SymbolTotals> symbols) {
  }

  private PortfolioStatsCalculator() {
  }

  // until 이하 거래일의 완료 체결과 until 이하 배당락일의 배당 몫
  public static Replay replay(List<Trade> trades, List<DividendPayout> payouts, LocalDate until,
    int pricePrecision) {
    Map<String, Holdings> positions = new HashMap<>();
    Map<String, BigDecimal> realized = new HashMap<>();
    Map<String, BigDecimal> fees = new HashMap<>();
    Map<String, BigDecimal> dividends = new HashMap<>();
    List<SellResult> sells = new ArrayList<>();
    for (Trade trade : completedUntil(trades, until)) {
      String symbol = trade.getSymbol();
      BigDecimal investedBefore = invested(positions.get(symbol));
      BigDecimal gain = ProfitFlowCalculator.apply(positions, trade, pricePrecision);
      fees.merge(symbol, trade.getFee(), BigDecimal::add);
      if (trade.getTradeType() == TradeType.SELL) {
        realized.merge(symbol, gain, BigDecimal::add);
        sells.add(new SellResult(trade, gain, investedBefore.subtract(invested(positions.get(symbol)))));
      }
    }
    payouts.stream()
      .filter(payout -> !payout.getExDate().isAfter(until))
      .forEach(payout -> dividends.merge(payout.getSymbol(), payout.getNetAmount(), BigDecimal::add));

    Set<String> symbols = new TreeSet<>(fees.keySet());
    symbols.addAll(dividends.keySet());
    Map<String, SymbolTotals> totals = new TreeMap<>();
    for (String symbol : symbols) {
      Holdings position = positions.get(symbol);
      totals.put(symbol, new SymbolTotals(symbol, position == null ? ZERO : position.getTotalQuantity(),
        invested(position), realized.getOrDefault(symbol, ZERO), dividends.getOrDefault(symbol, ZERO),
        fees.getOrDefault(symbol, ZERO)));
    }
    return new Replay(sells, totals);
  }

  // until 이하 거래일의 완료 체결(수익 흐름 계산과 같은 순서)
  private static List<Trade> completedUntil(List<Trade> trades, LocalDate until) {
    return trades.stream()
      .filter(trade -> trade.getStatus() == TradeStatus.COMPLETED && !trade.getTradeDate().isAfter(until))
      .sorted(ProfitFlowCalculator.TRADE_ORDER)
      .toList();
  }

  private static BigDecimal invested(Holdings position) {
    return position == null ? ZERO : position.getTotalInvestedAmount();
  }

  // 계좌마다의 종목 값을 종목마다 더함
  public static Map<String, SymbolTotals> merge(List<Replay> replays) {
    Map<String, SymbolTotals> merged = new TreeMap<>();
    replays.forEach(replay -> replay.symbols().forEach(
      (symbol, totals) -> merged.merge(symbol, totals, SymbolTotals::plus)));
    return merged;
  }

  // 수익 탭 자세히(sells 는 기간 안 매도, holdings 는 기간 끝에 들고 있는 종목, start 가 null 이면 전체 기간)
  public static ProfitDetailResponse profitDetail(List<ProfitPoint> points, LocalDate start,
    List<SellResult> sells, Collection<SymbolTotals> holdings, Map<String, NavigableMap<LocalDate, BigDecimal>> closes,
    Map<LocalDate, BigDecimal> fxRates, LocalDate end) {
    List<Sale> sales = sells.stream()
      .sorted(Comparator.comparing((SellResult sell) -> sell.trade().getTradeDate())
        .thenComparing(sell -> sell.trade().getId())
        .reversed())
      .map(sell -> sale(sell, fxRates.get(sell.trade().getTradeDate())))
      .toList();
    BigDecimal endRate = fxRates.get(end);
    List<Holding> held = holdings.stream()
      .map(totals -> holding(totals, close(closes, totals.symbol(), end), endRate))
      .sorted(Comparator.comparing(Holding::unrealizedKrw).reversed().thenComparing(Holding::symbol))
      .toList();
    return new ProfitDetailResponse(monthly(points, start), sum(sales.stream().map(Sale::realizedKrw)),
      sum(held.stream().map(Holding::unrealizedKrw)), sales, held);
  }

  // 기간 안 점이 있는 달마다 그 달 마지막 점 − 앞 달 마지막 점(첫 달은 기간 시작 전 마지막 점, 없으면 0)
  private static List<MonthlyProfit> monthly(List<ProfitPoint> points, LocalDate start) {
    BigDecimal previous = ZERO;
    NavigableMap<YearMonth, BigDecimal> monthEnds = new TreeMap<>();
    for (ProfitPoint point : points) {
      if (start != null && point.date().isBefore(start)) {
        previous = point.profitKrw();
      } else {
        monthEnds.put(YearMonth.from(point.date()), point.profitKrw());
      }
    }
    List<MonthlyProfit> monthly = new ArrayList<>();
    for (Map.Entry<YearMonth, BigDecimal> monthEnd : monthEnds.entrySet()) {
      monthly.add(new MonthlyProfit(monthEnd.getKey(), monthEnd.getValue().subtract(previous)));
      previous = monthEnd.getValue();
    }
    return monthly;
  }

  private static Sale sale(SellResult sell, BigDecimal fxRate) {
    Trade trade = sell.trade();
    return new Sale(trade.getId(), trade.getAccountId(), trade.getTradeDate(), trade.getSymbol(),
      quantity(trade.getQuantity()), usd(trade.getPrice()), usd(sell.realizedUsd()), rate(fxRate),
      krw(sell.realizedUsd(), fxRate), percent(sell.realizedUsd(), sell.costUsd()));
  }

  private static Holding holding(SymbolTotals totals, Map.Entry<LocalDate, BigDecimal> close, BigDecimal fxRate) {
    BigDecimal unrealized = totals.unrealizedUsd(close == null ? null : close.getValue());
    return new Holding(totals.symbol(), quantity(totals.quantity()),
      totals.investedUsd().divide(totals.quantity(), 2, RoundingMode.HALF_UP),
      close == null ? null : usd(close.getValue()), close == null ? null : close.getKey(),
      usd(unrealized), krw(unrealized, fxRate), percent(unrealized, totals.investedUsd()));
  }

  // 종목별 기여(칸마다 기간 끝 값 − 기간 시작 전 값, before 가 null 이면 시작 전 값 0, 시작 전날 환율이 없으면 기간 끝 환율)
  public static SymbolContributionResponse symbolContributions(Map<String, SymbolTotals> end,
    Map<String, SymbolTotals> previous, Map<String, NavigableMap<LocalDate, BigDecimal>> closes,
    Map<LocalDate, BigDecimal> fxRates, LocalDate endDate, LocalDate before) {
    BigDecimal endRate = fxRates.get(endDate);
    BigDecimal beforeRate = before == null ? null : fxRates.getOrDefault(before, endRate);
    Set<String> symbols = new TreeSet<>(end.keySet());
    symbols.addAll(previous.keySet());

    List<Change> changes = new ArrayList<>();
    for (String symbol : symbols) {
      SymbolTotals last = end.getOrDefault(symbol, SymbolTotals.empty(symbol));
      SymbolTotals first = previous.getOrDefault(symbol, SymbolTotals.empty(symbol));
      Change change = new Change(symbol,
        change(last.realizedUsd(), endRate, first.realizedUsd(), beforeRate),
        change(last.unrealizedUsd(closeValue(closes, symbol, endDate)), endRate,
          first.unrealizedUsd(before == null ? null : closeValue(closes, symbol, before)), beforeRate),
        change(last.dividendUsd(), endRate, first.dividendUsd(), beforeRate),
        change(last.feeUsd(), endRate, first.feeUsd(), beforeRate),
        last.held());
      if (change.shown()) {
        changes.add(change);
      }
    }

    BigDecimal absoluteSum = sum(changes.stream().map(change -> change.totalKrw().abs()));
    List<SymbolContribution> rows = changes.stream()
      .map(change -> new SymbolContribution(change.symbol(), change.realizedKrw(), change.unrealizedKrw(),
        change.dividendKrw(), change.feeKrw(), change.totalKrw(), percent(change.totalKrw(), absoluteSum),
        change.held()))
      .sorted(Comparator.comparing(SymbolContribution::totalKrw).reversed()
        .thenComparing(SymbolContribution::symbol))
      .toList();
    return new SymbolContributionResponse(sum(rows.stream().map(SymbolContribution::totalKrw)), rows);
  }

  // 종목 하나의 원화 칸(기간 끝 값 − 기간 시작 전 값)
  private record Change(String symbol, BigDecimal realizedKrw, BigDecimal unrealizedKrw, BigDecimal dividendKrw,
    BigDecimal feeKrw, boolean held) {

    BigDecimal totalKrw() {
      return realizedKrw.add(unrealizedKrw).add(dividendKrw).subtract(feeKrw);
    }

    // 칸 하나라도 0 이 아니거나 기간 끝에 들고 있음
    boolean shown() {
      return held || Stream.of(realizedKrw, unrealizedKrw, dividendKrw, feeKrw).anyMatch(value -> value.signum() != 0);
    }
  }

  private static BigDecimal change(BigDecimal endUsd, BigDecimal endRate, BigDecimal beforeUsd,
    BigDecimal beforeRate) {
    return krw(endUsd, endRate).subtract(krw(beforeUsd, beforeRate));
  }

  // 배당락일이 start(null 이면 처음)부터 today 까지이고 수량이 0 이 아닌 배당
  public static List<DividendPayout> paidInPeriod(Collection<DividendPayout> payouts, LocalDate start,
    LocalDate today) {
    return payouts.stream()
      .filter(payout -> payout.getShares().signum() > 0)
      .filter(payout -> start == null || !payout.getExDate().isBefore(start))
      .filter(payout -> !payout.getExDate().isAfter(today))
      .toList();
  }

  // 배당 탭(원화는 배당락일 환율, 달마다 · 종목마다 · 맨 위 합은 배당 줄 값의 합)
  public static DividendStatsResponse dividends(List<DividendPayout> paid, Map<LocalDate, BigDecimal> fxRates) {
    List<Payout> payouts = paid.stream()
      .map(payout -> payout(payout, fxRates.get(payout.getExDate())))
      .sorted(Comparator.comparing(Payout::exDate, Comparator.reverseOrder())
        .thenComparing(Payout::accountId)
        .thenComparing(Payout::symbol))
      .toList();
    BigDecimal netKrw = sum(payouts.stream().map(Payout::netKrw));

    List<MonthlyDividend> monthly = payouts.stream()
      .collect(Collectors.groupingBy(payout -> YearMonth.from(payout.exDate()), TreeMap::new, Collectors.toList()))
      .entrySet().stream()
      .map(month -> new MonthlyDividend(month.getKey(), sum(month.getValue().stream().map(Payout::netUsd)),
        sum(month.getValue().stream().map(Payout::netKrw))))
      .toList();
    List<SymbolDividend> symbols = payouts.stream()
      .collect(Collectors.groupingBy(Payout::symbol, TreeMap::new, Collectors.toList()))
      .entrySet().stream()
      .map(symbol -> {
        BigDecimal symbolKrw = sum(symbol.getValue().stream().map(Payout::netKrw));
        return new SymbolDividend(symbol.getKey(), sum(symbol.getValue().stream().map(Payout::netUsd)), symbolKrw,
          symbol.getValue().size(), percent(symbolKrw, netKrw));
      })
      .sorted(Comparator.comparing(SymbolDividend::netKrw).reversed().thenComparing(SymbolDividend::symbol))
      .toList();
    return new DividendStatsResponse(netKrw, sum(payouts.stream().map(Payout::netUsd)),
      sum(payouts.stream().map(Payout::grossUsd)), sum(payouts.stream().map(Payout::taxUsd)), payouts.size(),
      monthly, symbols, payouts);
  }

  private static Payout payout(DividendPayout payout, BigDecimal fxRate) {
    return new Payout(payout.getExDate(), payout.getAccountId(), payout.getSymbol(), quantity(payout.getShares()),
      quantity(payout.getDividendPerShare()), usd(payout.getGrossAmount()), usd(payout.getTaxAmount()),
      usd(payout.getNetAmount()), rate(fxRate), krw(payout.getNetAmount(), fxRate));
  }

  // 환율 탭(효과는 기간 안 환전마다 반올림한 효과 합 + 시작 전 환전의 기간 동안 효과 변화, 오늘 환율이 없으면 0)
  public static FxStatsResponse fx(List<ExchangeRecordDto> exchanges, LocalDate start, LocalDate today,
    Map<LocalDate, BigDecimal> fxRates) {
    BigDecimal endRate = fxRates.get(today);
    BigDecimal beforeRate = start == null ? null : fxRates.get(start.minusDays(1));
    BigDecimal signedUsd = ZERO;
    BigDecimal signedCost = ZERO;
    BigDecimal carried = ZERO;
    List<Exchange> rows = new ArrayList<>();
    List<ExchangeRecordDto> ordered = exchanges.stream()
      .sorted(Comparator.comparing(ExchangeRecordDto::exchangedAt))
      .toList();
    for (ExchangeRecordDto exchange : ordered) {
      boolean sold = "USD".equals(exchange.fromCurrency());
      BigDecimal usd = sold ? exchange.originalAmount() : exchange.exchangedAmount();
      BigDecimal signed = sold ? usd.negate() : usd;
      BigDecimal exchangeRate = exchange.exchangeRate();
      signedUsd = signedUsd.add(signed);
      signedCost = signedCost.add(signed.multiply(exchangeRate));
      LocalDate date = exchange.exchangedAt().toLocalDate();
      if (start != null && date.isBefore(start)) {
        carried = carried.add(effect(signed, endRate, exchangeRate)).subtract(effect(signed, beforeRate, exchangeRate));
      } else {
        BigDecimal won = sold ? exchange.exchangedAmount() : exchange.originalAmount();
        rows.addFirst(new Exchange(date, exchange.accountId(), sold ? Direction.USD_TO_KRW : Direction.KRW_TO_USD,
          won.setScale(0, RoundingMode.HALF_UP), usd(usd), rate(exchangeRate),
          effect(signed, endRate, exchangeRate).setScale(0, RoundingMode.HALF_UP)));
      }
    }

    BigDecimal fxEffect = endRate == null
      ? ZERO
      : sum(rows.stream().map(Exchange::effectKrw)).add(carried.setScale(0, RoundingMode.HALF_UP));
    return new FxStatsResponse(fxEffect, rate(endRate),
      signedUsd.signum() > 0 ? signedCost.divide(signedUsd, 2, RoundingMode.HALF_UP) : null,
      usd(signedUsd.max(ZERO)),
      sum(rows.stream().filter(row -> row.direction() == Direction.KRW_TO_USD).map(Exchange::krwAmount)),
      sum(rows.stream().filter(row -> row.direction() == Direction.USD_TO_KRW).map(Exchange::usdAmount)),
      rows.size(), rows);
  }

  // 환전 달러(판 달러는 음수) × (그 날짜 환율 − 환전 환율), 환율이 없으면 0
  private static BigDecimal effect(BigDecimal signedUsd, BigDecimal fxRate, BigDecimal exchangeRate) {
    return fxRate == null ? ZERO : signedUsd.multiply(fxRate.subtract(exchangeRate));
  }

  // 거래 패턴(accounts 는 계좌마다 모든 상태 체결, start 가 null 이면 전체 기간)
  public static TradingHabitsResponse habits(List<List<Trade>> accounts, LocalDate start, LocalDate today,
    int pricePrecision) {
    List<Trade> traded = new ArrayList<>();
    List<SellResult> sells = new ArrayList<>();
    HeldDays held = HeldDays.NONE;
    for (List<Trade> trades : accounts) {
      List<Trade> completed = completedUntil(trades, today);
      completed.stream().filter(trade -> inPeriod(trade, start)).forEach(traded::add);
      replay(trades, List.of(), today, pricePrecision).sells().stream()
        .filter(sell -> inPeriod(sell.trade(), start))
        .forEach(sells::add);
      held = held.plus(heldDays(completed, start));
    }

    Map<DayOfWeek, Long> byWeekday = traded.stream()
      .collect(Collectors.groupingBy(trade -> trade.getTradeDate().getDayOfWeek(), Collectors.counting()));
    List<WeekdayTrades> weekdays = Stream.of(DayOfWeek.values())
      .map(weekday -> new WeekdayTrades(weekday, byWeekday.getOrDefault(weekday, 0L).intValue()))
      .toList();
    List<SymbolTrades> topSymbols = traded.stream()
      .collect(Collectors.groupingBy(Trade::getSymbol))
      .entrySet().stream()
      .map(symbol -> symbolTrades(symbol.getKey(), symbol.getValue()))
      .sorted(Comparator.comparingInt(SymbolTrades::tradeCount).reversed()
        .thenComparing(SymbolTrades::amountUsd, Comparator.reverseOrder())
        .thenComparing(SymbolTrades::symbol))
      .limit(TOP_SYMBOLS)
      .toList();
    int winCount = (int) sells.stream().filter(sell -> usd(sell.realizedUsd()).signum() > 0).count();
    return new TradingHabitsResponse(buyCount(traded), sells.size(),
      (int) traded.stream().map(Trade::getTradeDate).distinct().count(), held.average(), winCount,
      sells.size() - winCount,
      sells.isEmpty() ? null : percent(BigDecimal.valueOf(winCount), BigDecimal.valueOf(sells.size())),
      weekdays, topSymbols);
  }

  // 짝지은 매수 수량 합과 수량 × 보유 일수 합
  private record HeldDays(BigDecimal quantity, BigDecimal quantityDays) {

    static final HeldDays NONE = new HeldDays(ZERO, ZERO);

    HeldDays plus(HeldDays other) {
      return new HeldDays(quantity.add(other.quantity), quantityDays.add(other.quantityDays));
    }

    // 짝지은 수량이 없으면 null
    BigDecimal average() {
      return quantity.signum() == 0 ? null : quantityDays.divide(quantity, 1, RoundingMode.HALF_UP);
    }
  }

  // 아직 짝짓지 않은 매수 수량
  private record Lot(LocalDate tradeDate, BigDecimal quantity) {
  }

  // 매도마다 판 수량을 종목마다 먼저 산 매수부터 짝지은 보유 일수(기간 안 매도만, 보유보다 많이 판 수량은 짝 없음)
  private static HeldDays heldDays(List<Trade> completed, LocalDate start) {
    Map<String, Deque<Lot>> lots = new HashMap<>();
    HeldDays held = HeldDays.NONE;
    for (Trade trade : completed) {
      Deque<Lot> symbolLots = lots.computeIfAbsent(trade.getSymbol(), symbol -> new ArrayDeque<>());
      if (trade.getTradeType() == TradeType.BUY) {
        symbolLots.addLast(new Lot(trade.getTradeDate(), trade.getQuantity()));
        continue;
      }
      BigDecimal remaining = trade.getQuantity();
      while (remaining.signum() > 0 && !symbolLots.isEmpty()) {
        Lot lot = symbolLots.pollFirst();
        BigDecimal matched = lot.quantity().min(remaining);
        if (lot.quantity().compareTo(matched) > 0) {
          symbolLots.addFirst(new Lot(lot.tradeDate(), lot.quantity().subtract(matched)));
        }
        remaining = remaining.subtract(matched);
        if (inPeriod(trade, start)) {
          long days = ChronoUnit.DAYS.between(lot.tradeDate(), trade.getTradeDate());
          held = held.plus(new HeldDays(matched, matched.multiply(BigDecimal.valueOf(days))));
        }
      }
    }
    return held;
  }

  private static SymbolTrades symbolTrades(String symbol, List<Trade> trades) {
    int buyCount = buyCount(trades);
    return new SymbolTrades(symbol, trades.size(), buyCount, trades.size() - buyCount,
      usd(sum(trades.stream().map(trade -> trade.getQuantity().multiply(trade.getPrice())))));
  }

  private static int buyCount(List<Trade> trades) {
    return (int) trades.stream().filter(trade -> trade.getTradeType() == TradeType.BUY).count();
  }

  // 거래일이 start(null 이면 처음)부터
  private static boolean inPeriod(Trade trade, LocalDate start) {
    return start == null || !trade.getTradeDate().isBefore(start);
  }

  // 계좌 하나의 모든 상태 체결과 수익 흐름 점
  public record AccountFlow(List<Trade> trades, List<ProfitPoint> points) {
  }

  // 기간 첫 날부터의 점과 그 앞 마지막 점(start 가 null 이면 모든 점)
  public static List<ProfitPoint> periodPoints(List<ProfitPoint> points, LocalDate start) {
    if (start == null) {
      return points;
    }
    List<ProfitPoint> kept = new ArrayList<>();
    ProfitPoint lastBefore = null;
    for (ProfitPoint point : points) {
      if (point.date().isBefore(start)) {
        lastBefore = point;
      } else {
        kept.add(point);
      }
    }
    if (lastBefore != null) {
      kept.addFirst(lastBefore);
    }
    return kept;
  }

  // 시장 비교(계좌마다 셈한 SPY 수익을 날짜마다 더해 기간 첫 날부터, 종가 · 환율이 빠진 날짜가 있으면 빈 값)
  public static MarketComparisonResponse market(List<AccountFlow> accounts,
    NavigableMap<LocalDate, BigDecimal> closes, Map<LocalDate, BigDecimal> fxRates, LocalDate start,
    int pricePrecision) {
    List<List<ProfitPoint>> series = new ArrayList<>();
    for (AccountFlow account : accounts) {
      List<ProfitPoint> points = marketPoints(account, closes, fxRates, pricePrecision);
      if (points == null) {
        return MarketComparisonResponse.empty();
      }
      series.add(points);
    }
    List<MarketPoint> points = ProfitFlowCalculator.sumByDate(series).stream()
      .filter(point -> start == null || !point.date().isBefore(start))
      .map(point -> new MarketPoint(point.date(), point.profitKrw()))
      .toList();
    return new MarketComparisonResponse(MarketComparisonResponse.SYMBOL, points,
      points.isEmpty() ? ZERO : points.getLast().profitKrw());
  }

  // 계좌 하나의 점 날짜마다 SPY 원화 수익(매수는 체결 금액 + 수수료만큼 사고 매도는 줄어든 투자금액 비율만큼 팜, 종가 · 환율이 빠지면 null)
  private static List<ProfitPoint> marketPoints(AccountFlow account, NavigableMap<LocalDate, BigDecimal> closes,
    Map<LocalDate, BigDecimal> fxRates, int pricePrecision) {
    List<Trade> completed = account.trades().stream()
      .filter(trade -> trade.getStatus() == TradeStatus.COMPLETED)
      .sorted(ProfitFlowCalculator.TRADE_ORDER)
      .toList();
    Map<String, Holdings> positions = new HashMap<>();
    BigDecimal shares = ZERO;
    BigDecimal cost = ZERO;
    BigDecimal realized = ZERO;
    int next = 0;
    List<ProfitPoint> points = new ArrayList<>();
    for (ProfitPoint flowPoint : account.points()) {
      LocalDate day = flowPoint.date();
      while (next < completed.size() && !completed.get(next).getTradeDate().isAfter(day)) {
        Trade trade = completed.get(next++);
        BigDecimal close = floorValue(closes, trade.getTradeDate());
        if (close == null) {
          return null;
        }
        BigDecimal investedBefore = sum(positions.values().stream().map(Holdings::getTotalInvestedAmount));
        BigDecimal symbolBefore = invested(positions.get(trade.getSymbol()));
        ProfitFlowCalculator.apply(positions, trade, pricePrecision);
        if (trade.getTradeType() == TradeType.BUY) {
          BigDecimal amount = trade.getQuantity().multiply(trade.getPrice()).add(trade.getFee());
          shares = shares.add(amount.divide(close, MathContext.DECIMAL128));
          cost = cost.add(amount);
        } else if (investedBefore.signum() > 0) {
          BigDecimal ratio = symbolBefore.subtract(invested(positions.get(trade.getSymbol())))
            .divide(investedBefore, MathContext.DECIMAL128);
          BigDecimal soldShares = shares.multiply(ratio);
          BigDecimal soldCost = cost.multiply(ratio);
          realized = realized.add(soldShares.multiply(close)).subtract(soldCost);
          shares = shares.subtract(soldShares);
          cost = cost.subtract(soldCost);
        }
      }
      BigDecimal close = floorValue(closes, day);
      BigDecimal fxRate = fxRates.get(day);
      if (close == null || fxRate == null) {
        return null;
      }
      points.add(new ProfitPoint(day, krw(shares.multiply(close).subtract(cost).add(realized), fxRate), ZERO));
    }
    return points;
  }

  // 그 날짜 이하 가장 늦은 종가
  private static Map.Entry<LocalDate, BigDecimal> close(Map<String, NavigableMap<LocalDate, BigDecimal>> closes,
    String symbol, LocalDate day) {
    NavigableMap<LocalDate, BigDecimal> symbolCloses = closes.get(symbol);
    return symbolCloses == null ? null : symbolCloses.floorEntry(day);
  }

  private static BigDecimal closeValue(Map<String, NavigableMap<LocalDate, BigDecimal>> closes, String symbol,
    LocalDate day) {
    Map.Entry<LocalDate, BigDecimal> close = close(closes, symbol, day);
    return close == null ? null : close.getValue();
  }

  private static BigDecimal floorValue(NavigableMap<LocalDate, BigDecimal> closes, LocalDate day) {
    Map.Entry<LocalDate, BigDecimal> close = closes.floorEntry(day);
    return close == null ? null : close.getValue();
  }

  private static BigDecimal sum(Stream<BigDecimal> values) {
    return values.reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal quantity(BigDecimal quantity) {
    return quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal usd(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal rate(BigDecimal fxRate) {
    return fxRate == null ? null : fxRate.setScale(2, RoundingMode.HALF_UP);
  }

  // 환율이 없으면 0
  private static BigDecimal krw(BigDecimal usd, BigDecimal fxRate) {
    return fxRate == null ? ZERO : usd.multiply(fxRate).setScale(0, RoundingMode.HALF_UP);
  }

  // 기준 금액이 0 이면 0
  private static BigDecimal percent(BigDecimal amount, BigDecimal base) {
    return base.signum() == 0
      ? ZERO.setScale(2)
      : amount.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
  }
}
