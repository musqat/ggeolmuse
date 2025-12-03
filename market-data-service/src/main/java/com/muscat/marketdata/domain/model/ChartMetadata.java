package com.muscat.marketdata.domain.model;

// Yahoo Finance 차트 메타데이터
public record ChartMetadata(
    String symbol,
    String currency
) {
}
