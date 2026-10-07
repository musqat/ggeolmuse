package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeRejectedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeRejectedEventProducer {

  private static final String TOPIC = "user.trade.rejected";
  private static final long SEND_TIMEOUT_SECONDS = 5;

  private final KafkaTemplate<String, TradeRejectedEvent> tradeRejectedKafkaTemplate;

  // 반영 실패 발행. 브로커 확인을 5초까지 기다리고, 받지 못하면 IllegalStateException
  public void publish(TradeCompletedEvent trade, String reasonCode, String reasonMessage) {
    TradeRejectedEvent event = TradeRejectedEvent.builder()
      .eventId(UUID.randomUUID().toString())
      .eventType("TRADE_REJECTED")
      .timestamp(LocalDateTime.now())
      .version("1.0")
      .source("user-service")
      .userId(trade.getUserId())
      .tradeId(trade.getTradeId())
      .accountId(trade.getAccountId())
      .reasonCode(reasonCode)
      .reasonMessage(reasonMessage)
      .originalEventId(trade.getEventId())
      .build();

    try {
      tradeRejectedKafkaTemplate.send(TOPIC, trade.getUserId(), event)
        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      log.info("반영 실패 발행: tradeId={}, reason={}", trade.getTradeId(), reasonCode);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("반영 실패 발행 중단: tradeId=" + trade.getTradeId(), e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("반영 실패 발행 실패: tradeId=" + trade.getTradeId(), e);
    }
  }
}
