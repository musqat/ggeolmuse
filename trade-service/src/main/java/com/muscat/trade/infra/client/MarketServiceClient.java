package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.trade.infra.client.dto.StockPriceDto;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "market-service", url = "http://market-data-service:8083")
public interface MarketServiceClient {

  @GetMapping("/api/market/price/{symbol}")
  StockPriceDto getCurrentPrice(@PathVariable("symbol") String symbol);

  @GetMapping("/api/market/prices")
  Map<String, StockPriceDto> getCurrentPrices(@RequestParam("symbols") List<String> symbols);


  @GetMapping("/api/market/ohlc/{symbol}")
  StockPriceDto getOHLCPrice(@PathVariable("symbol") String symbol,
      @RequestParam("date") String date);

  @GetMapping("/api/market/ohlc/{symbol}/with-dividends")
  List<StockPriceDto> getHistoricalPrices(@PathVariable("symbol") String symbol,
      @RequestParam("startDate") String startDate,
      @RequestParam("endDate") String endDate);

  @GetMapping("/api/internal/market/dividend/{symbol}")
  List<DividendDto> getDividends(@PathVariable("symbol") String symbol,
      @RequestParam(value = "startDate", required = false) String startDate,
      @RequestParam(value = "endDate", required = false) String endDate);
}