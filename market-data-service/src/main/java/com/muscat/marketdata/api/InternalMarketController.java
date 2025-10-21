package com.muscat.marketdata.api;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.service.MarketService;
import com.muscat.marketdata.feed.service.FxRateService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 서비스 간 통신용 컨트롤러 다른 마이크로서비스에서만 호출되는 API들
 */
@Slf4j
@RestController
@Validated
@RequestMapping("/api/internal/market")
@RequiredArgsConstructor
public class InternalMarketController {

  private final MarketService marketService;
  private final FxRateService fxRateService;

  @GetMapping("/ohlc/{symbol}")
  public ResponseEntity<OHLCPriceDto> getOHLCPrice(
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

    log.debug("내부 OHLC 조회 요청: symbol={}, date={}", symbol, date);

    OHLCPriceDto result = marketService.getOHLCPrice(symbol, date);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/ohlc/{symbol}/range")
  public ResponseEntity<List<OHLCPriceDto>> getOHLCPriceRange(
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    log.debug("내부 OHLC 범위 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    List<OHLCPriceDto> result = marketService.getOHLCPriceRange(symbol, startDate, endDate);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/ohlc/{symbol}/with-dividends")
  public ResponseEntity<List<OHLCPriceDto>> getCandlesWithDividends(
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    log.debug("내부 배당 포함 캔들 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    List<OHLCPriceDto> result = marketService.getCandlesWithDividends(symbol, startDate, endDate);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/price/{symbol}")
  public ResponseEntity<StockPriceDto> getCurrentPrice(
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol) {
    log.debug("내부 현재가 조회 요청: symbol={}", symbol);

    StockPriceDto result = marketService.getCurrentPrice(symbol);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/prices")
  public ResponseEntity<java.util.Map<String, StockPriceDto>> getCurrentPrices(
    @RequestParam("symbols") List<String> symbols) {
    log.debug("내부 다중 현재가 조회 요청: symbols={}", symbols);

    java.util.Map<String, StockPriceDto> results = new java.util.HashMap<>();
    for (String symbol : symbols) {
      try {
        StockPriceDto price = marketService.getCurrentPrice(symbol.toUpperCase());
        results.put(symbol.toUpperCase(), price);
      } catch (Exception e) {
        log.warn("현재가 조회 실패: symbol={}, error={}", symbol, e.getMessage());
      }
    }

    return ResponseEntity.status(HttpStatus.OK).body(results);
  }

  @GetMapping("/fx/{date}")
  public ResponseEntity<FxRate> getFxRate(
    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    log.debug("내부 환율 조회 요청: date={}", date);

    FxRate fxRate = fxRateService.findByDate(date);

    if (fxRate == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    return ResponseEntity.status(HttpStatus.OK).body(fxRate);
  }

  @GetMapping("/fx/latest")
  public ResponseEntity<FxRate> getLatestFxRate() {
    log.debug("내부 최신 환율 조회 요청");

    var fxRate = fxRateService.getLatestRate();

    if (fxRate.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    return ResponseEntity.status(HttpStatus.OK).body(fxRate.get());
  }

  @GetMapping("/fx/bulk")
  public ResponseEntity<java.util.Map<String, java.math.BigDecimal>> getBulkFxRates(
    @RequestParam("dates") List<LocalDate> dates) {
    log.debug("내부 Bulk 환율 조회 요청: dates size={}", dates.size());

    List<FxRate> fxRates = fxRateService.findByDates(dates);

    // 날짜 문자열 -> 환율
    java.util.Map<String, java.math.BigDecimal> result = new java.util.HashMap<>();
    for (FxRate rate : fxRates) {
      result.put(rate.getDate().toString(), rate.getRate());
    }

    log.debug("Bulk 환율 조회 완료: {}개 요청, {}개 반환", dates.size(), result.size());

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/dividend/{symbol}")
  public ResponseEntity<List<DividendDto>> getDividendHistory(
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
    @RequestParam(required = false) LocalDate startDate,
    @RequestParam(required = false) LocalDate endDate) {
    log.debug("내부 배당 이력 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    List<DividendDto> dividends = marketService.getDividendHistory(symbol, startDate, endDate);

    return ResponseEntity.status(HttpStatus.OK).body(dividends);
  }
}
