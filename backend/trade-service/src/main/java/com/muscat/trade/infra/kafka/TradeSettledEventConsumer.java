package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.trade.domain.service.TradeConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// user.trade.settled 의 대기 매수 완료. 실패하면 예외를 던져 재시도 → DLT
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSettledEventConsumer {

  private final TradeConfirmationService tradeConfirmationService;

  @KafkaListener(
    topics = "user.trade.settled",
    groupId = "${spring.application.name}-trade-settled-consumer",
    containerFactory = "tradeSettledEventKafkaListenerContainerFactory"
  )
  public void handleTradeSettled(
    @Payload TradeSettledEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
  ) {
    log.info("정산됨 이벤트 수신: tradeId={}, partition={}, offset={}", event.getTradeId(), partition, offset);
    tradeConfirmationService.confirm(event);
    acknowledgment.acknowledge();
  }
}
