package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 계좌 출금 완료 이벤트
 *
 * 사용자가 계좌에서 출금을 완료했을 때 발행됩니다.
 * 자금 흐름 추적, 출금 통계, 대량 출금 모니터링 등에 활용할 수 있습니다.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountWithdrawalCompletedEvent extends BaseEvent {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 계좌 ID
     */
    private Long accountId;

    /**
     * 계좌 번호
     */
    private String accountNumber;

    /**
     * 출금 화폐 종류
     * "KRW" - 한국 원화
     * "USD" - 미국 달러
     */
    private String currency;

    /**
     * 출금 금액
     */
    private BigDecimal withdrawalAmount;

    /**
     * 출금 전 잔액
     */
    private BigDecimal balanceBeforeWithdrawal;

    /**
     * 출금 후 잔액
     */
    private BigDecimal balanceAfterWithdrawal;

    /**
     * 출금 방법
     * "MANUAL" - 수동 출금 (관리자 또는 사용자 요청)
     * "BANK_TRANSFER" - 계좌 이체 (향후 확장)
     * "EXCHANGE" - 환전으로 인한 출금
     */
    private String withdrawalMethod;

    /**
     * 거래 참조 ID
     */
    private String referenceId;

    /**
     * 출금 설명
     */
    private String description;

    /**
     * 출금 승인 여부 (향후 확장: 고액 출금 승인 프로세스)
     */
    private Boolean approved;
}
