package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import com.muscat.messaging.event.AccountDeletedEvent;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 계좌 잔액 변경 · 삭제 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventProducer {

  private static final String ACCOUNT_BALANCE_UPDATED_TOPIC = "user.account.balance.updated";
  private static final String ACCOUNT_DELETED_TOPIC = "user.account.deleted";

  private final KafkaTemplate<String, AccountBalanceUpdatedEvent> accountBalanceUpdatedKafkaTemplate;
  private final KafkaTemplate<String, AccountDeletedEvent> accountDeletedKafkaTemplate;

  // exchangeRate 는 환전일 때만
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
      .userId(String.valueOf(account.getUser().getId()))
      .accountId(account.getId())
      .accountNumber(account.getAccountNumber())
      .accountName(account.getAccountName())
      .deletionReason(deletionReason)
      .build();

    log.info("계좌 삭제 이벤트 발행 중: accountId={}, accountNumber={}, userId={}",
      account.getId(), account.getAccountNumber(), account.getUser().getId());

    // 삭제가 롤백되면 trade 가 거래 · 보유를 지우면 안 되므로 커밋 뒤에 보낸다.
    // 이벤트는 엔티티가 살아 있는 지금 만들어 두고, 보낼 때는 엔티티를 읽지 않는다
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          sendAccountDeleted(event);
        }
      });
    } else {
      sendAccountDeleted(event);
    }
  }

  // 커밋이 끝난 뒤라 실패를 호출한 쪽으로 올리지 않는다
  private void sendAccountDeleted(AccountDeletedEvent event) {
    try {
      CompletableFuture<SendResult<String, AccountDeletedEvent>> future =
        accountDeletedKafkaTemplate.send(ACCOUNT_DELETED_TOPIC, event.getUserId(), event);

      future.whenComplete((result, ex) -> {
        if (ex == null) {
          log.info(
            "계좌 삭제 이벤트 발행 성공: topic={}, partition={}, offset={}, accountId={}, accountNumber={}",
            ACCOUNT_DELETED_TOPIC,
            result.getRecordMetadata().partition(),
            result.getRecordMetadata().offset(),
            event.getAccountId(), event.getAccountNumber());
        } else {
          log.error("계좌 삭제 이벤트 발행 실패: accountId={}, accountNumber={}, error={}",
            event.getAccountId(), event.getAccountNumber(), ex.getMessage(), ex);
        }
      });
    } catch (Exception e) {
      log.error("계좌 삭제 이벤트 발행 실패: accountId={}, accountNumber={}, error={}",
        event.getAccountId(), event.getAccountNumber(), e.getMessage(), e);
    }
  }
}
