package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.UserDeletedEvent;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 사용자 삭제 이벤트를 받아 그 사용자의 거래 내역 · 보유 자산 · 배당 내역 · 수익 흐름 저장분을 지우는 컨슈머
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedEventConsumer {

  private final TradeRepository tradeRepository;
  private final HoldingsRepository holdingsRepository;
  private final DividendPayoutRepository dividendPayoutRepository;
  private final AccountProfitDailyRepository accountProfitDailyRepository;

  @KafkaListener(
    topics = "user.deleted",
    groupId = "${spring.application.name}-user-deleted-consumer",
    containerFactory = "userDeletedEventKafkaListenerContainerFactory"
  )
  @Transactional
  public void handleUserDeleted(@Payload UserDeletedEvent event, Acknowledgment acknowledgment) {
    String keycloakId = event.getKeycloakId();
    if (keycloakId == null || keycloakId.isBlank()) {
      log.warn("Keycloak ID 없는 사용자 삭제 이벤트 - 건너뜀: eventId={}", event.getEventId());
      acknowledgment.acknowledge();
      return;
    }
    tradeRepository.deleteByUserId(keycloakId);
    holdingsRepository.deleteByUserId(keycloakId);
    dividendPayoutRepository.deleteByUserId(keycloakId);
    accountProfitDailyRepository.deleteByUserId(keycloakId);
    log.info("사용자 삭제로 거래 데이터 지움: keycloakId={}", keycloakId);
    acknowledgment.acknowledge();
  }
}
