package com.muscat.backtest.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// 환율 정보 DTO
public record FxRateDto(
    LocalDate date,
    BigDecimal rate,
    // 환율이 없어 기본 환율로 채운 값
    boolean defaulted
) {

  public FxRateDto(LocalDate date, BigDecimal rate) {
    this(date, rate, false);
  }
}
