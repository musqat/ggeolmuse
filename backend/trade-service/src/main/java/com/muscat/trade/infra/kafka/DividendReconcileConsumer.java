package com.muscat.trade.infra.kafka;

import com.muscat.trade.domain.event.DividendReconcileRequest;
import com.muscat.trade.domain.service.DividendPayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 배당 몫 재계산 요청을 받아 그 계좌 · 종목을 다시 계산. 실패는 재시도 뒤 DLT
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendReconcileConsumer {

  private final DividendPayoutService dividendPayoutService;

  @KafkaListener(
    topics = "trading.dividend.reconcile",
    groupId = "${spring.application.name}-dividend-reconcile-consumer",
    containerFactory = "dividendReconcileKafkaListenerContainerFactory"
  )
  public void handle(@Payload DividendReconcileRequest request, Acknowledgment acknowledgment) {
    try {
      dividendPayoutService.reconcile(request.userId(), request.accountId(), request.symbol());
      acknowledgment.acknowledge();
    } catch (RuntimeException e) {
      log.error("배당 몫 재계산 실패: accountId={}, symbol={}, error={}",
        request.accountId(), request.symbol(), e.getMessage(), e);
      throw e;
    }
  }
}
