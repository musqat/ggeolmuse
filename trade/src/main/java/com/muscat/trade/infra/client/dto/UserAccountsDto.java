package com.muscat.trade.infra.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UserAccountsDto {
  
  private List<AccountBalanceDto> accounts;
}