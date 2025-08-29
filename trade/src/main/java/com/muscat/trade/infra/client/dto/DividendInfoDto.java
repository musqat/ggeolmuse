package com.muscat.trade.infra.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class DividendInfoDto {
  
  private String symbol;
  private LocalDate exDividendDate;
  private LocalDate paymentDate;
  private BigDecimal dividendAmount;
  private String frequency;
  private BigDecimal annualYield;
}