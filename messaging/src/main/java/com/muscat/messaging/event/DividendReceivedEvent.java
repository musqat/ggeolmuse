package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 배당금 수령 이벤트
 * trade-service에서 발행하며, user-service가 소비하여 사용자 계좌에 배당금을 입금
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DividendReceivedEvent extends BaseEvent {

    // 배당금을 받을 사용자 ID
    private String userId;

    // 배당금을 입금할 계좌 ID
    private Long accountId;

    // 배당을 지급한 종목 심볼
    private String symbol;

    // 배당 기준일 (ex-dividend date)
    private String exDate;

    // 주당 배당금
    private BigDecimal dividendPerShare;

    // 보유 수량
    private BigDecimal quantity;

    // 총 배당금 액수 (주당 배당금 × 보유 수량)
    private BigDecimal totalAmount;

    // 통화 코드 (예: "USD")
    private String currency;
}
