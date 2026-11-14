package com.muscat.marketdata.domain.dto;

/**
 * 이름 검색 결과 한 건(티커와 회사 이름)
 */
public record SymbolSearchDto(String symbol, String name) {}
