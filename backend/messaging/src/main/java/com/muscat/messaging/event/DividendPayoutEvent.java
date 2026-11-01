package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

// 배당 몫 목표 금액 이벤트(차액이 아니라 그 배당락일 몫 전체)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DividendPayoutEvent extends BaseEvent {

    private String userId;

    private Long accountId;

    private String symbol;

    // yyyy-MM-dd
    private String exDate;

    private BigDecimal dividendPerShare;

    // 배당락일 전날 보유 수량
    private BigDecimal quantity;

    private BigDecimal grossAmount;

    private BigDecimal taxAmount;

    // 이 배당락일의 세후 몫(받으면 안 되게 되면 0)
    private BigDecimal netAmount;

    private String currency;
}
