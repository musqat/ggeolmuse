package com.muscat.backtest.common.util;

import com.muscat.backtest.infra.client.dto.FxRateDto;
import java.math.BigDecimal;
import java.time.LocalDate;

// 환율 데이터가 없을 때 쓰는 기본 환율 단일 소스(1300)
public final class FxFallback {

  /** 환율 미존재 시 기본값 (원/USD) */
  public static final BigDecimal DEFAULT_RATE = new BigDecimal("1300");

  private FxFallback() {}

  /** 지정 날짜의 기본환율 FxRateDto */
  public static FxRateDto defaultFxRate(LocalDate date) {
    return new FxRateDto(date, DEFAULT_RATE);
  }
}
