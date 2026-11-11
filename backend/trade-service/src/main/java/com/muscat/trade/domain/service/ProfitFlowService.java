package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.FlowPeriod;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.entity.AccountProfitDaily;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import com.muscat.trade.domain.service.PortfolioStatsReader.AccountActivity;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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

  static final int ALWAYS_RECOMPUTED_DAYS = 7;
  static final Duration CHANGE_MARGIN = Duration.ofMinutes(1);

  private final PortfolioStatsReader portfolioStatsReader;
  private final AccountProfitDailyRepository accountProfitDailyRepository;
  private final AccountProfitStore accountProfitStore;
  private final TradeProperties tradeProperties;
  private final Clock clock;

  // accountId 는 계좌 ID 또는 all, 남의 계좌면 접근 거부
  public ProfitFlowResponse flow(String userId, String accountId, FlowPeriod period) {
    List<AccountActivity> activities = portfolioStatsReader.activities(userId, accountId);
    LocalDate today = LocalDate.now(clock);
    LocalDate start = period.startFrom(today);
    return ProfitFlowCalculator.summarize(points(userId, activities, today).stream()
      .filter(point -> start == null || !point.date().isBefore(start))
      .toList());
  }

  // 계좌마다 첫 거래일부터 오늘까지의 점을 날짜마다 더한 값
  public List<ProfitPoint> points(String userId, List<AccountActivity> activities, LocalDate today) {
    return ProfitFlowCalculator.sumByDate(activities.stream()
      .map(activity -> refresh(userId, activity, today))
      .toList());
  }

  // 한 계좌의 점(정산 전 매수의 거래일부터는 저장하지 않음)
  private List<ProfitPoint> refresh(String userId, AccountActivity activity, LocalDate today) {
    Long accountId = activity.accountId();
    List<Trade> trades = activity.trades();
    List<DividendPayout> payouts = activity.payouts();
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
    Map<String, LocalDate> firstTradeDates = completed.stream()
      .collect(Collectors.toMap(Trade::getSymbol, Trade::getTradeDate, (a, b) -> a.isBefore(b) ? a : b));
    for (String symbol : new TreeSet<>(firstTradeDates.keySet())) {
      NavigableMap<LocalDate, BigDecimal> symbolCloses =
        portfolioStatsReader.closes(symbol, from, today, firstTradeDates.get(symbol));
      closes.put(symbol, symbolCloses);
      days.addAll(symbolCloses.subMap(from, true, today, true).keySet());
    }
    completed.forEach(trade -> days.add(trade.getTradeDate()));
    payouts.forEach(payout -> days.add(payout.getExDate()));
    List<LocalDate> range = List.copyOf(days.subSet(from, true, today, true));

    return ProfitFlowCalculator.calculate(trades, payouts, closes, portfolioStatsReader.fxRates(range), range,
      tradeProperties.getCalculation().getPricePrecision());
  }

  // 같은 계좌를 다른 요청이 먼저 저장했으면 저장만 넘김
  private void save(Long accountId, LocalDate from, List<AccountProfitDaily> rows) {
    try {
      accountProfitStore.replaceFrom(accountId, from, rows);
    } catch (DataIntegrityViolationException e) {
      log.info("수익 흐름 저장이 다른 요청과 겹쳐 넘어감: accountId={}, from={}", accountId, from);
    }
  }
}
