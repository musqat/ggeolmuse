package com.muscat.trade.domain.dto;

// 거래나 배당 몫이 있는 사용자 · 계좌 · 종목 한 자리
public record TradePosition(String userId, Long accountId, String symbol) {
}
