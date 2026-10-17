package com.muscat.trade.infra.kafka;

import com.muscat.commonlib.constants.CommonConstants;
import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.DividendEntitlement;
import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 배당금 업데이트 이벤트를 Kafka에서 소비하는 Consumer market-data-service에서 발행한 DividendUpdatedEvent를 소비하여 해당 종목을
 * 보유한 사용자에게 자동으로 배당금을 지급합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendEventConsumer {

  private final TradeRepository tradeRepository;
  private final DividendEventProducer dividendEventProducer;

  /**
   * 배당금 업데이트 이벤트 처리
   *
   * @param event          배당금 업데이트 이벤트
   * @param partition      Kafka 파티션 번호
   * @param offset         메시지 오프셋
   * @param acknowledgment 수동 커밋용 객체
   */
  @KafkaListener(
    topics = "market.dividend.updated",
    groupId = "${spring.application.name}-dividend-consumer",
    containerFactory = "dividendEventKafkaListenerContainerFactory"
  )
  @Transactional
  public void handleDividendUpdated(
    @Payload DividendUpdatedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    @Header(KafkaHeaders.OFFSET) long offset,
    Acknowledgment acknowledgment
  ) {
    log.info("배당금 업데이트 이벤트 수신: symbol={}, exDate={}, amount={}, partition={}, offset={}",
      event.getSymbol(), event.getExDate(), event.getAmount(), partition, offset);

    try {
      // 1. 배당락일 전에 이 종목을 거래한 계좌. 지금 보유가 아니라 배당락일 전날 보유가 기준이다
      List<Trade> trades = tradeRepository.findBySymbolAndStatusAndTradeDateBeforeOrderByTradeDateAsc(
        event.getSymbol(), TradeStatus.COMPLETED, event.getExDate());
      Map<Long, List<Trade>> tradesByAccount = trades.stream()
        .collect(Collectors.groupingBy(Trade::getAccountId, LinkedHashMap::new, Collectors.toList()));

      if (tradesByAccount.isEmpty()) {
        log.info("배당금 지급 대상 없음: symbol={}, exDate={} (배당락일 전 거래 없음)", event.getSymbol(), event.getExDate());
        acknowledgment.acknowledge();
        return;
      }

      log.info("배당금 지급 시작: symbol={}, 대상 계좌 수={}", event.getSymbol(), tradesByAccount.size());

      // 2. 계좌마다 배당락일 전날 보유 수량으로 세후 배당금을 계산해 지급
      int successCount = 0;
      int failCount = 0;

      for (Map.Entry<Long, List<Trade>> entry : tradesByAccount.entrySet()) {
        Long accountId = entry.getKey();
        String userId = entry.getValue().getFirst().getUserId();
        try {
          BigDecimal shares = DividendEntitlement.sharesHeldBefore(entry.getValue(), event.getExDate());
          if (shares.signum() <= 0) {
            continue;
          }

          BigDecimal grossAmount = event.getAmount().multiply(shares)
            .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
          BigDecimal taxAmount = grossAmount.multiply(TradeConstants.DIVIDEND_TAX_RATE)
            .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
          BigDecimal netAmount = grossAmount.subtract(taxAmount);

          log.info("배당금 지급 처리: userId={}, accountId={}, symbol={}, quantity={}, 세전={}, 세후={}",
            userId, accountId, event.getSymbol(), shares, grossAmount, netAmount);

          dividendEventProducer.publishDividendReceived(
            userId, accountId, event.getSymbol(), event.getExDate().toString(),
            event.getAmount(), shares, netAmount);
          successCount++;

        } catch (Exception e) {
          failCount++;
          log.error("배당금 지급 실패: userId={}, accountId={}, symbol={}, error={}",
            userId, accountId, event.getSymbol(), e.getMessage(), e);
          // 나머지 계좌는 계속 지급하고, 끝에서 다시 받도록 예외를 올린다
        }
      }

      log.info("배당금 지급 완료: symbol={}, exDate={}, 성공={}, 실패={}",
        event.getSymbol(), event.getExDate(), successCount, failCount);

      // 실패가 있으면 커밋하지 않는다. Kafka 가 다시 주고(재시도 뒤 DLT), 이미 지급한 계좌는 user-service 가 같은 배당으로 걸러낸다
      if (failCount > 0) {
        throw new TradeException(TradeResponse.DIVIDEND_PAYMENT_FAILED);
      }

      // 4. 수동 커밋 (처리 성공시에만)
      acknowledgment.acknowledge();

    } catch (Exception ex) {
      log.error("배당금 이벤트 처리 실패: symbol={}, exDate={}, error={}",
        event.getSymbol(), event.getExDate(), ex.getMessage(), ex);

      // Kafka가 재시도
      throw ex;
    }
  }
}
