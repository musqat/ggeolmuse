package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.trade.infra.kafka.DividendPayoutProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 계좌 · 종목의 배당 몫을 거래 기록으로 다시 계산해 바뀐 몫만 저장하고 목표 금액을 보냄
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendPayoutService {

  private final TradeRepository tradeRepository;
  private final DividendPayoutRepository dividendPayoutRepository;
  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final DividendPayoutProducer dividendPayoutProducer;
  private final TransactionTemplate transactionTemplate;

  public int reconcile(String userId, Long accountId, String symbol) {
    return reconcile(userId, accountId, symbol, false, List.of());
  }

  // resend 면 바뀌지 않은 몫도 다시 보냄(settledExDates 는 user 가 넣은 배당락일, 목표가 없으면 0 몫)
  // 배당 목록은 트랜잭션 밖에서 받고 몫 저장 · 발행만 트랜잭션 안
  public int reconcile(String userId, Long accountId, String symbol, boolean resend,
    Collection<LocalDate> settledExDates) {
    List<Trade> trades = tradeRepository.findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(
      userId, accountId, symbol, TradeStatus.COMPLETED);
    Map<LocalDate, DividendAmounts> targets = new TreeMap<>(trades.isEmpty()
      ? Map.of()
      : computeTargets(trades, fetchDividends(symbol, trades)));
    Integer changed = transactionTemplate.execute(status ->
      apply(userId, accountId, symbol, resend, settledExDates, targets));
    return changed == null ? 0 : changed;
  }

  private int apply(String userId, Long accountId, String symbol, boolean resend,
    Collection<LocalDate> settledExDates, Map<LocalDate, DividendAmounts> targets) {
    Map<LocalDate, DividendPayout> existing = dividendPayoutRepository.findByAccountIdAndSymbol(accountId, symbol)
      .stream()
      .collect(Collectors.toMap(DividendPayout::getExDate, Function.identity(), (a, b) -> a, TreeMap::new));
    for (LocalDate exDate : settledExDates) {
      DividendPayout row = existing.get(exDate);
      targets.putIfAbsent(exDate,
        DividendAmounts.of(row != null ? row.getDividendPerShare() : BigDecimal.ZERO, BigDecimal.ZERO));
    }

    LocalDateTime now = LocalDateTime.now();
    int changed = 0;
    int resent = 0;
    for (Map.Entry<LocalDate, DividendAmounts> target : targets.entrySet()) {
      DividendPayout row = existing.remove(target.getKey());
      if (row == null) {
        row = DividendPayout.of(userId, accountId, symbol, target.getKey(), target.getValue(), now);
      } else if (row.matches(target.getValue())) {
        resent += publishIf(resend, row);
        continue;
      } else {
        row.apply(target.getValue(), now);
      }
      saveAndPublish(row);
      changed++;
    }
    // 배당 목록에서 빠졌거나 체결이 다 없어진 배당락일
    for (DividendPayout row : existing.values()) {
      if (row.getShares().signum() == 0 && row.getNetAmount().signum() == 0) {
        resent += publishIf(resend, row);
        continue;
      }
      row.apply(DividendAmounts.of(row.getDividendPerShare(), BigDecimal.ZERO), now);
      saveAndPublish(row);
      changed++;
    }
    log.info("배당 몫 재계산: accountId={}, symbol={}, 바뀐 몫={}, 다시 보낸 몫={}", accountId, symbol, changed, resent);
    return changed;
  }

  // 사용자 보유 전체의 받은 배당을 계좌 · 종목마다(거래와 몫은 한 번씩만 읽음)
  @Transactional(readOnly = true)
  public Map<TradePosition, BigDecimal> dividendsOnHeldShares(String userId) {
    Map<TradePosition, List<Trade>> tradesByPosition = tradeRepository
      .findByUserIdAndStatusOrderByExecutedAtAscIdAsc(userId, TradeStatus.COMPLETED).stream()
      .collect(Collectors.groupingBy(t -> new TradePosition(t.getUserId(), t.getAccountId(), t.getSymbol())));
    Map<TradePosition, List<DividendPayout>> payoutsByPosition = dividendPayoutRepository.findByUserId(userId).stream()
      .collect(Collectors.groupingBy(p -> new TradePosition(p.getUserId(), p.getAccountId(), p.getSymbol())));

    Map<TradePosition, BigDecimal> totals = new HashMap<>();
    tradesByPosition.forEach((position, trades) ->
      totals.put(position, heldDividends(trades, payoutsByPosition.getOrDefault(position, List.of()))));
    return totals;
  }

  // 지금 남은 매수 묶음은 매수일 뒤 배당을 다 받은 셈(판 주식이 받은 배당은 뺌)
  private static BigDecimal heldDividends(List<Trade> trades, List<DividendPayout> payouts) {
    BigDecimal total = BigDecimal.ZERO;
    for (DividendEntitlement.HeldBuy lot : DividendEntitlement.remainingByBuyBefore(trades, LocalDate.now().plusDays(1))) {
      for (DividendPayout payout : payouts) {
        if (payout.getShares().signum() > 0 && lot.buy().getTradeDate().isBefore(payout.getExDate())) {
          total = total.add(DividendAmounts.of(payout.getDividendPerShare(), lot.shares()).netAmount());
        }
      }
    }
    return total;
  }

  // 배당락일마다 그 전날 보유로 센 몫(보유가 0 이면 0 몫)
  public static Map<LocalDate, DividendAmounts> computeTargets(List<Trade> trades, List<DividendDto> dividends) {
    Map<LocalDate, DividendAmounts> targets = new TreeMap<>();
    for (DividendDto dividend : dividends) {
      LocalDate exDate = LocalDate.parse(dividend.exDate());
      targets.put(exDate, DividendAmounts.of(dividend.amount(), DividendEntitlement.sharesHeldBefore(trades, exDate)));
    }
    return targets;
  }

  private List<DividendDto> fetchDividends(String symbol, List<Trade> trades) {
    LocalDate firstTradeDate = trades.stream().map(Trade::getTradeDate).min(Comparator.naturalOrder()).orElseThrow();
    return marketServiceClientWrapper.getDividends(symbol, firstTradeDate.toString(), LocalDate.now().toString());
  }

  private void saveAndPublish(DividendPayout row) {
    dividendPayoutRepository.save(row);
    dividendPayoutProducer.publish(row);
  }

  private int publishIf(boolean resend, DividendPayout row) {
    if (!resend) {
      return 0;
    }
    dividendPayoutProducer.publish(row);
    return 1;
  }
}
