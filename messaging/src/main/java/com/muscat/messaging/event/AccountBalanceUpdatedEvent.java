package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 계좌 잔액 변경 이벤트
 * user-service에서 계좌 잔액이 변경될 때 발행
 * 다른 서비스들이 실시간 자산 현황 모니터링, 이상 거래 탐지
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountBalanceUpdatedEvent extends BaseEvent {

    // 사용자 ID
    private String userId;

    // 계좌 ID
    private Long accountId;

    // 계좌 번호
    private String accountNumber;

    // 잔액 변경 타입
    private String updateType;

    // 변경 전 KRW 잔액
    private BigDecimal previousBalanceKrw;

    // 변경 후 KRW 잔액
    private BigDecimal currentBalanceKrw;

    // 변경 전 USD 잔액
    private BigDecimal previousBalanceUsd;

    // 변경 후 USD 잔액
    private BigDecimal currentBalanceUsd;

    // KRW 잔액 변화량 (양수: 증가, 음수: 감소)
    private BigDecimal krwChange;

    // USD 잔액 변화량 (양수: 증가, 음수: 감소)
    private BigDecimal usdChange;

    // 변경 사유 설명
    private String description;

    // 관련 거래 ID (거래로 인한 잔액 변경 시)
    private String relatedTradeId;

    // 적용된 환율 (환전 시)
    private BigDecimal exchangeRate;
}
