package com.muscat.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

// 배당금 업데이트 이벤트
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DividendUpdatedEvent extends BaseEvent {

    // 종목 심볼
    private String symbol;

    // 배당락일 (Ex-Dividend Date)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate exDate;

    // 주당 배당금액
    private BigDecimal amount;

    // 배당 통화 (기본: USD)
    private String currency;
}
