package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountDepositCompletedEvent;
import com.muscat.messaging.event.AccountWithdrawalCompletedEvent;
import com.muscat.user.domain.account.entity.Account;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 입금/출금 관련 이벤트를 Kafka에 발행하는 Producer
 *
 * 계좌 입금/출금 완료 이벤트를 발행하여
 * 자금 흐름 추적, 통계 분석, 이상 거래 탐지 등에 활용할 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositWithdrawalEventProducer {

    private static final String DEPOSIT_COMPLETED_TOPIC = "user.account.deposit.completed";
    private static final String WITHDRAWAL_COMPLETED_TOPIC = "user.account.withdrawal.completed";

    private final KafkaTemplate<String, AccountDepositCompletedEvent> depositCompletedKafkaTemplate;
    private final KafkaTemplate<String, AccountWithdrawalCompletedEvent> withdrawalCompletedKafkaTemplate;

    /**
     * 입금 완료 이벤트 발행
     *
     * @param account 계좌 정보
     * @param currency 입금 화폐 종류
     * @param depositAmount 입금 금액
     * @param balanceBefore 입금 전 잔액
     * @param depositMethod 입금 방법
     * @param referenceId 거래 참조 ID
     * @param description 입금 설명
     */
    public void publishDepositCompleted(Account account, String currency, BigDecimal depositAmount,
                                         BigDecimal balanceBefore, String depositMethod,
                                         String referenceId, String description) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        // 입금 후 잔액 계산
        BigDecimal balanceAfter = "KRW".equals(currency) ?
            account.getBalanceKrw() : account.getBalanceUsd();

        AccountDepositCompletedEvent event = AccountDepositCompletedEvent.builder()
                .eventId(eventId)
                .eventType("ACCOUNT_DEPOSIT_COMPLETED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("user-service")
                // Deposit 정보
                .userId(String.valueOf(account.getUser().getId()))
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .currency(currency)
                .depositAmount(depositAmount)
                .balanceBeforeDeposit(balanceBefore)
                .balanceAfterDeposit(balanceAfter)
                .depositMethod(depositMethod)
                .referenceId(referenceId)
                .description(description)
                .build();

        log.info("입금 완료 이벤트 발행 중: accountId={}, currency={}, amount={}, method={}",
                account.getId(), currency, depositAmount, depositMethod);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, AccountDepositCompletedEvent>> future =
                depositCompletedKafkaTemplate.send(DEPOSIT_COMPLETED_TOPIC,
                        String.valueOf(account.getUser().getId()), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("입금 완료 이벤트 발행 성공: topic={}, partition={}, offset={}, accountId={}, amount={}",
                        DEPOSIT_COMPLETED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        account.getId(), depositAmount);
            } else {
                log.error("입금 완료 이벤트 발행 실패: accountId={}, amount={}, error={}",
                        account.getId(), depositAmount, ex.getMessage(), ex);
            }
        });
    }

    /**
     * 출금 완료 이벤트 발행
     *
     * @param account 계좌 정보
     * @param currency 출금 화폐 종류
     * @param withdrawalAmount 출금 금액
     * @param balanceBefore 출금 전 잔액
     * @param withdrawalMethod 출금 방법
     * @param referenceId 거래 참조 ID
     * @param description 출금 설명
     * @param approved 승인 여부
     */
    public void publishWithdrawalCompleted(Account account, String currency, BigDecimal withdrawalAmount,
                                            BigDecimal balanceBefore, String withdrawalMethod,
                                            String referenceId, String description, Boolean approved) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        // 출금 후 잔액 계산
        BigDecimal balanceAfter = "KRW".equals(currency) ?
            account.getBalanceKrw() : account.getBalanceUsd();

        AccountWithdrawalCompletedEvent event = AccountWithdrawalCompletedEvent.builder()
                .eventId(eventId)
                .eventType("ACCOUNT_WITHDRAWAL_COMPLETED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("user-service")
                // Withdrawal 정보
                .userId(String.valueOf(account.getUser().getId()))
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .currency(currency)
                .withdrawalAmount(withdrawalAmount)
                .balanceBeforeWithdrawal(balanceBefore)
                .balanceAfterWithdrawal(balanceAfter)
                .withdrawalMethod(withdrawalMethod)
                .referenceId(referenceId)
                .description(description)
                .approved(approved)
                .build();

        log.info("출금 완료 이벤트 발행 중: accountId={}, currency={}, amount={}, method={}",
                account.getId(), currency, withdrawalAmount, withdrawalMethod);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, AccountWithdrawalCompletedEvent>> future =
                withdrawalCompletedKafkaTemplate.send(WITHDRAWAL_COMPLETED_TOPIC,
                        String.valueOf(account.getUser().getId()), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("출금 완료 이벤트 발행 성공: topic={}, partition={}, offset={}, accountId={}, amount={}",
                        WITHDRAWAL_COMPLETED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        account.getId(), withdrawalAmount);
            } else {
                log.error("출금 완료 이벤트 발행 실패: accountId={}, amount={}, error={}",
                        account.getId(), withdrawalAmount, ex.getMessage(), ex);
            }
        });
    }
}
