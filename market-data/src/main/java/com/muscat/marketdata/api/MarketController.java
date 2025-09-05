package com.muscat.marketdata.api;

import com.muscat.marketdata.common.response.ApiResponse;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.service.MarketService;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.feed.service.FxRateService;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
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
  private final FxRateService fxRateService;

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

  @GetMapping("/fx/{date}")
  public ResponseEntity<ApiResponse<FxRate>> getFxRate(@PathVariable LocalDate date) {
    try {
      log.debug("환율 조회 요청: date={}", date);

      FxRate fxRate = fxRateService.findByDate(date);
      
      if (fxRate == null) {
        return ResponseEntity.ok(ApiResponse.success("해당 날짜의 환율 데이터가 없습니다", null));
      }

      return ResponseEntity.ok(ApiResponse.success(fxRate));

    } catch (Exception e) {
      log.error("환율 조회 중 오류: date={}, error={}", date, e.getMessage(), e);
      throw e;
    }
  }

  @GetMapping("/fx/latest")
  public ResponseEntity<ApiResponse<FxRate>> getLatestFxRate() {
    try {
      log.debug("최신 환율 조회 요청");

      var fxRate = fxRateService.getLatestRate();
      
      if (fxRate.isEmpty()) {
        return ResponseEntity.ok(ApiResponse.success("환율 데이터가 없습니다", null));
      }

      return ResponseEntity.ok(ApiResponse.success(fxRate.get()));

    } catch (Exception e) {
      log.error("최신 환율 조회 중 오류: error={}", e.getMessage(), e);
      throw e;
    }
  }

  @PostMapping("/fx/generate")
  public ResponseEntity<ApiResponse<Integer>> generateHistoricalFxRates(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(defaultValue = "1350") BigDecimal baseRate) {
    try {
      log.info("과거 환율 데이터 생성 요청: {} ~ {}, 기준환율={}", startDate, endDate, baseRate);

      int savedCount = fxRateService.generateHistoricalRates(startDate, endDate, baseRate);
      
      return ResponseEntity.ok(ApiResponse.success("과거 환율 데이터 생성 완료", savedCount));

    } catch (Exception e) {
      log.error("과거 환율 생성 중 오류: error={}", e.getMessage(), e);
      throw e;
    }
  }

}