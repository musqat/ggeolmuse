package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountDepositCompletedEvent;
import com.muscat.messaging.event.AccountWithdrawalCompletedEvent;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
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
 * 입출금 완료 이벤트를 Kafka에서 소비하는 Consumer
 *
 * 입금/출금 완료 이벤트를 소비하여 AccountHistory에 이력을 저장합니다.
 * 향후 "입출금 내역 조회" API에서 사용할 데이터를 수집합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositWithdrawalEventConsumer {

    private final AccountHistoryRepository accountHistoryRepository;
    private final AccountRepository accountRepository;

    /**
     * 입금 완료 이벤트 처리
     *
     * @param event          입금 완료 이벤트
     * @param partition      Kafka 파티션 번호
     * @param offset         메시지 오프셋
     * @param acknowledgment 수동 커밋용 객체
     */
    @KafkaListener(
            topics = "user.account.deposit.completed",
            groupId = "${spring.application.name}-deposit-consumer",
            containerFactory = "depositCompletedEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleDepositCompleted(
            @Payload AccountDepositCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("입금 완료 이벤트 수신: accountId={}, currency={}, amount={}, partition={}, offset={}",
                event.getAccountId(), event.getCurrency(), event.getDepositAmount(), partition, offset);

        try {
            // 계좌 조회
            Account account = accountRepository.findById(event.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + event.getAccountId()));

            // AccountHistory 저장
            AccountHistory history = AccountHistory.builder()
                    .account(account)
                    .transactionType(TransactionType.DEPOSIT)
                    .amount(event.getDepositAmount())
                    .currency(event.getCurrency())
                    .balanceAfter(event.getBalanceAfterDeposit())
                    .description(event.getDescription())
                    .referenceId(event.getReferenceId())
                    .build();

            accountHistoryRepository.save(history);

            // 수동 커밋 (처리 성공시에만)
            acknowledgment.acknowledge();

            log.info("입금 이력 저장 완료: accountId={}, historyId={}, amount={}",
                    event.getAccountId(), history.getId(), event.getDepositAmount());

        } catch (Exception ex) {
            log.error("입금 이력 저장 실패: accountId={}, amount={}, error={}",
                    event.getAccountId(), event.getDepositAmount(), ex.getMessage(), ex);

            // NOTE: 수동 커밋하지 않음 -> Kafka가 재시도
            // 재시도 실패시 DLQ로 이동
            throw new RuntimeException("Deposit history save failed", ex);
        }
    }

    /**
     * 출금 완료 이벤트 처리
     *
     * @param event          출금 완료 이벤트
     * @param partition      Kafka 파티션 번호
     * @param offset         메시지 오프셋
     * @param acknowledgment 수동 커밋용 객체
     */
    @KafkaListener(
            topics = "user.account.withdrawal.completed",
            groupId = "${spring.application.name}-withdrawal-consumer",
            containerFactory = "withdrawalCompletedEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleWithdrawalCompleted(
            @Payload AccountWithdrawalCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("출금 완료 이벤트 수신: accountId={}, currency={}, amount={}, partition={}, offset={}",
                event.getAccountId(), event.getCurrency(), event.getWithdrawalAmount(), partition, offset);

        try {
            // 계좌 조회
            Account account = accountRepository.findById(event.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + event.getAccountId()));

            // AccountHistory 저장
            // NOTE: 출금은 EXCHANGE 타입 사용 (환전 출금 포함)
            AccountHistory history = AccountHistory.builder()
                    .account(account)
                    .transactionType(TransactionType.EXCHANGE)
                    .amount(event.getWithdrawalAmount().negate()) // 출금은 음수로 저장
                    .currency(event.getCurrency())
                    .balanceAfter(event.getBalanceAfterWithdrawal())
                    .description(event.getDescription())
                    .referenceId(event.getReferenceId())
                    .build();

            accountHistoryRepository.save(history);

            // 수동 커밋 (처리 성공시에만)
            acknowledgment.acknowledge();

            log.info("출금 이력 저장 완료: accountId={}, historyId={}, amount={}",
                    event.getAccountId(), history.getId(), event.getWithdrawalAmount());

        } catch (Exception ex) {
            log.error("출금 이력 저장 실패: accountId={}, amount={}, error={}",
                    event.getAccountId(), event.getWithdrawalAmount(), ex.getMessage(), ex);

            // NOTE: 수동 커밋하지 않음 -> Kafka가 재시도
            // 재시도 실패시 DLQ로 이동
            throw new RuntimeException("Withdrawal history save failed", ex);
        }
    }
}
