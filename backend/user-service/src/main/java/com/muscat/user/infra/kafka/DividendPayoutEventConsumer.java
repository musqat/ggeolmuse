package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.user.domain.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 배당 몫 목표 금액을 받아 계좌에 차이만 넣거나 돌려받음
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPayoutEventConsumer {

  private final AccountService accountService;

  @KafkaListener(
    topics = "trading.dividend.payout",
    groupId = "${spring.application.name}-dividend-payout-consumer",
    containerFactory = "dividendPayoutEventKafkaListenerContainerFactory"
  )
  public void handleDividendPayout(
    @Payload DividendPayoutEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
  ) {
    log.info("배당 몫 이벤트 수신: accountId={}, symbol={}, exDate={}, netAmount={}, partition={}, offset={}",
      event.getAccountId(), event.getSymbol(), event.getExDate(), event.getNetAmount(), partition, offset);
    try {
      accountService.applyDividendPayout(event);
      acknowledgment.acknowledge();
    } catch (Exception ex) {
      log.error("배당 몫 반영 실패: accountId={}, symbol={}, exDate={}, error={}",
        event.getAccountId(), event.getSymbol(), event.getExDate(), ex.getMessage(), ex);
      throw ex;
    }
  }
}
