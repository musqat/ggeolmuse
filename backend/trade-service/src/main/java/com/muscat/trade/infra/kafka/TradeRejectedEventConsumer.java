package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.trade.domain.service.TradeCancellationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// user.trade.rejected 의 체결을 취소한다. 실패하면 예외를 던져 재시도 → DLT
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeRejectedEventConsumer {

  private final TradeCancellationService tradeCancellationService;

  @KafkaListener(
    topics = "user.trade.rejected",
    groupId = "${spring.application.name}-trade-rejected-consumer",
    containerFactory = "tradeRejectedEventKafkaListenerContainerFactory"
  )
  public void handleTradeRejected(
    @Payload TradeRejectedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
  ) {
    log.info("반영 실패 이벤트 수신: tradeId={}, reason={}, partition={}, offset={}",
      event.getTradeId(), event.getReasonCode(), partition, offset);
    tradeCancellationService.cancel(event);
    acknowledgment.acknowledge();
  }
}
