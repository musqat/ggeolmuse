package com.muscat.backtest.infra.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentBacktestResultDto {
    
    private String userId;
    private String backtestResult;
    private LocalDateTime calculatedAt;
}