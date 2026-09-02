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
 * 환율 업데이트 이벤트
 * market-data-service에서 환율 데이터가 업데이트될 때 발행
 * user-service가 이 이벤트를 소비하여 환율을 실시간 업데이트
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FxRateUpdatedEvent extends BaseEvent {

    // 환율 날짜
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    // 통화 쌍 (예: "USD/KRW")
    private String currencyPair;

    // 환율 (USD → KRW)
    private BigDecimal rate;

    // 이전 환율 (변경 추적용, nullable)
    private BigDecimal previousRate;
}
