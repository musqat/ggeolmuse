package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 거래 취소 이벤트 - 거래 실패 또는 보상 트랜잭션으로 거래를 취소할 때 발행
 * user-service가 이 이벤트를 소비하여 잔액을 원복
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TradeCancelledEvent extends BaseEvent {

    // 거래를 실행한 사용자 ID
    private String userId;

    // 고유 거래 식별자
    private Long tradeId;

    // 계좌 ID
    private Long accountId;

    // 종목 심볼 (예: "AAPL", "GOOGL")
    private String symbol;

    // 거래 타입: "BUY" 또는 "SELL"
    private String tradeType;

    // 거래된 주식 수량
    private BigDecimal quantity;

    // 주당 가격
    private BigDecimal price;

    // 총 거래 금액 (수량 * 가격)
    private BigDecimal totalAmount;

    // 통화 코드 (예: "USD", "KRW")
    private String currency;

    // 거래 수수료
    private BigDecimal fee;

    // 취소 사유 코드
    private String cancellationReason;

    // 취소 사유 상세 메시지
    private String cancellationMessage;

    // 원본 TradeCompletedEvent의 eventId (추적용)
    private String originalEventId;
}
