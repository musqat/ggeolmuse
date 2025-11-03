package com.muscat.trade.infra.client.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceDto {

  private String accountId;
  private String accountName;
  private String accountNumber;
  
  private BigDecimal balanceUsd;
  private BigDecimal balanceKrw;
  
  private BigDecimal myAvgExchangeRate;
  private BigDecimal currentExchangeRate;
  
  private BigDecimal commissionRate;
  private BigDecimal slippageRate;
}