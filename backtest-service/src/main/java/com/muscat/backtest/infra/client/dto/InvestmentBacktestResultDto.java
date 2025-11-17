package com.muscat.backtest.infra.client.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentBacktestResultDto {

  private String userId;
  private String backtestResult;
  private LocalDateTime calculatedAt;
}
