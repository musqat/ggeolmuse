package com.muscat.trade.domain.service;

import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.FlowPeriod;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.entity.AccountProfitDaily;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// 계좌 수익 흐름 조회(저장분을 읽고 모자라거나 바뀐 날짜만 다시 계산해 저장, 원격 호출은 트랜잭션 밖)
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitFlowService {

  static final String ALL_ACCOUNTS = "all";
  static final int ALWAYS_RECOMPUTED_DAYS = 7;
  static final int CLOSE_LOOKBACK_DAYS = 14;
  static final Duration CHANGE_MARGIN = Duration.ofMinutes(1);

  private final TradeRepository tradeRepository;
  private final DividendPayoutRepository dividendPayoutRepository;
  private final AccountProfitDailyRepository accountProfitDailyRepository;
  private final AccountProfitStore accountProfitStore;
  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final TradeProperties tradeProperties;
  private final Clock clock;

  // accountId 는 계좌 ID 또는 all, 남의 계좌면 접근 거부
  public ProfitFlowResponse flow(String userId, String accountId, FlowPeriod period) {
    Long account = parseAccount(accountId);
    List<Trade> trades;
    if (account == null) {
      trades = tradeRepository.findByUserId(userId);
    } else {
      checkOwner(userId, account);
      trades = tradeRepository.findByUserIdAndAccountId(userId, account);
    }
    Map<Long, List<DividendPayout>> payouts = dividendPayoutRepository.findByUserId(userId).stream()
      .collect(Collectors.groupingBy(DividendPayout::getAccountId));

    LocalDate today = LocalDate.now(clock);
    List<List<ProfitPoint>> series = new ArrayList<>();
    trades.stream()
      .collect(Collectors.groupingBy(Trade::getAccountId, TreeMap::new, Collectors.toList()))
      .forEach((id, accountTrades) -> series.add(
        refresh(userId, id, accountTrades, payouts.getOrDefault(id, List.of()), today)));

    LocalDate start = period.startFrom(today);
    return ProfitFlowCalculator.summarize(sumByDate(series).stream()
      .filter(point -> start == null || !point.date().isBefore(start))
      .toList());
  }

  private static Long parseAccount(String accountId) {
    if (ALL_ACCOUNTS.equalsIgnoreCase(accountId)) {
      return null;
    }
    try {
      long id = Long.parseLong(accountId);
      if (id > 0) {
        return id;
      }
    } catch (NumberFormatException e) {
      log.debug("계좌 값이 숫자가 아님: {}", accountId);
    }
    throw new TradeException(TradeResponse.INVALID_ACCOUNT_ID);
  }

  // 체결 테이블의 계좌 주인(체결이 없는 계좌는 보여 줄 값이 없어 넘어감)
  private void checkOwner(String userId, Long accountId) {
    tradeRepository.findFirstByAccountIdOrderByIdAsc(accountId)
      .filter(trade -> !trade.getUserId().equals(userId))
      .ifPresent(trade -> {
        throw new TradeException(TradeResponse.ACCOUNT_ACCESS_DENIED);
      });
  }

  // 한 계좌의 점(정산 전 매수의 거래일부터는 저장하지 않음)
  private List<ProfitPoint> refresh(String userId, Long accountId, List<Trade> trades,
    List<DividendPayout> payouts, LocalDate today) {
    LocalDateTime startedAt = LocalDateTime.now(clock);
    List<AccountProfitDaily> stored =
      accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(userId, accountId);
    LocalDate first = earliest(trades.stream()
      .filter(trade -> trade.getStatus() == TradeStatus.COMPLETED)
      .map(Trade::getTradeDate));
    if (first == null) {
      if (!stored.isEmpty()) {
        accountProfitStore.replaceFrom(accountId, stored.getFirst().getTradeDate(), List.of());
      }
      return List.of();
    }

    LocalDate from = recomputeFrom(stored, trades, payouts, first);
    LocalDate computeFrom = from.isBefore(first) ? first : from;
    List<DailyProfit> computed = compute(trades, payouts, computeFrom, today);

    LocalDate unsettled = earliest(trades.stream()
      .filter(trade -> trade.getStatus() == TradeStatus.PENDING)
      .map(Trade::getTradeDate));
    List<AccountProfitDaily> rows = computed.stream()
      .filter(day -> unsettled == null || day.date().isBefore(unsettled))
      .map(day -> AccountProfitDaily.of(userId, accountId, day, startedAt))
      .toList();
    save(accountId, from, rows);

    return Stream.concat(
        stored.stream()
          .filter(row -> row.getTradeDate().isBefore(from))
          .map(row -> new ProfitPoint(row.getTradeDate(), row.getProfitKrw(), row.getInvestedKrw())),
        computed.stream().map(day -> new ProfitPoint(day.date(), day.profitKrw(), day.investedKrw())))
      .toList();
  }

  // 저장분이 없으면 첫 거래일, 있으면 최근 7거래일과 마지막 계산 뒤 생긴 체결 · 취소 · 배당 몫 변경 중 가장 이른 날짜
  private static LocalDate recomputeFrom(List<AccountProfitDaily> stored, List<Trade> trades,
    List<DividendPayout> payouts, LocalDate first) {
    if (stored.isEmpty()) {
      return first;
    }
    LocalDate recent = stored.get(Math.max(0, stored.size() - ALWAYS_RECOMPUTED_DAYS)).getTradeDate();
    LocalDateTime since = stored.stream()
      .map(AccountProfitDaily::getComputedAt)
      .max(LocalDateTime::compareTo)
      .orElseThrow()
      .minus(CHANGE_MARGIN);
    LocalDate changed = earliest(Stream.concat(
      trades.stream()
        .filter(trade -> after(trade.getCreatedAt(), since) || after(trade.getCancelledAt(), since))
        .map(Trade::getTradeDate),
      payouts.stream()
        .filter(payout -> after(payout.getUpdatedAt(), since))
        .map(DividendPayout::getExDate)));
    return changed != null && changed.isBefore(recent) ? changed : recent;
  }

  private static boolean after(LocalDateTime at, LocalDateTime since) {
    return at != null && at.isAfter(since);
  }

  private static LocalDate earliest(Stream<LocalDate> dates) {
    return dates.min(LocalDate::compareTo).orElse(null);
  }

  // 거래한 종목들의 캔들 날짜 · 거래일 · 배당락일로 거래일을 잡고 그 날짜 이하 가장 늦은 종가 · 환율로 계산
  private List<DailyProfit> compute(List<Trade> trades, List<DividendPayout> payouts, LocalDate from,
    LocalDate today) {
    List<Trade> completed = trades.stream()
      .filter(trade -> trade.getStatus() == TradeStatus.COMPLETED)
      .toList();
    TreeSet<LocalDate> days = new TreeSet<>();
    Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new HashMap<>();
    LocalDate start = from.minusDays(CLOSE_LOOKBACK_DAYS);
    Map<String, LocalDate> firstTradeDates = completed.stream()
      .collect(Collectors.toMap(Trade::getSymbol, Trade::getTradeDate, (a, b) -> a.isBefore(b) ? a : b));
    for (String symbol : new TreeSet<>(firstTradeDates.keySet())) {
      NavigableMap<LocalDate, BigDecimal> symbolCloses = closesBetween(symbol, start, today);
      LocalDate firstTrade = firstTradeDates.get(symbol);
      // 앞 14일 안에 종가가 없는 종목(거래 정지 · 상장 폐지)은 그 앞 마지막 종가
      if (symbolCloses.floorKey(from) == null && firstTrade.isBefore(start)) {
        Map.Entry<LocalDate, BigDecimal> earlier = closesBetween(symbol, firstTrade, start.minusDays(1)).lastEntry();
        if (earlier != null) {
          symbolCloses.put(earlier.getKey(), earlier.getValue());
        }
      }
      closes.put(symbol, symbolCloses);
      days.addAll(symbolCloses.subMap(from, true, today, true).keySet());
    }
    completed.forEach(trade -> days.add(trade.getTradeDate()));
    payouts.forEach(payout -> days.add(payout.getExDate()));
    List<LocalDate> range = List.copyOf(days.subSet(from, true, today, true));

    Map<LocalDate, BigDecimal> fxRates = new HashMap<>();
    marketServiceClientWrapper.getBulkFxRates(range.stream().map(LocalDate::toString).toList())
      .forEach((date, rate) -> fxRates.put(LocalDate.parse(date), rate));
    return ProfitFlowCalculator.calculate(trades, payouts, closes, fxRates, range,
      tradeProperties.getCalculation().getPricePrecision());
  }

  private NavigableMap<LocalDate, BigDecimal> closesBetween(String symbol, LocalDate from, LocalDate to) {
    NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
    for (OHLCPriceDto candle : marketServiceClientWrapper.getOHLCPriceRange(symbol, from.toString(), to.toString())) {
      if (candle.closePrice() != null) {
        closes.put(candle.date(), candle.closePrice());
      }
    }
    return closes;
  }

  // 같은 계좌를 다른 요청이 먼저 저장했으면 저장만 넘김
  private void save(Long accountId, LocalDate from, List<AccountProfitDaily> rows) {
    try {
      accountProfitStore.replaceFrom(accountId, from, rows);
    } catch (DataIntegrityViolationException e) {
      log.info("수익 흐름 저장이 다른 요청과 겹쳐 넘어감: accountId={}, from={}", accountId, from);
    }
  }

  // 계좌마다의 점(수익 · 투자 원금)을 날짜마다 더함(그 날짜에 점이 없는 계좌는 직전 점, 첫 점 전은 0)
  private static List<ProfitPoint> sumByDate(List<List<ProfitPoint>> series) {
    if (series.size() == 1) {
      return series.getFirst();
    }
    List<NavigableMap<LocalDate, ProfitPoint>> byDate = new ArrayList<>();
    TreeSet<LocalDate> dates = new TreeSet<>();
    for (List<ProfitPoint> points : series) {
      NavigableMap<LocalDate, ProfitPoint> map = new TreeMap<>();
      points.forEach(point -> map.put(point.date(), point));
      byDate.add(map);
      dates.addAll(map.keySet());
    }
    return dates.stream()
      .map(date -> {
        List<ProfitPoint> latest = byDate.stream()
          .map(map -> map.floorEntry(date))
          .filter(Objects::nonNull)
          .map(Map.Entry::getValue)
          .toList();
        return new ProfitPoint(date,
          latest.stream().map(ProfitPoint::profitKrw).reduce(BigDecimal.ZERO, BigDecimal::add),
          latest.stream().map(ProfitPoint::investedKrw).reduce(BigDecimal.ZERO, BigDecimal::add));
      })
      .toList();
  }
}
