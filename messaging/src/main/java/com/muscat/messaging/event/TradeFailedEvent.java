package com.muscat.messaging.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 거래 실패 이벤트
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TradeFailedEvent extends BaseEvent {

    /**
     * 거래 ID
     */
    private Long tradeId;

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 계좌 ID
     */
    private Long accountId;

    /**
     * 종목 심볼 (예: AAPL, GOOGL)
     */
    private String symbol;

    /**
     * 거래 타입 (BUY 또는 SELL)
     */
    private String tradeType;

    /**
     * 주문 수량
     */
    private Integer quantity;

    /**
     * 주문 가격
     */
    private BigDecimal price;

    /**
     * 총 금액 (가격 * 수량)
     */
    private BigDecimal totalAmount;

    /**
     * 실패 원인 코드
     */
    private String failureCode;

    /**
     * 실패 원인 메시지
     */
    private String failureMessage;

    /**
     * 통화 (USD, KRW 등)
     */
    private String currency;

    /**
     * 가격 타입 (MARKET, LIMIT 등)
     */
    private String priceType;
}
