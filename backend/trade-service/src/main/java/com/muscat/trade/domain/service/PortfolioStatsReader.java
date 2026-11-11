package com.muscat.trade.domain.service;

import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 계좌 통계 계산용 계좌별 체결 · 배당 몫과 종가 · 환율 읽기(계좌 값 해석 · 주인 확인 포함)
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioStatsReader {

  static final String ALL_ACCOUNTS = "all";
  static final int CLOSE_LOOKBACK_DAYS = 14;

  private final TradeRepository tradeRepository;
  private final DividendPayoutRepository dividendPayoutRepository;
  private final MarketServiceClientWrapper marketServiceClientWrapper;

  // 계좌 하나의 모든 상태 체결과 배당 몫
  public record AccountActivity(Long accountId, List<Trade> trades, List<DividendPayout> payouts) {
  }

  // accountId 는 계좌 ID 또는 all, 남의 계좌면 접근 거부, 체결이 있는 계좌만 계좌 ID 순
  public List<AccountActivity> activities(String userId, String accountId) {
    Long account = account(userId, accountId);
    List<Trade> trades = account == null
      ? tradeRepository.findByUserId(userId)
      : tradeRepository.findByUserIdAndAccountId(userId, account);
    Map<Long, List<DividendPayout>> payouts = dividendPayoutRepository.findByUserId(userId).stream()
      .collect(Collectors.groupingBy(DividendPayout::getAccountId));
    return trades.stream()
      .collect(Collectors.groupingBy(Trade::getAccountId, TreeMap::new, Collectors.toList()))
      .entrySet().stream()
      .map(entry -> new AccountActivity(entry.getKey(), entry.getValue(),
        payouts.getOrDefault(entry.getKey(), List.of())))
      .toList();
  }

  // 계좌 ID(all 이면 null), 남의 계좌면 접근 거부
  public Long account(String userId, String accountId) {
    Long account = parseAccount(accountId);
    if (account != null) {
      checkOwner(userId, account);
    }
    return account;
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

  // 체결 테이블의 계좌 주인 확인(체결이 없는 계좌는 넘어감)
  private void checkOwner(String userId, Long accountId) {
    tradeRepository.findFirstByAccountIdOrderByIdAsc(accountId)
      .filter(trade -> !trade.getUserId().equals(userId))
      .ifPresent(trade -> {
        throw new TradeException(TradeResponse.ACCOUNT_ACCESS_DENIED);
      });
  }

  // from 앞 14일부터 to 까지 종가
  public NavigableMap<LocalDate, BigDecimal> closes(String symbol, LocalDate from, LocalDate to,
    LocalDate firstTrade) {
    LocalDate start = from.minusDays(CLOSE_LOOKBACK_DAYS);
    NavigableMap<LocalDate, BigDecimal> closes = closesBetween(symbol, start, to);
    // 앞 14일 안에 종가가 없는 종목(거래 정지 · 상장 폐지)은 첫 체결일부터 찾은 그 앞 마지막 종가
    if (closes.floorKey(from) == null && firstTrade.isBefore(start)) {
      Map.Entry<LocalDate, BigDecimal> earlier = closesBetween(symbol, firstTrade, start.minusDays(1)).lastEntry();
      if (earlier != null) {
        closes.put(earlier.getKey(), earlier.getValue());
      }
    }
    return closes;
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

  // 날짜마다 그 날짜 이하 가장 늦은 환율(환율이 없는 날짜는 빠짐, 날짜가 없으면 호출 안 함)
  public Map<LocalDate, BigDecimal> fxRates(Collection<LocalDate> dates) {
    Map<LocalDate, BigDecimal> rates = new HashMap<>();
    if (dates.isEmpty()) {
      return rates;
    }
    marketServiceClientWrapper.getBulkFxRates(dates.stream().map(LocalDate::toString).toList())
      .forEach((date, rate) -> rates.put(LocalDate.parse(date), rate));
    return rates;
  }
}
