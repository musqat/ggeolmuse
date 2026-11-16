package com.muscat.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

// 주식 분할 이벤트(종목 · 분할일이 멱등 키)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StockSplitEvent extends BaseEvent {

    private String symbol;

    // 분할이 반영된 첫 거래일
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate splitDate;

    // 분할 전 1주가 분할 뒤 몇 주인지(4:1 분할은 4, 1:10 병합은 0.1)
    private BigDecimal ratio;
}
