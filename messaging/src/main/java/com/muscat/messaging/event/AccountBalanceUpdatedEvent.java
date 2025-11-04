package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 계좌 잔액 변경 이벤트
 *
 * user-service에서 계좌 잔액이 변경될 때 발행합니다.
 * 다른 서비스들이 이 이벤트를 소비하여 실시간 자산 현황을 모니터링하고,
 * 이상 거래를 탐지하거나, 사용자에게 알림을 보낼 수 있습니다.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountBalanceUpdatedEvent extends BaseEvent {

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
     * 잔액 변경 타입
     * "DEPOSIT_KRW", "EXCHANGE_TO_USD", "EXCHANGE_TO_KRW",
     * "TRADE_COMPLETED", "TRADE_CANCELLED", "DIVIDEND_RECEIVED"
     */
    private String updateType;

    /**
     * 변경 전 KRW 잔액
     */
    private BigDecimal previousBalanceKrw;

    /**
     * 변경 후 KRW 잔액
     */
    private BigDecimal currentBalanceKrw;

    /**
     * 변경 전 USD 잔액
     */
    private BigDecimal previousBalanceUsd;

    /**
     * 변경 후 USD 잔액
     */
    private BigDecimal currentBalanceUsd;

    /**
     * KRW 잔액 변화량 (양수: 증가, 음수: 감소)
     */
    private BigDecimal krwChange;

    /**
     * USD 잔액 변화량 (양수: 증가, 음수: 감소)
     */
    private BigDecimal usdChange;

    /**
     * 변경 사유 설명
     */
    private String description;

    /**
     * 관련 거래 ID (거래로 인한 잔액 변경 시)
     */
    private String relatedTradeId;

    /**
     * 적용된 환율 (환전 시)
     */
    private BigDecimal exchangeRate;
}
