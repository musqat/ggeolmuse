package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.DividendPayout;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
  private static final long SEND_TIMEOUT_SECONDS = 5;

  private final KafkaTemplate<String, DividendPayoutEvent> dividendPayoutKafkaTemplate;

  public void publish(DividendPayout payout) {
    DividendPayoutEvent event = DividendPayoutEvent.builder()
      .eventId(UUID.randomUUID().toString())
      .eventType("DIVIDEND_PAYOUT")
      .timestamp(LocalDateTime.now())
      .version("1.0")
      .source("trade-service")
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

    try {
      dividendPayoutKafkaTemplate.send(DIVIDEND_PAYOUT_TOPIC, payout.getUserId(), event)
        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      log.info("배당 몫 이벤트 발행: accountId={}, symbol={}, exDate={}, netAmount={}",
        payout.getAccountId(), payout.getSymbol(), payout.getExDate(), payout.getNetAmount());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e);
    }
  }
}
