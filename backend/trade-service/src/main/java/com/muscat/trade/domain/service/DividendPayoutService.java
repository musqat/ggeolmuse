package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 계좌 · 종목의 배당 몫을 거래 기록으로 다시 계산해 바뀐 몫만 저장하고 목표 금액을 보냄
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendPayoutService {

  private final TradeRepository tradeRepository;
  private final DividendPayoutRepository dividendPayoutRepository;
  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final DividendPayoutProducer dividendPayoutProducer;

  @Transactional
  public int reconcile(String userId, Long accountId, String symbol) {
    List<Trade> trades = tradeRepository.findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(
      userId, accountId, symbol, TradeStatus.COMPLETED);
    Map<LocalDate, DividendPayout> existing = dividendPayoutRepository.findByAccountIdAndSymbol(accountId, symbol)
      .stream()
      .collect(Collectors.toMap(DividendPayout::getExDate, Function.identity(), (a, b) -> a, TreeMap::new));
    Map<LocalDate, DividendAmounts> targets = trades.isEmpty()
      ? Map.of()
      : computeTargets(trades, fetchDividends(symbol, trades));

    LocalDateTime now = LocalDateTime.now();
    int changed = 0;
    for (Map.Entry<LocalDate, DividendAmounts> target : targets.entrySet()) {
      DividendPayout row = existing.remove(target.getKey());
      if (row == null) {
        row = DividendPayout.of(userId, accountId, symbol, target.getKey(), target.getValue(), now);
      } else if (row.matches(target.getValue())) {
        continue;
      } else {
        row.apply(target.getValue(), now);
      }
      saveAndPublish(row);
      changed++;
    }
    // 이제 받을 몫이 없는 배당락일
    for (DividendPayout row : existing.values()) {
      if (row.getShares().signum() == 0 && row.getNetAmount().signum() == 0) {
        continue;
      }
      row.apply(DividendAmounts.of(row.getDividendPerShare(), BigDecimal.ZERO), now);
      saveAndPublish(row);
      changed++;
    }
    log.info("배당 몫 재계산: accountId={}, symbol={}, 바뀐 몫={}", accountId, symbol, changed);
    return changed;
  }

  // 배당락일마다 그 전날 보유로 센 몫. 보유가 0 인 배당락일은 뺀다
  public static Map<LocalDate, DividendAmounts> computeTargets(List<Trade> trades, List<DividendDto> dividends) {
    Map<LocalDate, DividendAmounts> targets = new TreeMap<>();
    for (DividendDto dividend : dividends) {
      LocalDate exDate = LocalDate.parse(dividend.exDate());
      BigDecimal shares = DividendEntitlement.sharesHeldBefore(trades, exDate);
      if (shares.signum() > 0) {
        targets.put(exDate, DividendAmounts.of(dividend.amount(), shares));
      }
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
}
