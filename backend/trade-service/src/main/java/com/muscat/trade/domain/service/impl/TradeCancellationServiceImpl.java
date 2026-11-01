package com.muscat.trade.domain.service.impl;

import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeCancelledEvent;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.HoldingsRebuilder;
import com.muscat.trade.domain.service.TradeCancellationService;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeCancellationServiceImpl implements TradeCancellationService {

  private final TradeRepository tradeRepository;
  private final HoldingsRebuilder holdingsRebuilder;
  private final ApplicationEventPublisher eventPublisher;

  // 체결 잠금으로 같은 반영 실패가 두 번 와도 한 번만 취소
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

    // 대기 매수는 보유에 들어간 적이 없어, 다시 쌓다 찾은 보유보다 많은 매도는 옛것이라 세지 않음
    boolean reviewUncovered = trade.getStatus() != TradeStatus.PENDING;
    trade.cancel(event.getReasonCode(), LocalDateTime.now());
    holdingsRebuilder.rebuild(trade.getUserId(), trade.getAccountId(), trade.getSymbol(), reviewUncovered);
    eventPublisher.publishEvent(new TradeCancelledEvent(trade.getUserId(), trade.getAccountId(), trade.getSymbol()));
    log.info("잔액 반영 실패로 체결 취소: tradeId={}, reason={}", trade.getId(), event.getReasonCode());
  }
}
