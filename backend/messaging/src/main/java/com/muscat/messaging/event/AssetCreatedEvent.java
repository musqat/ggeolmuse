package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDate;

// 심볼(Asset) 생성 이벤트(collectData 가 true 면 받는 쪽이 데이터 수집)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssetCreatedEvent extends BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // Asset 정보
    private String symbol;
    private String name;
    private String country;
    private String currency;
    private String assetType;
    private Long marketCap;

    // 데이터 수집 옵션
    private boolean collectData;
    private LocalDate fromDate;
    private LocalDate toDate;
    private boolean includeDividends;
}
