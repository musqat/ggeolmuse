package com.muscat.backtest.infra.client.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FxRateDto {
    private LocalDate date;
    private BigDecimal rate;  // USD/KRW 환율
    private String source;
}