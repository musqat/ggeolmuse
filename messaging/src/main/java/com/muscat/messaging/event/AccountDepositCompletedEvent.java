package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 계좌 입금 완료 이벤트
 *
 * 사용자가 계좌에 입금을 완료했을 때 발행됩니다.
 * 자금 흐름 추적, 입금 통계 등에 활용할 수 있습니다.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountDepositCompletedEvent extends BaseEvent {

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
     * 입금 화폐 종류
     * "KRW" - 한국 원화
     * "USD" - 미국 달러 (현재 KRW만 지원)
     */
    private String currency;

    /**
     * 입금 금액
     */
    private BigDecimal depositAmount;

    /**
     * 입금 전 잔액
     */
    private BigDecimal balanceBeforeDeposit;

    /**
     * 입금 후 잔액
     */
    private BigDecimal balanceAfterDeposit;

    /**
     * 입금 방법
     * "MANUAL" - 수동 입금 (관리자 또는 사용자 요청)
     */
    private String depositMethod;

    /**
     * 거래 참조 ID
     */
    private String referenceId;

    /**
     * 입금 설명
     */
    private String description;
}
