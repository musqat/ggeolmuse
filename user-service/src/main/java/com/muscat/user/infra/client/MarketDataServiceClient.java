package com.muscat.user.infra.client;

import com.muscat.user.infra.client.dto.FxRateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "market-data", url = "${app.market-data.url:http://market-data-service:8083}")
public interface MarketDataServiceClient {

  @GetMapping("/api/internal/market/fx/{date}")
  FxRateDto getFxRate(@PathVariable("date") String date);

  @GetMapping("/api/internal/market/fx/latest")
  FxRateDto getLatestFxRate();
}