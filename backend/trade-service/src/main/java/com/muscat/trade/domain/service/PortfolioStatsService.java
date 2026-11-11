package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.FlowPeriod;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.Replay;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.SellResult;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.SymbolTotals;
import com.muscat.trade.domain.service.PortfolioStatsReader.AccountActivity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 계좌 통계 탭 조회(열 때마다 체결 · 배당 몫 · 종가 · 환율로 계산)
@Service
@RequiredArgsConstructor
public class PortfolioStatsService {

  private final PortfolioStatsReader portfolioStatsReader;
  private final ProfitFlowService profitFlowService;
  private final TradeProperties tradeProperties;
  private final Clock clock;

  // 달마다 수익 · 기간 안 매도 · 기간 끝에 들고 있는 종목, accountId 는 계좌 ID 또는 all
  public ProfitDetailResponse profit(String userId, String accountId, FlowPeriod period) {
    List<AccountActivity> activities = portfolioStatsReader.activities(userId, accountId);
    LocalDate today = LocalDate.now(clock);
    LocalDate start = period.startFrom(today);
    List<Replay> replays = replay(activities, today);
    List<SellResult> sells = replays.stream()
      .flatMap(replay -> replay.sells().stream())
      .filter(sell -> start == null || !sell.trade().getTradeDate().isBefore(start))
      .toList();
    List<SymbolTotals> holdings = PortfolioStatsCalculator.merge(replays).values().stream()
      .filter(SymbolTotals::held)
      .toList();

    Set<LocalDate> fxDates = new TreeSet<>();
    sells.forEach(sell -> fxDates.add(sell.trade().getTradeDate()));
    Map<String, LocalDate> closeFrom = new HashMap<>();
    holdings.forEach(totals -> closeFrom.put(totals.symbol(), today));
    if (!holdings.isEmpty()) {
      fxDates.add(today);
    }
    return PortfolioStatsCalculator.profitDetail(profitFlowService.points(userId, activities, today), start, sells,
      holdings, closes(activities, closeFrom, today), portfolioStatsReader.fxRates(fxDates), today);
  }

  // 종목마다 기간 끝 값 − 기간 시작 전날 값, accountId 는 계좌 ID 또는 all
  public SymbolContributionResponse symbols(String userId, String accountId, FlowPeriod period) {
    List<AccountActivity> activities = portfolioStatsReader.activities(userId, accountId);
    LocalDate today = LocalDate.now(clock);
    LocalDate start = period.startFrom(today);
    LocalDate before = start == null ? null : start.minusDays(1);
    Map<String, SymbolTotals> end = PortfolioStatsCalculator.merge(replay(activities, today));
    Map<String, SymbolTotals> previous = before == null
      ? Map.of()
      : PortfolioStatsCalculator.merge(replay(activities, before));

    Set<LocalDate> fxDates = new TreeSet<>();
    Map<String, LocalDate> closeFrom = new HashMap<>();
    end.values().stream().filter(SymbolTotals::held).forEach(totals -> closeFrom.put(totals.symbol(), today));
    previous.values().stream().filter(SymbolTotals::held).forEach(totals -> closeFrom.put(totals.symbol(), before));
    if (!end.isEmpty()) {
      fxDates.add(today);
    }
    if (!previous.isEmpty()) {
      fxDates.add(before);
    }
    return PortfolioStatsCalculator.symbolContributions(end, previous, closes(activities, closeFrom, today),
      portfolioStatsReader.fxRates(fxDates), today, before);
  }

  private List<Replay> replay(List<AccountActivity> activities, LocalDate until) {
    int pricePrecision = tradeProperties.getCalculation().getPricePrecision();
    return activities.stream()
      .map(activity -> PortfolioStatsCalculator.replay(activity.trades(), activity.payouts(), until, pricePrecision))
      .toList();
  }

  // 종목마다 종가가 필요한 가장 이른 날짜(closeFrom)부터 오늘까지 종가
  private Map<String, NavigableMap<LocalDate, BigDecimal>> closes(List<AccountActivity> activities,
    Map<String, LocalDate> closeFrom, LocalDate today) {
    Map<String, LocalDate> firstTrades = activities.stream()
      .flatMap(activity -> activity.trades().stream())
      .filter(trade -> trade.getStatus() == TradeStatus.COMPLETED)
      .collect(Collectors.toMap(Trade::getSymbol, Trade::getTradeDate, (a, b) -> a.isBefore(b) ? a : b));
    Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new HashMap<>();
    new TreeMap<>(closeFrom).forEach((symbol, from) ->
      closes.put(symbol, portfolioStatsReader.closes(symbol, from, today, firstTrades.get(symbol))));
    return closes;
  }
}
