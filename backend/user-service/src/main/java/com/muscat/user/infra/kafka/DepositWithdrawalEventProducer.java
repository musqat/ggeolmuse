package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountDepositCompletedEvent;
import com.muscat.messaging.event.AccountWithdrawalCompletedEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.user.domain.account.entity.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// 입금 · 출금 완료 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositWithdrawalEventProducer {

    private static final String DEPOSIT_COMPLETED_TOPIC = "user.account.deposit.completed";
    private static final String WITHDRAWAL_COMPLETED_TOPIC = "user.account.withdrawal.completed";

    private final KafkaTemplate<String, AccountDepositCompletedEvent> depositCompletedKafkaTemplate;
    private final KafkaTemplate<String, AccountWithdrawalCompletedEvent> withdrawalCompletedKafkaTemplate;

    public void publishDepositCompleted(Account account, String currency, BigDecimal depositAmount,
                                         BigDecimal balanceBefore, String depositMethod,
                                         String referenceId, String description) {
        // 입금 후 잔액 계산
        BigDecimal balanceAfter = "KRW".equals(currency) ?
            account.getBalanceKrw() : account.getBalanceUsd();

        AccountDepositCompletedEvent event = AccountDepositCompletedEvent.builder()
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
        EventPublishing.stamp(event, "ACCOUNT_DEPOSIT_COMPLETED", "user-service");

        log.info("입금 완료 이벤트 발행 중: accountId={}, currency={}, amount={}, method={}",
                account.getId(), currency, depositAmount, depositMethod);

        EventPublishing.send(depositCompletedKafkaTemplate, DEPOSIT_COMPLETED_TOPIC, event.getUserId(), event);
    }

    public void publishWithdrawalCompleted(Account account, String currency, BigDecimal withdrawalAmount,
                                            BigDecimal balanceBefore, String withdrawalMethod,
                                            String referenceId, String description, Boolean approved) {
        // 출금 후 잔액 계산
        BigDecimal balanceAfter = "KRW".equals(currency) ?
            account.getBalanceKrw() : account.getBalanceUsd();

        AccountWithdrawalCompletedEvent event = AccountWithdrawalCompletedEvent.builder()
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
        EventPublishing.stamp(event, "ACCOUNT_WITHDRAWAL_COMPLETED", "user-service");

        log.info("출금 완료 이벤트 발행 중: accountId={}, currency={}, amount={}, method={}",
                account.getId(), currency, withdrawalAmount, withdrawalMethod);

        EventPublishing.send(withdrawalCompletedKafkaTemplate, WITHDRAWAL_COMPLETED_TOPIC, event.getUserId(), event);
    }
}
