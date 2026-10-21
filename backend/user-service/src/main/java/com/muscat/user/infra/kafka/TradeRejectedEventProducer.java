package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.exceptions.UserException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeRejectedEventProducer {

  private static final String TOPIC = "user.trade.rejected";
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

  private final KafkaTemplate<String, TradeRejectedEvent> tradeRejectedKafkaTemplate;

  // 반영 실패 발행. 브로커 확인을 5초까지 기다리고, 받지 못하면 EVENT_PUBLISH_FAILED
  public void publish(TradeCompletedEvent trade, String reasonCode, String reasonMessage) {
    TradeRejectedEvent event = TradeRejectedEvent.builder()
      .userId(trade.getUserId())
      .tradeId(trade.getTradeId())
      .accountId(trade.getAccountId())
      .reasonCode(reasonCode)
      .reasonMessage(reasonMessage)
      .originalEventId(trade.getEventId())
      .build();
    EventPublishing.stamp(event, "TRADE_REJECTED", "user-service");

    EventPublishing.sendAndWait(tradeRejectedKafkaTemplate, TOPIC, trade.getUserId(), event, SEND_TIMEOUT,
      e -> new UserException(UserResponse.EVENT_PUBLISH_FAILED, e));
    log.info("반영 실패 발행: tradeId={}, reason={}", trade.getTradeId(), reasonCode);
  }
}
