package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

// 거래 실패 이벤트
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TradeFailedEvent extends BaseEvent {

    // 거래를 시도한 사용자 ID
    private String userId;

    // 계좌 ID
    private Long accountId;

    // 종목 심볼 (예: "AAPL", "GOOGL")
    private String symbol;

    // 거래 타입: "BUY" 또는 "SELL"
    private String tradeType;

    // 주문 수량
    private Integer quantity;

    // 주문 가격
    private BigDecimal price;

    // 총 주문 금액
    private BigDecimal totalAmount;

    // 통화 코드
    private String currency;

    // 실패 사유 코드
    private String failureCode;

    // 실패 사유 상세 메시지
    private String failureMessage;
}
