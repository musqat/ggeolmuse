package com.muscat.trade.domain.service.impl;

import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.HoldingsRebuilder;
import com.muscat.trade.domain.service.TradeCancellationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class TradeCancellationServiceImpl implements TradeCancellationService {

  private final TradeRepository tradeRepository;
  private final DividendRepository dividendRepository;
  private final HoldingsRebuilder holdingsRebuilder;
  private final Counter dividendRecorded;

  public TradeCancellationServiceImpl(TradeRepository tradeRepository,
    DividendRepository dividendRepository, HoldingsRebuilder holdingsRebuilder,
    MeterRegistry meterRegistry) {
    this.tradeRepository = tradeRepository;
    this.dividendRepository = dividendRepository;
    this.holdingsRebuilder = holdingsRebuilder;
    // 시작 때 0 으로 등록. 첫 증가 때 생긴 시계열은 increase 가 첫 값을 못 센다
    this.dividendRecorded = Counter.builder("trade.cancel.review").tag("reason", "dividend_recorded")
      .register(meterRegistry);
  }

  // 체결을 잠가서 같은 반영 실패가 두 번 와도 한 번만 취소한다
  @Override
  @Transactional
  public void cancel(TradeRejectedEvent event) {
    Optional<Trade> found = tradeRepository.findByIdForUpdate(event.getTradeId());
    if (found.isEmpty()) {
      log.warn("취소할 체결 없음. 계좌 삭제로 지워졌을 수 있음: tradeId={}", event.getTradeId());
      return;
    }
    Trade trade = found.get();
    if (trade.getStatus() == TradeStatus.CANCELLED) {
      log.info("이미 취소한 체결: tradeId={}", trade.getId());
      return;
    }
    if (!trade.getUserId().equals(event.getUserId())) {
      log.error("반영 실패 이벤트와 체결의 사용자가 다름: tradeId={}, eventUserId={}",
        trade.getId(), event.getUserId());
      return;
    }

    trade.cancel(event.getReasonCode(), LocalDateTime.now());
    holdingsRebuilder.rebuild(trade.getUserId(), trade.getAccountId(), trade.getSymbol());
    if (dividendRepository.existsByTradeId(trade.getId())) {
      dividendRecorded.increment();
      log.warn("취소한 체결에 배당 기록이 있음. 잔액 확인 필요: tradeId={}", trade.getId());
    }
    log.info("잔액 반영 실패로 체결 취소: tradeId={}, reason={}", trade.getId(), event.getReasonCode());
  }
}
