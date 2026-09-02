package com.muscat.trade.infra.kafka;

import com.muscat.commonlib.constants.CommonConstants;
import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.repository.HoldingsRepository;
import java.math.BigDecimal;
import java.util.List;
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

  private final HoldingsRepository holdingsRepository;
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
      // 1. 해당 종목을 보유한 모든 Holdings 조회
      List<Holdings> holdingsList = holdingsRepository.findBySymbol(event.getSymbol());

      if (holdingsList.isEmpty()) {
        log.info("배당금 지급 대상 없음: symbol={} (보유자 없음)", event.getSymbol());
        acknowledgment.acknowledge();
        return;
      }

      log.info("배당금 지급 시작: symbol={}, 보유자 수={}", event.getSymbol(), holdingsList.size());

      // 2. 각 Holdings에 대해 배당금 계산 및 지급
      int successCount = 0;
      int failCount = 0;

      for (Holdings holdings : holdingsList) {
        try {
          // 배당금 계산: 주당 배당금 × 보유 수량
          BigDecimal dividendAmount = event.getAmount()
            .multiply(holdings.getTotalQuantity())
            .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);

          log.info("배당금 지급 처리: userId={}, accountId={}, symbol={}, quantity={}, dividendAmount={}",
            holdings.getUserId(), holdings.getAccountId(),
            holdings.getSymbol(), holdings.getTotalQuantity(), dividendAmount);

          // 3. 배당금 수령 이벤트 발행 (비동기)
          // 기존 동기 REST API 호출을 Kafka 이벤트로 전환
          dividendEventProducer.publishDividendReceived(
            holdings.getUserId(),
            holdings.getAccountId(),
            holdings.getSymbol(),
            event.getExDate().toString(),
            event.getAmount(),
            holdings.getTotalQuantity(),
            dividendAmount
          );

          successCount++;
          log.info("배당금 수령 이벤트 발행: userId={}, accountId={}, amount={}",
            holdings.getUserId(), holdings.getAccountId(), dividendAmount);

        } catch (Exception e) {
          failCount++;
          log.error("배당금 지급 실패: userId={}, accountId={}, symbol={}, error={}",
            holdings.getUserId(), holdings.getAccountId(),
            holdings.getSymbol(), e.getMessage(), e);
          // 개별 지급 실패는 무시하고 계속 진행
        }
      }

      log.info("배당금 지급 완료: symbol={}, exDate={}, 성공={}, 실패={}",
        event.getSymbol(), event.getExDate(), successCount, failCount);

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
