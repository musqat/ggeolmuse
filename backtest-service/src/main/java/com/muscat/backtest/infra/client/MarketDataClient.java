package com.muscat.backtest.infra.client;

import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "market-data-internal", url = "${app.market-data.internal.url:http://market-data-service:8083}")
public interface MarketDataClient {

  // 특정 날짜의 주식 OHLC 가격 조회 (내부 API)
  @GetMapping("/api/internal/market/ohlc/{symbol}")
  OHLCPriceDto getOHLCPrice(@PathVariable("symbol") String symbol,
    @RequestParam("date") String date);

  // 특정 기간의 주식 OHLC 가격 범위 조회 (내부 API)
  @GetMapping("/api/internal/market/ohlc/{symbol}/range")
  List<OHLCPriceDto> getOHLCPriceRange(@PathVariable("symbol") String symbol,
    @RequestParam("startDate") String startDate,
    @RequestParam("endDate") String endDate);

  // 현재 주식 가격 조회 (내부 API)
  @GetMapping("/api/internal/market/price/{symbol}")
  StockPriceDto getCurrentPrice(@PathVariable("symbol") String symbol);

  // 특정 날짜의 환율 조회 (내부 API)
  @GetMapping("/api/internal/market/fx/{date}")
  FxRateDto getFxRate(@PathVariable("date") String date);

  // 최신 환율 조회 (내부 API)
  @GetMapping("/api/internal/market/fx/latest")
  FxRateDto getLatestFxRate();

  // 특정 기간의 배당 이력 조회 (내부 API)
  @GetMapping("/api/internal/market/dividend/{symbol}")
  List<DividendDto> getDividendHistory(@PathVariable("symbol") String symbol,
    @RequestParam(value = "startDate", required = false) String startDate,
    @RequestParam(value = "endDate", required = false) String endDate);

  // Bulk 환율 조회 (내부 API) - 여러 날짜의 환율을 한 번에 조회
  @PostMapping("/api/internal/market/fx/bulk")
  java.util.Map<String, BigDecimal> getBulkFxRates(
    @RequestBody List<String> dates);
}
