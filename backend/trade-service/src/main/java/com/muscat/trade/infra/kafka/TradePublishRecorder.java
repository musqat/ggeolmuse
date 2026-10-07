package com.muscat.trade.infra.kafka;

import com.muscat.trade.domain.repository.TradeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 체결 이벤트 발행 시각 저장. AFTER_COMMIT 에서는 원래 트랜잭션이 끝나 있어 새 트랜잭션을 연다
@Component
@RequiredArgsConstructor
public class TradePublishRecorder {

  private final TradeRepository tradeRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordPublished(Long tradeId) {
    tradeRepository.markEventPublished(tradeId, LocalDateTime.now());
  }
}
