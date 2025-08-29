package com.muscat.trade.infra.client;

import com.muscat.trade.common.responses.ApiResponse;
import com.muscat.trade.infra.client.dto.DividendInfoDto;
import com.muscat.trade.infra.client.dto.OHLCPriceDto;
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
  ApiResponse<StockPriceDto> getCurrentPrice(@PathVariable("symbol") String symbol);

  @GetMapping("/api/market/prices")
  ApiResponse<Map<String, StockPriceDto>> getCurrentPrices(@RequestParam("symbols") List<String> symbols);

  @GetMapping("/api/market/dividend/{symbol}")
  ApiResponse<DividendInfoDto> getDividendInfo(@PathVariable("symbol") String symbol);

  @GetMapping("/api/market/dividends")
  ApiResponse<List<DividendInfoDto>> getDividendsForDate(@RequestParam("date") LocalDate date);

  @GetMapping("/api/market/ohlc/{symbol}")
  ApiResponse<OHLCPriceDto> getOHLCPrice(@PathVariable("symbol") String symbol, @RequestParam("date") LocalDate date);
}