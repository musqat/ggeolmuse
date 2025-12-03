package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

// 거래 완료 이벤트 - user-service: 계좌 잔액 업데이트
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TradeCompletedEvent extends BaseEvent {

    // 거래를 실행한 사용자 ID
    private String userId;

    // 고유 거래 식별자
    private Long tradeId;

    // 종목 심볼 (예: "AAPL", "GOOGL")
    private String symbol;

    // 거래 타입: "BUY" 또는 "SELL"
    private String tradeType;

    // 거래된 주식 수량
    private BigDecimal quantity;

    // 주당 체결 가격
    private BigDecimal price;

    // 총 거래 금액 (수량 * 가격)
    private BigDecimal totalAmount;

    // 통화 코드 (예: "USD", "KRW")
    private String currency;

    // 거래 수수료
    private BigDecimal fee;

    // 사용된 가격 타입: "CURRENT_PRICE" 또는 "AVERAGE_PRICE"
    private String priceType;
}
