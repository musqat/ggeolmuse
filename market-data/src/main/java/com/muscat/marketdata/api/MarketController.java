package com.muscat.marketdata.api;

import com.muscat.marketdata.common.response.ApiResponse;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.service.MarketService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

  private final MarketService marketService;

  @GetMapping("/ohlc/{symbol}")
  @PostMapping("/ohlc/{symbol}")
  public ResponseEntity<ApiResponse<OHLCPriceDto>> getOHLCPrice(
      @PathVariable String symbol,
      @RequestParam LocalDate date) {

    try {
      log.debug("OHLC 조회 요청: symbol={}, date={}", symbol, date);

      OHLCPriceDto result = marketService.getOHLCPrice(symbol, date);
      
      if (!result.isAvailable()) {
        return ResponseEntity.ok(ApiResponse.success("해당 날짜의 시장 데이터가 없습니다", result));
      }

      return ResponseEntity.ok(ApiResponse.success(result));

    } catch (Exception e) {
      log.error("OHLC 조회 중 오류: symbol={}, date={}, error={}", symbol, date, e.getMessage(), e);
      throw e;
    }
  }

  @GetMapping("/price/{symbol}")
  @PostMapping("/price/{symbol}")
  public ResponseEntity<ApiResponse<StockPriceDto>> getCurrentPrice(@PathVariable String symbol) {
    try {
      log.debug("현재가 조회 요청: symbol={}", symbol);

      StockPriceDto result = marketService.getCurrentPrice(symbol);
      
      if (!result.isAvailable()) {
        return ResponseEntity.ok(ApiResponse.success("시장 데이터가 없습니다", result));
      }

      return ResponseEntity.ok(ApiResponse.success(result));

    } catch (Exception e) {
      log.error("현재가 조회 중 오류: symbol={}, error={}", symbol, e.getMessage(), e);
      throw e;
    }
  }

}