package com.muscat.trade.domain.service;

import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.StockSplit;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.StockSplitRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.kafka.DividendReconcileProducer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// 분할 전 체결을 분할 기준 수량 · 체결가로 맞추고 보유 · 배당 몫을 다시 계산
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSplitService {

  // trades.quantity 컬럼의 소수 자리
  private static final int QUANTITY_SCALE = 6;

  private final TradeRepository tradeRepository;
  private final StockSplitRepository stockSplitRepository;
  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final HoldingsRebuilder holdingsRebuilder;
  private final DividendReconcileProducer dividendReconcileProducer;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  // 분할이 반영된 첫 거래일과 분할 전 1주가 분할 뒤 몇 주인지
  public record Split(LocalDate date, BigDecimal ratio) {
  }

  // 맞춘 체결 수와 보내지 못한 배당 몫 재계산 요청 수
  public record Outcome(int adjusted, int reconcileFailed) {
  }

  private record Applied(int trades, List<TradePosition> positions) {
  }

  private record DayRange(BigDecimal low, BigDecimal high) {
  }

  // 수량을 한 번에 반올림하는 체결 묶음
  private record QuantityGroup(Long accountId, TradeType type, TradeStatus status) {
  }

  // 분할을 날짜 순으로 다시 판정해 체결가에 아직 안 들어간 것만 반영(시세 조회는 트랜잭션 시작 전, 기록 여부와 상관없이 판정)
  public Outcome apply(String symbol, List<Split> splits) {
    List<Split> valid = splits.stream()
      .filter(StockSplitService::valid)
      .sorted(Comparator.comparing(Split::date))
      .toList();
    if (valid.isEmpty()) {
      return new Outcome(0, 0);
    }

    Map<LocalDate, DayRange> ranges = rangesBefore(symbol, valid.getLast().date());
    Applied applied = adjustRetryingDuplicate(symbol, valid, ranges);
    int reconcileFailed = (int) applied.positions().stream().filter(p -> !requestDividendReconcile(p)).count();
    log.info("분할 반영: symbol={}, splits={}, 맞춘 체결={}, 자리={}, 재계산 요청 실패={}", symbol, valid,
      applied.trades(), applied.positions().size(), reconcileFailed);
    return new Outcome(applied.trades(), reconcileFailed);
  }

  // 같은 분할을 동시에 반영한 트랜잭션이 먼저 기록해 유니크 제약에 걸리면 한 번 더(기록된 분할이 없으면 유니크 제약이 아닌 위반이라 그대로 예외)
  private Applied adjustRetryingDuplicate(String symbol, List<Split> splits, Map<LocalDate, DayRange> ranges) {
    try {
      return Objects.requireNonNull(transactionTemplate.execute(status -> adjust(symbol, splits, ranges)));
    } catch (DataIntegrityViolationException e) {
      boolean recorded = splits.stream()
        .anyMatch(split -> stockSplitRepository.existsBySymbolAndSplitDate(symbol, split.date()));
      if (!recorded) {
        throw e;
      }
      log.info("같은 분할이 먼저 기록돼 다시 반영: symbol={}, splits={}", symbol, splits);
      return Objects.requireNonNull(transactionTemplate.execute(status -> adjust(symbol, splits, ranges)));
    }
  }

  // 체결마다 체결가에 아직 안 들어간 뒤쪽 분할만 곱하고, 바뀐 자리의 보유를 다시 쌓고 분할마다 맞춘 수를 기록
  private Applied adjust(String symbol, List<Split> splits, Map<LocalDate, DayRange> ranges) {
    List<BigDecimal> ratios = splits.stream().map(Split::ratio).toList();
    List<Trade> trades = tradeRepository.findBySymbolAndTradeDateBeforeOrderByIdAsc(symbol, splits.getLast().date());
    LocalDateTime now = LocalDateTime.now(clock);
    int[] adjustedBySplit = new int[splits.size()];
    Map<Trade, BigDecimal> factors = new LinkedHashMap<>();

    for (Trade trade : trades) {
      int later = (int) splits.stream().filter(split -> split.date().isAfter(trade.getTradeDate())).count();
      DayRange range = ranges.get(trade.getTradeDate());
      if (range == null) {
        log.warn("체결일 시세가 없어 분할 반영을 멈춤: tradeId={}, symbol={}, tradeDate={}",
          trade.getId(), symbol, trade.getTradeDate());
        throw new TradeException(TradeResponse.PRICE_DATA_UNAVAILABLE);
      }
      OptionalInt count = SplitPricing.pendingCount(trade.getPrice(), range.low(), range.high(),
        ratios.subList(ratios.size() - later, ratios.size()));
      if (count.isEmpty()) {
        log.warn("체결가로 분할 전후를 가리지 못해 맞추지 않음: tradeId={}, symbol={}, tradeDate={}, price={}",
          trade.getId(), symbol, trade.getTradeDate(), trade.getPrice());
        continue;
      }
      if (count.getAsInt() == 0) {
        continue;
      }
      factors.put(trade, SplitPricing.lastProduct(ratios, count.getAsInt()));
      for (int i = splits.size() - count.getAsInt(); i < splits.size(); i++) {
        adjustedBySplit[i]++;
      }
    }

    applyQuantities(factors, now);
    Set<TradePosition> positions = factors.keySet().stream()
      .map(trade -> new TradePosition(trade.getUserId(), trade.getAccountId(), symbol))
      .collect(Collectors.toCollection(LinkedHashSet::new));
    positions.forEach(p -> holdingsRebuilder.rebuild(p.userId(), p.accountId(), p.symbol(), false));
    record(symbol, splits, adjustedBySplit, now);
    return new Applied(factors.size(), List.copyOf(positions));
  }

  // 계좌 · 매수매도 · 상태마다 거래일 순서로 수량 × 비율을 누적해 반올림하고 직전 누적과의 차이를 체결 수량으로
  private static void applyQuantities(Map<Trade, BigDecimal> factors, LocalDateTime at) {
    Map<QuantityGroup, List<Trade>> groups = factors.keySet().stream()
      .sorted(TradeOrder.LEDGER)
      .collect(Collectors.groupingBy(t -> new QuantityGroup(t.getAccountId(), t.getTradeType(), t.getStatus()),
        LinkedHashMap::new, Collectors.toList()));
    for (List<Trade> group : groups.values()) {
      BigDecimal exact = BigDecimal.ZERO;
      BigDecimal rounded = BigDecimal.ZERO;
      for (Trade trade : group) {
        BigDecimal factor = factors.get(trade);
        exact = exact.add(trade.getQuantity().multiply(factor));
        BigDecimal next = exact.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        trade.applySplit(factor, next.subtract(rounded), at);
        rounded = next;
      }
    }
  }

  // 분할마다 맞춘 체결 수를 더해 기록(처음 보는 분할은 새 줄)
  private void record(String symbol, List<Split> splits, int[] adjustedBySplit, LocalDateTime now) {
    for (int i = 0; i < splits.size(); i++) {
      Split split = splits.get(i);
      Optional<StockSplit> stored = stockSplitRepository.findBySymbolAndSplitDate(symbol, split.date());
      if (stored.isEmpty()) {
        stockSplitRepository.save(StockSplit.of(symbol, split.date(), split.ratio(), adjustedBySplit[i], now));
      } else if (adjustedBySplit[i] > 0) {
        stored.get().addAdjusted(adjustedBySplit[i], now);
      }
    }
  }

  // 분할일 전 가장 이른 체결의 거래일부터 분할 전날까지 저가 · 고가(비었으면 종가, 체결이 없으면 묻지 않음)
  private Map<LocalDate, DayRange> rangesBefore(String symbol, LocalDate splitDate) {
    Optional<Trade> first = tradeRepository.findFirstBySymbolAndTradeDateBeforeOrderByTradeDateAsc(symbol, splitDate);
    if (first.isEmpty()) {
      return Map.of();
    }
    return marketServiceClientWrapper.getOHLCPriceRange(symbol, first.get().getTradeDate().toString(),
        splitDate.minusDays(1).toString()).stream()
      .filter(candle -> candle.date() != null && candle.closePrice() != null)
      .collect(Collectors.toMap(OHLCPriceDto::date, StockSplitService::rangeOf, (a, b) -> a));
  }

  private static DayRange rangeOf(OHLCPriceDto candle) {
    return new DayRange(Objects.requireNonNullElse(candle.lowPrice(), candle.closePrice()),
      Objects.requireNonNullElse(candle.highPrice(), candle.closePrice()));
  }

  private boolean requestDividendReconcile(TradePosition position) {
    try {
      dividendReconcileProducer.request(position.userId(), position.accountId(), position.symbol());
      return true;
    } catch (RuntimeException e) {
      log.warn("분할 뒤 배당 몫 재계산 요청 실패: accountId={}, symbol={}, error={}",
        position.accountId(), position.symbol(), e.getMessage());
      return false;
    }
  }

  private static boolean valid(Split split) {
    return split.date() != null && split.ratio() != null && split.ratio().signum() > 0
      && split.ratio().compareTo(BigDecimal.ONE) != 0;
  }
}
