package com.muscat.trade.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// user 계좌 장부의 환전 한 건(originalAmount 는 낸 통화, exchangedAmount 는 받은 통화, 환율은 1 USD 당 KRW)
public record ExchangeRecordDto(Long accountId, LocalDateTime exchangedAt, String fromCurrency, String toCurrency,
  BigDecimal originalAmount, BigDecimal exchangedAmount, BigDecimal exchangeRate) {
}
