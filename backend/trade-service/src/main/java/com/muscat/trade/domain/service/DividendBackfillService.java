package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.dto.response.PayoutDiffResponse;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.UserServiceClient;
import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.trade.infra.client.dto.DividendSettlementDto;
import com.muscat.trade.infra.kafka.DividendReconcileProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 배당 몫 백필과 어긋남 미리 보기
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendBackfillService {

  private final TradeRepository tradeRepository;
  private final DividendPayoutRepository dividendPayoutRepository;
  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final UserServiceClient userServiceClient;
  private final DividendReconcileProducer dividendReconcileProducer;

  public int requestAll() {
    Set<TradePosition> positions = positions();
    positions.forEach(p -> dividendReconcileProducer.request(p.userId(), p.accountId(), p.symbol()));
    log.info("배당 몫 백필 요청: {} 자리", positions.size());
    return positions.size();
  }

  @Transactional(readOnly = true)
  public List<PayoutDiffResponse> diff() {
    Map<PayoutKey, BigDecimal> settled = new HashMap<>();
    for (DividendSettlementDto s : userServiceClient.getDividendSettlements()) {
      settled.merge(new PayoutKey(s.accountId(), s.symbol(), s.exDate()), s.amount(), BigDecimal::add);
    }

    Map<TradePosition, List<Trade>> tradesByPosition = new LinkedHashMap<>();
    for (TradePosition p : positions()) {
      tradesByPosition.put(p, tradeRepository.findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(
        p.userId(), p.accountId(), p.symbol(), TradeStatus.COMPLETED));
    }

    Map<String, List<DividendDto>> dividendsBySymbol = dividendsBySymbol(tradesByPosition);
    Map<PayoutKey, BigDecimal> targets = new HashMap<>();
    Map<Long, String> userByAccount = new HashMap<>();
    tradesByPosition.forEach((p, trades) -> {
      userByAccount.put(p.accountId(), p.userId());
      DividendPayoutService.computeTargets(trades, dividendsBySymbol.getOrDefault(p.symbol(), List.of()))
        .forEach((exDate, amounts) -> targets.put(new PayoutKey(p.accountId(), p.symbol(), exDate), amounts.netAmount()));
    });

    Map<Long, AccountDiff> byAccount = new TreeMap<>();
    Set<PayoutKey> keys = new HashSet<>(settled.keySet());
    keys.addAll(targets.keySet());
    for (PayoutKey key : keys) {
      BigDecimal settledAmount = settled.getOrDefault(key, BigDecimal.ZERO);
      BigDecimal targetAmount = targets.getOrDefault(key, BigDecimal.ZERO);
      byAccount.computeIfAbsent(key.accountId(), id -> new AccountDiff()).add(settledAmount, targetAmount);
    }

    List<PayoutDiffResponse> diff = new ArrayList<>();
    byAccount.forEach((accountId, d) -> {
      if (d.differingRows > 0) {
        diff.add(new PayoutDiffResponse(userByAccount.get(accountId), accountId, d.settled, d.target,
          d.target.subtract(d.settled), d.differingRows));
      }
    });
    return diff;
  }

  // 체결이 다 취소된 자리도 몫을 0 으로 돌리려고 몫 표의 자리를 같이 모음
  private Set<TradePosition> positions() {
    Set<TradePosition> positions = new LinkedHashSet<>(tradeRepository.findDistinctPositions(TradeStatus.COMPLETED));
    positions.addAll(dividendPayoutRepository.findDistinctPositions());
    return positions;
  }

  // 종목마다 배당 목록을 한 번만 받음. 그 종목 거래 중 가장 이른 날부터
  private Map<String, List<DividendDto>> dividendsBySymbol(Map<TradePosition, List<Trade>> tradesByPosition) {
    Map<String, LocalDate> firstTradeDate = new HashMap<>();
    tradesByPosition.forEach((p, trades) -> trades.forEach(t ->
      firstTradeDate.merge(p.symbol(), t.getTradeDate(), (a, b) -> a.isBefore(b) ? a : b)));
    Map<String, List<DividendDto>> dividends = new HashMap<>();
    String today = LocalDate.now().toString();
    firstTradeDate.forEach((symbol, from) ->
      dividends.put(symbol, marketServiceClientWrapper.getDividends(symbol, from.toString(), today)));
    return dividends;
  }

  private record PayoutKey(Long accountId, String symbol, LocalDate exDate) {
  }

  private static final class AccountDiff {

    private BigDecimal settled = BigDecimal.ZERO;
    private BigDecimal target = BigDecimal.ZERO;
    private int differingRows;

    void add(BigDecimal settledAmount, BigDecimal targetAmount) {
      settled = settled.add(settledAmount);
      target = target.add(targetAmount);
      if (settledAmount.compareTo(targetAmount) != 0) {
        differingRows++;
      }
    }
  }
}
