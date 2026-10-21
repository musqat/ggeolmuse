package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.DividendPayout;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 배당 몫 목표 금액 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPayoutProducer {

  private static final String DIVIDEND_PAYOUT_TOPIC = "trading.dividend.payout";
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

  private final KafkaTemplate<String, DividendPayoutEvent> dividendPayoutKafkaTemplate;

  public void publish(DividendPayout payout) {
    DividendPayoutEvent event = DividendPayoutEvent.builder()
      .userId(payout.getUserId())
      .accountId(payout.getAccountId())
      .symbol(payout.getSymbol())
      .exDate(payout.getExDate().toString())
      .dividendPerShare(payout.getDividendPerShare())
      .quantity(payout.getShares())
      .grossAmount(payout.getGrossAmount())
      .taxAmount(payout.getTaxAmount())
      .netAmount(payout.getNetAmount())
      .currency("USD")
      .build();
    EventPublishing.stamp(event, "DIVIDEND_PAYOUT", "trade-service");

    EventPublishing.sendAndWait(dividendPayoutKafkaTemplate, DIVIDEND_PAYOUT_TOPIC, payout.getUserId(), event,
      SEND_TIMEOUT, e -> new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e));
    log.info("배당 몫 이벤트 발행: accountId={}, symbol={}, exDate={}, netAmount={}",
      payout.getAccountId(), payout.getSymbol(), payout.getExDate(), payout.getNetAmount());
  }
}
