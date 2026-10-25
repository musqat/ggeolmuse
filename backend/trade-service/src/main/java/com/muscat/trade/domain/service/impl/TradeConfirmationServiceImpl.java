package com.muscat.trade.domain.service.impl;

import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.metrics.TradeMetrics;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeConfirmedEvent;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.HoldingsRebuilder;
import com.muscat.trade.domain.service.TradeConfirmationService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeConfirmationServiceImpl implements TradeConfirmationService {

  private final TradeRepository tradeRepository;
  private final HoldingsRebuilder holdingsRebuilder;
  private final ApplicationEventPublisher eventPublisher;
  private final TradeMetrics tradeMetrics;

  // 체결을 잠가 같은 정산됨이 두 번 와도 한 번만 완료
  @Override
  @Transactional
  public void confirm(TradeSettledEvent event) {
    Optional<Trade> found = tradeRepository.findByIdForUpdate(event.getTradeId());
    if (found.isEmpty()) {
      log.warn("완료할 체결 없음. 계좌 삭제로 지워졌을 수 있음: tradeId={}", event.getTradeId());
      return;
    }
    Trade trade = found.get();
    if (trade.getStatus() != TradeStatus.PENDING) {
      log.info("대기 체결이 아니라 넘어감: tradeId={}, status={}", trade.getId(), trade.getStatus());
      return;
    }
    if (!trade.getUserId().equals(event.getUserId())) {
      log.error("정산됨 이벤트와 체결의 사용자가 다름: tradeId={}, eventUserId={}",
        trade.getId(), event.getUserId());
      return;
    }

    trade.complete();
    holdingsRebuilder.rebuild(trade.getUserId(), trade.getAccountId(), trade.getSymbol(), false);
    eventPublisher.publishEvent(new TradeConfirmedEvent(trade.getUserId(), trade.getAccountId(), trade.getSymbol()));
    tradeMetrics.recordCompleted(trade);
    log.info("정산된 매수 완료: tradeId={}", trade.getId());
  }
}
