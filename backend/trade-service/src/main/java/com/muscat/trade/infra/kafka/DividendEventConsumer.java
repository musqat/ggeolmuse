package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 배당 공시를 받아 배당락일 전에 그 종목을 거래한 계좌마다 배당 몫 재계산을 요청
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendEventConsumer {

  private final TradeRepository tradeRepository;
  private final DividendReconcileProducer dividendReconcileProducer;

  @KafkaListener(
    topics = "market.dividend.updated",
    groupId = "${spring.application.name}-dividend-consumer",
    containerFactory = "dividendEventKafkaListenerContainerFactory"
  )
  public void handleDividendUpdated(
    @Payload DividendUpdatedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
  ) {
    log.info("배당 공시 수신: symbol={}, exDate={}, amount={}, partition={}, offset={}",
      event.getSymbol(), event.getExDate(), event.getAmount(), partition, offset);

    List<Trade> trades = tradeRepository.findBySymbolAndStatusAndTradeDateBeforeOrderByTradeDateAsc(
      event.getSymbol(), TradeStatus.COMPLETED, event.getExDate());
    Map<Long, String> userByAccount = trades.stream()
      .collect(Collectors.toMap(Trade::getAccountId, Trade::getUserId, (a, b) -> a, LinkedHashMap::new));

    int failCount = 0;
    for (Map.Entry<Long, String> account : userByAccount.entrySet()) {
      try {
        dividendReconcileProducer.request(account.getValue(), account.getKey(), event.getSymbol());
      } catch (RuntimeException e) {
        failCount++;
        log.error("배당 몫 재계산 요청 실패: accountId={}, symbol={}, error={}",
          account.getKey(), event.getSymbol(), e.getMessage(), e);
      }
    }
    log.info("배당 공시 처리: symbol={}, exDate={}, 계좌={}, 실패={}",
      event.getSymbol(), event.getExDate(), userByAccount.size(), failCount);

    // 실패가 있으면 커밋 안 함. Kafka 가 다시 주고(재시도 뒤 DLT), 같은 요청은 다시 계산해도 결과가 같음
    if (failCount > 0) {
      throw new TradeException(TradeResponse.DIVIDEND_PAYMENT_FAILED);
    }
    acknowledgment.acknowledge();
  }
}
