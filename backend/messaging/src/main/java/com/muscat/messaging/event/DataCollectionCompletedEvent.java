package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDate;

// 데이터 수집 완료/실패 이벤트 - AssetCreatedEvent에 대한 응답으로 발행
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DataCollectionCompletedEvent extends BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // 수집 정보
    private String symbol;
    private LocalDate fromDate;
    private LocalDate toDate;
    private boolean successful;
    private Integer candleCount;
    private Integer dividendCount;
    private String errorMessage;
    private Long executionTimeMs;
}
