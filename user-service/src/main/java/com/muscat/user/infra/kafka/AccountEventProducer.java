package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import com.muscat.user.domain.account.entity.Account;
import io.opentelemetry.api.trace.Span;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * 계좌 관련 이벤트를 Kafka에 발행하는 Producer
 * <p>
 * 계좌 잔액 변경 등의 중요한 계좌 이벤트를 발행하여 다른 서비스들이 실시간으로 계좌 상태 변경을 추적할 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventProducer {

  private static final String ACCOUNT_BALANCE_UPDATED_TOPIC = "user.account.balance.updated";

  private final KafkaTemplate<String, AccountBalanceUpdatedEvent> accountBalanceUpdatedKafkaTemplate;

  /**
   * 계좌 잔액 업데이트 이벤트 발행
   *
   * @param account            계좌 정보
   * @param updateType         업데이트 타입
   * @param previousBalanceKrw 변경 전 KRW 잔액
   * @param previousBalanceUsd 변경 전 USD 잔액
   * @param krwChange          KRW 변화량
   * @param usdChange          USD 변화량
   * @param description        변경 사유
   * @param relatedTradeId     관련 거래 ID
   * @param exchangeRate       환율 (환전 시)
   */
  public void publishAccountBalanceUpdated(Account account, String updateType,
    BigDecimal previousBalanceKrw, BigDecimal previousBalanceUsd,
    BigDecimal krwChange, BigDecimal usdChange,
    String description, String relatedTradeId,
    BigDecimal exchangeRate) {
    String eventId = UUID.randomUUID().toString();

    // OpenTelemetry trace ID 추출
    String traceId = null;
    try {
      traceId = Span.current().getSpanContext().getTraceId();
    } catch (Exception e) {
      log.debug("TraceID 추출 실패: {}", e.getMessage());
    }

    AccountBalanceUpdatedEvent event = AccountBalanceUpdatedEvent.builder()
      .eventId(eventId)
      .eventType("ACCOUNT_BALANCE_UPDATED")
      .timestamp(LocalDateTime.now())
      .version("1.0")
      .traceId(traceId)
      .source("user-service")
      // Account 정보
      .userId(String.valueOf(account.getUser().getId()))
      .accountId(account.getId())
      .accountNumber(account.getAccountNumber())
      .updateType(updateType)
      .previousBalanceKrw(previousBalanceKrw)
      .currentBalanceKrw(account.getBalanceKrw())
      .previousBalanceUsd(previousBalanceUsd)
      .currentBalanceUsd(account.getBalanceUsd())
      .krwChange(krwChange)
      .usdChange(usdChange)
      .description(description)
      .relatedTradeId(relatedTradeId)
      .exchangeRate(exchangeRate)
      .build();

    log.info("계좌 잔액 업데이트 이벤트 발행 중: accountId={}, updateType={}, KRW: {} -> {}, USD: {} -> {}",
      account.getId(), updateType, previousBalanceKrw, account.getBalanceKrw(),
      previousBalanceUsd, account.getBalanceUsd());

    // 비동기로 Kafka에 전송
    CompletableFuture<SendResult<String, AccountBalanceUpdatedEvent>> future =
      accountBalanceUpdatedKafkaTemplate.send(ACCOUNT_BALANCE_UPDATED_TOPIC,
        String.valueOf(account.getUser().getId()), event);

    future.whenComplete((result, ex) -> {
      if (ex == null) {
        log.info(
          "계좌 잔액 업데이트 이벤트 발행 성공: topic={}, partition={}, offset={}, accountId={}, updateType={}",
          ACCOUNT_BALANCE_UPDATED_TOPIC,
          result.getRecordMetadata().partition(),
          result.getRecordMetadata().offset(),
          account.getId(), updateType);
      } else {
        log.error("계좌 잔액 업데이트 이벤트 발행 실패: accountId={}, updateType={}, error={}",
          account.getId(), updateType, ex.getMessage(), ex);
      }
    });
  }
}
