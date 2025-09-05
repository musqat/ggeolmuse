package com.muscat.backtest.infra.client;

import com.muscat.backtest.common.response.ApiResponse;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.OHLCPriceDto;
import com.muscat.backtest.infra.client.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// 마켓 데이터 서비스 클라이언트 - 주가, 환율, 배당 정보 조회
@FeignClient(name = "market-data", url = "${app.market-data.url:http://localhost:8083}")
public interface MarketDataClient {

  // 특정 날짜의 주식 OHLC 가격 조회
  @GetMapping("/api/market/ohlc/{symbol}")
  ApiResponse<OHLCPriceDto> getOHLCPrice(@PathVariable String symbol,
      @RequestParam("date") String date);

  // 현재 주식 가격 조회
  @GetMapping("/api/market/price/{symbol}")
  ApiResponse<StockPriceDto> getCurrentPrice(@PathVariable String symbol);

  // 특정 날짜의 환율 조회
  @GetMapping("/api/market/fx/{date}")
  ApiResponse<FxRate> getFxRate(@PathVariable("date") String date);

  // 최신 환율 조회
  @GetMapping("/api/market/fx/latest")
  ApiResponse<FxRate> getLatestFxRate();

  // 특정 기간의 배당 이력 조회
  @GetMapping("/api/market/dividends/{symbol}")
  ApiResponse<DividendHistoryDto> getDividendHistory(@PathVariable String symbol,
      @RequestParam("startDate") String startDate,
      @RequestParam("endDate") String endDate);

  // 환율 정보 레코드
  record FxRate(LocalDate date, BigDecimal rate) {

  }
}