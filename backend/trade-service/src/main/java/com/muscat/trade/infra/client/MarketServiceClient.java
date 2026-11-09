package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "market-service", url = "http://market-data-service:8083")
public interface MarketServiceClient {

  @GetMapping("/api/internal/market/prices")
  Map<String, StockPriceDto> getCurrentPrices(@RequestParam("symbols") List<String> symbols);

  @GetMapping("/api/internal/market/ohlc/{symbol}")
  StockPriceDto getOHLCPrice(@PathVariable("symbol") String symbol,
      @RequestParam("date") String date);

  @GetMapping("/api/internal/market/dividend/{symbol}")
  List<DividendDto> getDividends(@PathVariable("symbol") String symbol,
      @RequestParam(value = "startDate", required = false) String startDate,
      @RequestParam(value = "endDate", required = false) String endDate);

  @GetMapping("/api/internal/market/ohlc/{symbol}/range")
  List<OHLCPriceDto> getOHLCPriceRange(@PathVariable("symbol") String symbol,
      @RequestParam("startDate") String startDate,
      @RequestParam("endDate") String endDate);

  // 날짜마다 그 날짜 이하 가장 늦은 환율(날짜 문자열 → USD/KRW)
  @PostMapping("/api/internal/market/fx/bulk")
  Map<String, BigDecimal> getBulkFxRates(@RequestBody List<String> dates);
}
