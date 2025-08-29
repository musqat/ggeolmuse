package com.muscat.trade.common.enums.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PriceType {
    OPEN("시가", "OPEN"),
    CLOSE("종가", "CLOSE"),
    HIGH("고가", "HIGH"),
    LOW("저가", "LOW"),
    MANUAL("직접입력", "MANUAL");

    private final String description;
    private final String code;
}