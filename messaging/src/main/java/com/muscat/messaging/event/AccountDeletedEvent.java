package com.muscat.messaging.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 계좌 삭제 이벤트
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AccountDeletedEvent extends BaseEvent {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 삭제된 계좌 ID
     */
    private Long accountId;

    /**
     * 삭제된 계좌 번호
     */
    private String accountNumber;

    /**
     * 삭제된 계좌 이름
     */
    private String accountName;

    /**
     * 삭제 시점 KRW 잔액
     */
    private BigDecimal finalKrwBalance;

    /**
     * 삭제 시점 USD 잔액
     */
    private BigDecimal finalUsdBalance;

    /**
     * 삭제 사유 (선택 사항)
     */
    private String deletionReason;
}
