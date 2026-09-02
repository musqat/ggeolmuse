package com.muscat.backtest.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// 환율 정보 DTO
public record FxRateDto(
    LocalDate date,
    BigDecimal rate
) {
}
