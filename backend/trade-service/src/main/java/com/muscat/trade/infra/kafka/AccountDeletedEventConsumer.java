package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.LegacyDividendRepository;
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

// 계좌 삭제 이벤트를 받아 그 계좌의 거래 내역 · 보유 자산 · 배당 내역을 지움
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletedEventConsumer {

  private final TradeRepository tradeRepository;
  private final HoldingsRepository holdingsRepository;
  private final DividendPayoutRepository dividendPayoutRepository;
  private final LegacyDividendRepository legacyDividendRepository;

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

      // 3. 해당 계좌의 배당 내역 삭제(회원 탈퇴 뒤 남지 않게)
      dividendPayoutRepository.deleteByAccountId(event.getAccountId());
      legacyDividendRepository.deleteByAccountId(event.getAccountId());
      log.info("배당 내역 삭제 완료: accountId={}", event.getAccountId());

      log.info("계좌 삭제 이벤트 처리 완료: userId={}, accountId={}, accountNumber={}",
        event.getUserId(), event.getAccountId(), event.getAccountNumber());

      // 4. 수동 커밋 (처리 성공시에만)
      acknowledgment.acknowledge();

    } catch (Exception ex) {
      log.error("계좌 삭제 이벤트 처리 실패: userId={}, accountId={}, error={}",
        event.getUserId(), event.getAccountId(), ex.getMessage(), ex);

      // Kafka가 재시도
      throw ex;
    }
  }
}
