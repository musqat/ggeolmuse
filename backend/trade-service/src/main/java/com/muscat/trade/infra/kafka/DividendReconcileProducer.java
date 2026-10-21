package com.muscat.trade.infra.kafka;

import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.event.DividendReconcileRequest;
import java.time.Duration;
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
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

  private final KafkaTemplate<String, DividendReconcileRequest> dividendReconcileKafkaTemplate;

  public void request(String userId, Long accountId, String symbol) {
    request(DividendReconcileRequest.of(userId, accountId, symbol));
  }

  public void request(DividendReconcileRequest request) {
    EventPublishing.sendAndWait(dividendReconcileKafkaTemplate, DIVIDEND_RECONCILE_TOPIC,
      String.valueOf(request.accountId()), request, SEND_TIMEOUT,
      e -> new TradeException(TradeResponse.EVENT_PUBLISH_FAILED, e));
    log.info("배당 몫 재계산 요청: accountId={}, symbol={}, resend={}",
      request.accountId(), request.symbol(), request.resend());
  }
}
