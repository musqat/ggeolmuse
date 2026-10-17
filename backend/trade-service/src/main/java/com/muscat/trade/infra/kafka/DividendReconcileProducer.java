package com.muscat.trade.infra.kafka;

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.event.DividendReconcileRequest;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 배당 몫 재계산 요청 발행. 계좌 ID 를 키로 보내 같은 계좌의 재계산 순서를 지킴
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendReconcileProducer {

  private static final String DIVIDEND_RECONCILE_TOPIC = "trading.dividend.reconcile";
  private static final long SEND_TIMEOUT_SECONDS = 5;

  private final KafkaTemplate<String, DividendReconcileRequest> dividendReconcileKafkaTemplate;

  public void request(String userId, Long accountId, String symbol) {
    try {
      dividendReconcileKafkaTemplate.send(DIVIDEND_RECONCILE_TOPIC, String.valueOf(accountId),
          new DividendReconcileRequest(userId, accountId, symbol))
        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      log.info("배당 몫 재계산 요청: accountId={}, symbol={}", accountId, symbol);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e);
    }
  }
}
