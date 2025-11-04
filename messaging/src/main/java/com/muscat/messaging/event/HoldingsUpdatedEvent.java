package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 포트폴리오 보유 현황 변경 이벤트
 *
 * trade-service에서 거래 체결 후 Holdings가 업데이트될 때 발행합니다.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HoldingsUpdatedEvent extends BaseEvent {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 계좌 ID
     */
    private Long accountId;

    /**
     * 종목 심볼
     */
    private String symbol;

    /**
     * 보유 현황 변경 타입: "CREATED", "UPDATED", "DELETED"
     */
    private String updateType;

    /**
     * 변경 전 보유 수량 (신규 생성 시 0)
     */
    private BigDecimal previousQuantity;

    /**
     * 변경 후 보유 수량 (전량 매도 시 0)
     */
    private BigDecimal currentQuantity;

    /**
     * 변경 전 평균 매수 단가
     */
    private BigDecimal previousAvgPrice;

    /**
     * 변경 후 평균 매수 단가
     */
    private BigDecimal currentAvgPrice;

    /**
     * 현재 총 투자 금액
     */
    private BigDecimal totalInvestedAmount;

    /**
     * 이 변경을 일으킨 거래 ID
     */
    private String tradeId;

    /**
     * 거래 타입: "BUY" 또는 "SELL"
     */
    private String tradeType;

    /**
     * 거래 수량
     */
    private BigDecimal tradeQuantity;

    /**
     * 거래 단가
     */
    private BigDecimal tradePrice;
}
