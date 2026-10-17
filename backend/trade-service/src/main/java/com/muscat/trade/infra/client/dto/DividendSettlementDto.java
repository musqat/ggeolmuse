package com.muscat.trade.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// user 가 계좌 · 종목 · 배당락일마다 지금까지 넣은 배당
public record DividendSettlementDto(Long accountId, String symbol, LocalDate exDate, BigDecimal amount) {
}
