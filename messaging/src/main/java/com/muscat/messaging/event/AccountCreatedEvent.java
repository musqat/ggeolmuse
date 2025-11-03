package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 계좌 생성 이벤트
 * user-service에서 계좌 생성시 발행되는 이벤트
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountCreatedEvent extends BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 계좌 이름
     */
    private String accountName;

    /**
     * 초기 KRW 잔액
     */
    private BigDecimal initialKrwBalance;

    /**
     * 수수료율
     */
    private BigDecimal commissionRate;
}
