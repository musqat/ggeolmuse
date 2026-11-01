package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.exception.UserException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSettledEventProducer {

  private static final String TOPIC = "user.trade.settled";
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

  private final KafkaTemplate<String, TradeSettledEvent> tradeSettledKafkaTemplate;

  // 정산됨 발행(브로커 확인 5초 대기, 못 받으면 EVENT_PUBLISH_FAILED)
  public void publish(TradeCompletedEvent trade) {
    TradeSettledEvent event = TradeSettledEvent.builder()
      .userId(trade.getUserId())
      .tradeId(trade.getTradeId())
      .accountId(trade.getAccountId())
      .originalEventId(trade.getEventId())
      .build();
    EventPublishing.stamp(event, "TRADE_SETTLED", "user-service");

    EventPublishing.sendAndWait(tradeSettledKafkaTemplate, TOPIC, trade.getUserId(), event, SEND_TIMEOUT,
      e -> new UserException(UserResponse.EVENT_PUBLISH_FAILED, e));
    log.info("정산됨 발행: tradeId={}", trade.getTradeId());
  }
}
