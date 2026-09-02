package com.muscat.trade.infra.client.dto;

import java.math.BigDecimal;

public record DividendDto(
    String exDate,
    BigDecimal amount
) {
}
