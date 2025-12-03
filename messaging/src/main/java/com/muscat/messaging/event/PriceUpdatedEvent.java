package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 주가 업데이트 이벤트
 * market-data-service에서 주가 데이터 업데이트시 발행
 * trade-service, backtest-service 등에서 구독하여 실시간 주가 정보 반영
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PriceUpdatedEvent extends BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // 주식 심볼 (예: AAPL, MSFT)
    private String symbol;

    // 주가 데이터 날짜
    private LocalDate date;

    // 통화 (USD, KRW 등)
    private String currency;

    // 시가 (Open Price)
    private BigDecimal open;

    // 고가 (High Price)
    private BigDecimal high;

    // 저가 (Low Price)
    private BigDecimal low;

    // 종가 (Close Price)
    private BigDecimal close;

    // 조정 종가 (Adjusted Close) - 배당, 주식 분할을 반영한 실제 거래 가격
    private BigDecimal adjustedClose;

    // 거래량 (Volume)
    private Long volume;

    // 배당금 (Dividend Amount)
    private BigDecimal dividendAmount;

    // 주식 분할 계수 (Split Coefficient)
    private BigDecimal splitCoefficient;
}
