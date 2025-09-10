package com.muscat.user.infra.client;

import com.muscat.user.infra.client.dto.FxRateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;

@FeignClient(name = "market-data", url = "${app.market-data.url:http://localhost:8082}")
public interface MarketDataServiceClient {
    
    @GetMapping("/api/market/fx/{date}")
    FxRateDto getFxRate(@PathVariable("date") String date);
    
    @GetMapping("/api/market/fx/latest")
    FxRateDto getLatestFxRate();
}