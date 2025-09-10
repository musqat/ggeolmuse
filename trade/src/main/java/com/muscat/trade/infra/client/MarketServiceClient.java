package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.StockPriceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@FeignClient(name = "market-service", url = "${market-service.url:http://localhost:8083}")
public interface MarketServiceClient {

  @GetMapping("/api/market/price/{symbol}")
  StockPriceDto getCurrentPrice(@PathVariable("symbol") String symbol);

  @GetMapping("/api/market/prices")
  Map<String, StockPriceDto> getCurrentPrices(@RequestParam("symbols") List<String> symbols);


  @GetMapping("/api/market/ohlc/{symbol}")
  StockPriceDto getOHLCPrice(@PathVariable("symbol") String symbol, @RequestParam("date") String date);
}