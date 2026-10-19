package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.exceptions.UserException;
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
public class TradeSettledEventProducer {

  private static final String TOPIC = "user.trade.settled";
  private static final long SEND_TIMEOUT_SECONDS = 5;

  private final KafkaTemplate<String, TradeSettledEvent> tradeSettledKafkaTemplate;

  // 정산됨 발행. 브로커 확인을 5초까지 기다리고, 받지 못하면 EVENT_PUBLISH_FAILED
  public void publish(TradeCompletedEvent trade) {
    TradeSettledEvent event = TradeSettledEvent.builder()
      .eventId(UUID.randomUUID().toString())
      .eventType("TRADE_SETTLED")
      .timestamp(LocalDateTime.now())
      .version("1.0")
      .source("user-service")
      .userId(trade.getUserId())
      .tradeId(trade.getTradeId())
      .accountId(trade.getAccountId())
      .originalEventId(trade.getEventId())
      .build();

    try {
      tradeSettledKafkaTemplate.send(TOPIC, trade.getUserId(), event)
        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      log.info("정산됨 발행: tradeId={}", trade.getTradeId());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UserException(UserResponse.EVENT_PUBLISH_FAILED, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new UserException(UserResponse.EVENT_PUBLISH_FAILED, e);
    }
  }
}
