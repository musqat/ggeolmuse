package com.muscat.backtest.infra.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividendDto(
  String symbol,              // 티커 예: AAPL
  LocalDate exDate,           // 권리락일 (가장 중요)
  LocalDate paymentDate,      // 지급일 (없을 수 있음)
  LocalDate recordDate,       // 기준일 (없을 수 있음)
  BigDecimal amount,          // 주당 배당금
  String currency,            // 예: USD
  String source               // 데이터 출처 태그(예: "AlphaVantage")
) {

}
