package com.muscat.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 배당금 업데이트 이벤트
 *
 * market-data-service에서 배당금 데이터가 저장될 때 발행
 * trade-service가 이 이벤트를 소비하여 포트폴리오에 배당금을 자동 반영
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DividendUpdatedEvent extends BaseEvent {

    /**
     * 종목 심볼
     */
    private String symbol;

    /**
     * 배당락일 (Ex-Dividend Date)
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate exDate;

    /**
     * 주당 배당금액
     */
    private BigDecimal amount;

    /**
     * 배당 통화 (기본: USD)
     */
    private String currency;
}
