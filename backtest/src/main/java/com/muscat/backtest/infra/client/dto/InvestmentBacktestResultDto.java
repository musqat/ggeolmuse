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
    private String backtestResult; // JSON 형태의 백테스트 결과
    private LocalDateTime calculatedAt;
    private LocalDateTime nextScheduledAt;
    private Long executionTimeMs;
    private String status; // COMPLETED, FAILED 등
}