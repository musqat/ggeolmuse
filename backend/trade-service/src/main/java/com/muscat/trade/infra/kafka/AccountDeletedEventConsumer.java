package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계좌 삭제 이벤트를 소비하는 Consumer
 * user-service에서 발행한 AccountDeletedEvent를 소비하여
 * 해당 계좌의 모든 거래 내역 및 보유 자산을 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletedEventConsumer {

  private final TradeRepository tradeRepository;
  private final HoldingsRepository holdingsRepository;

  /**
   * 계좌 삭제 이벤트 처리
   */
  @KafkaListener(
    topics = "user.account.deleted",
    groupId = "${spring.application.name}-account-deleted-consumer",
    containerFactory = "accountDeletedEventKafkaListenerContainerFactory"
  )
  @Transactional
  public void handleAccountDeleted(
    @Payload AccountDeletedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
  ) {
    log.info("계좌 삭제 이벤트 수신: userId={}, accountId={}, accountNumber={}, partition={}, offset={}",
      event.getUserId(), event.getAccountId(), event.getAccountNumber(), partition, offset);

    try {
      // 1. 해당 계좌의 모든 거래 내역 삭제
      log.debug("거래 내역 삭제 시작: accountId={}", event.getAccountId());
      tradeRepository.deleteByAccountId(event.getAccountId());
      log.info("거래 내역 삭제 완료: accountId={}", event.getAccountId());

      // 2. 해당 계좌의 모든 보유 자산 삭제
      log.debug("보유 자산 삭제 시작: accountId={}", event.getAccountId());
      holdingsRepository.deleteByAccountId(event.getAccountId());
      log.info("보유 자산 삭제 완료: accountId={}", event.getAccountId());

      log.info("계좌 삭제 이벤트 처리 완료: userId={}, accountId={}, accountNumber={}",
        event.getUserId(), event.getAccountId(), event.getAccountNumber());

      // 3. 수동 커밋 (처리 성공시에만)
      acknowledgment.acknowledge();

    } catch (Exception ex) {
      log.error("계좌 삭제 이벤트 처리 실패: userId={}, accountId={}, error={}",
        event.getUserId(), event.getAccountId(), ex.getMessage(), ex);

      // Kafka가 재시도
      throw ex;
    }
  }
}
