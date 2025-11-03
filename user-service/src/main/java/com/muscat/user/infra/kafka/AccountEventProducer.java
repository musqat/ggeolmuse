package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountCreatedEvent;
import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.user.domain.account.entity.Account;
import io.opentelemetry.api.trace.Span;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * 계좌 이벤트를 Kafka에 발행하는 Producer
 * 계좌 생성/삭제시 이벤트를 발행하여 알림, 분석, 감사 로그 등에 활용합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventProducer {

  private static final String ACCOUNT_CREATED_TOPIC = "user.account.created";
  private static final String ACCOUNT_DELETED_TOPIC = "user.account.deleted";

  private final KafkaTemplate<String, AccountCreatedEvent> accountCreatedKafkaTemplate;
  private final KafkaTemplate<String, AccountDeletedEvent> accountDeletedKafkaTemplate;

  /**
   * 계좌 생성 이벤트 발행
   *
   * @param account 생성된 계좌 정보
   */
  public void publishAccountCreated(Account account) {
    String eventId = UUID.randomUUID().toString();

    // OpenTelemetry trace ID 추출
    String traceId = null;
    try {
      traceId = Span.current().getSpanContext().getTraceId();
    } catch (Exception e) {
      log.debug("TraceID 추출 실패: {}", e.getMessage());
    }

    AccountCreatedEvent event = AccountCreatedEvent.builder()
      .eventId(eventId)
      .eventType("ACCOUNT_CREATED")
      .timestamp(LocalDateTime.now())
      .version("1.0")
      .traceId(traceId)
      .source("user-service")
      // Account 정보
      .userId(account.getUser().getId().toString())
      .accountId(account.getId())
      .accountNumber(account.getAccountNumber())
      .accountName(account.getAccountName())
      .initialKrwBalance(account.getBalanceKrw())
      .commissionRate(account.getCommissionRate())
      .build();

    log.info("계좌 생성 이벤트 발행 중: accountId={}, userId={}, accountNumber={}",
      account.getId(), account.getUser().getId(), account.getAccountNumber());

    // 비동기로 Kafka에 전송 (userId를 파티션 키로 사용)
    CompletableFuture<SendResult<String, AccountCreatedEvent>> future =
      accountCreatedKafkaTemplate.send(ACCOUNT_CREATED_TOPIC, account.getUser().getId().toString(),
        event);

    future.whenComplete((result, ex) -> {
      if (ex == null) {
        log.info(
          "계좌 생성 이벤트 발행 성공: topic={}, partition={}, offset={}, accountId={}",
          ACCOUNT_CREATED_TOPIC,
          result.getRecordMetadata().partition(),
          result.getRecordMetadata().offset(),
          account.getId());
      } else {
        log.error("계좌 생성 이벤트 발행 실패: accountId={}, error={}",
          account.getId(), ex.getMessage(), ex);
      }
    });
  }

  /**
   * 계좌 삭제 이벤트 발행
   *
   * @param account        삭제된 계좌 정보
   * @param deletionReason 삭제 사유 (선택 사항)
   */
  public void publishAccountDeleted(Account account, String deletionReason) {
    String eventId = UUID.randomUUID().toString();

    // OpenTelemetry trace ID 추출
    String traceId = null;
    try {
      traceId = Span.current().getSpanContext().getTraceId();
    } catch (Exception e) {
      log.debug("TraceID 추출 실패: {}", e.getMessage());
    }

    AccountDeletedEvent event = AccountDeletedEvent.builder()
      .eventId(eventId)
      .eventType("ACCOUNT_DELETED")
      .timestamp(LocalDateTime.now())
      .version("1.0")
      .traceId(traceId)
      .source("user-service")
      // Account 정보
      .userId(account.getUser().getId().toString())
      .accountId(account.getId())
      .accountNumber(account.getAccountNumber())
      .accountName(account.getAccountName())
      .finalKrwBalance(account.getBalanceKrw())
      .finalUsdBalance(account.getBalanceUsd())
      .deletionReason(deletionReason)
      .build();

    log.info(
      "계좌 삭제 이벤트 발행 중: accountId={}, userId={}, accountNumber={}, krwBalance={}, usdBalance={}",
      account.getId(), account.getUser().getId(), account.getAccountNumber(),
      account.getBalanceKrw(), account.getBalanceUsd());

    // 비동기로 Kafka에 전송 (userId를 파티션 키로 사용)
    CompletableFuture<SendResult<String, AccountDeletedEvent>> future =
      accountDeletedKafkaTemplate.send(ACCOUNT_DELETED_TOPIC, account.getUser().getId().toString(),
        event);

    future.whenComplete((result, ex) -> {
      if (ex == null) {
        log.info(
          "계좌 삭제 이벤트 발행 성공: topic={}, partition={}, offset={}, accountId={}",
          ACCOUNT_DELETED_TOPIC,
          result.getRecordMetadata().partition(),
          result.getRecordMetadata().offset(),
          account.getId());
      } else {
        log.error("계좌 삭제 이벤트 발행 실패: accountId={}, error={}",
          account.getId(), ex.getMessage(), ex);
      }
    });
  }
}
