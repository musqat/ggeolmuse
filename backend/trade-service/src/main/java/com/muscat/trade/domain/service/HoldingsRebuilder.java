package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 취소 안 된 체결을 반영 순서대로 다시 쌓아 한 종목 보유를 덮어쓴다
@Slf4j
@Component
public class HoldingsRebuilder {

  private final TradeRepository tradeRepository;
  private final HoldingsRepository holdingsRepository;
  private final TradeProperties tradeProperties;
  private final TradeLogger tradeLogger;
  private final Counter uncoveredSells;

  public HoldingsRebuilder(TradeRepository tradeRepository, HoldingsRepository holdingsRepository,
    TradeProperties tradeProperties, TradeLogger tradeLogger, MeterRegistry meterRegistry) {
    this.tradeRepository = tradeRepository;
    this.holdingsRepository = holdingsRepository;
    this.tradeProperties = tradeProperties;
    this.tradeLogger = tradeLogger;
    // 시작 때 0 으로 등록. 첫 증가 때 생긴 시계열은 increase 가 첫 값을 못 센다
    this.uncoveredSells = Counter.builder("trade.cancel.review").tag("reason", "uncovered_sell")
      .register(meterRegistry);
  }

  // 보유 행을 잠가 같은 종목 주문과 차례를 맞춘다. 보유보다 많은 매도는 보유를 비우고 이어 쌓는다
  public void rebuild(String userId, Long accountId, String symbol) {
    Optional<Holdings> existing =
      holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
    int pricePrecision = tradeProperties.getCalculation().getPricePrecision();

    Holdings rebuilt = null;
    for (Trade trade : tradeRepository.findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(
      userId, accountId, symbol, TradeStatus.COMPLETED)) {
      HoldingsCalculator.Applied applied = HoldingsCalculator.apply(rebuilt, trade, pricePrecision);
      if (applied.uncovered()) {
        uncoveredSells.increment();
        log.error("보유보다 많은 매도라 보유를 비우고 이어 쌓음: tradeId={}, userId={}, symbol={}",
          trade.getId(), userId, symbol);
      }
      rebuilt = applied.holdings();
    }

    BigDecimal oldQuantity = existing.map(Holdings::getTotalQuantity).orElse(BigDecimal.ZERO);
    BigDecimal oldAvgPrice = existing.map(Holdings::getAvgPurchasePrice).orElse(BigDecimal.ZERO);
    if (rebuilt == null) {
      existing.ifPresent(holdingsRepository::delete);
    } else if (existing.isPresent()) {
      existing.get().overwriteWith(rebuilt);
    } else {
      holdingsRepository.save(rebuilt);
    }

    tradeLogger.logHoldingsUpdate(userId, String.valueOf(accountId), symbol, oldQuantity,
      rebuilt == null ? BigDecimal.ZERO : rebuilt.getTotalQuantity(),
      oldAvgPrice, rebuilt == null ? BigDecimal.ZERO : rebuilt.getAvgPurchasePrice());
  }
}
