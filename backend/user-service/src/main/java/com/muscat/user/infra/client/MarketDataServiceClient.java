package com.muscat.user.infra.client;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.dto.FxRateRangeDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "market-data", url = "${app.market-data.url:http://market-data-service:8083}")
public interface MarketDataServiceClient {

  @GetMapping("/api/internal/market/fx/latest")
  FxRateDto getLatestFxRate();

  @GetMapping("/api/internal/market/fx/range")
  FxRateRangeDto getFxRateRange();
}
