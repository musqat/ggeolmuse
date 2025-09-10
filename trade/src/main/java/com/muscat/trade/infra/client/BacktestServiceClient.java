package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.InvestmentBacktestResultDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "backtest-service", url = "${app.backtest-service.url:http://localhost:8082}")
public interface BacktestServiceClient {

    // 사용자의 투자 백테스트 캐시 결과 조회
    @GetMapping("/api/backtest/investment-result/{userId}")
    InvestmentBacktestResultDto getCachedInvestmentBacktestResult(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("userId") String userId
    );
}