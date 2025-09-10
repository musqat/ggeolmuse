package com.muscat.marketdata.api;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.service.MarketService;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.feed.service.FxRateService;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

  private final MarketService marketService;
  private final FxRateService fxRateService;

  @GetMapping("/ohlc/{symbol}")
  public ResponseEntity<OHLCPriceDto> getOHLCPrice(
      @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
      @RequestParam LocalDate date) {

    log.debug("OHLC 조회 요청: symbol={}, date={}", symbol, date);

    OHLCPriceDto result = marketService.getOHLCPrice(symbol, date);
    
    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/price/{symbol}")
  public ResponseEntity<StockPriceDto> getCurrentPrice(
      @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol) {
    log.debug("현재가 조회 요청: symbol={}", symbol);

    StockPriceDto result = marketService.getCurrentPrice(symbol);
    
    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/fx/{date}")
  public ResponseEntity<FxRate> getFxRate(@PathVariable LocalDate date) {
    log.debug("환율 조회 요청: date={}", date);

    FxRate fxRate = fxRateService.findByDate(date);
    
    if (fxRate == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    return ResponseEntity.status(HttpStatus.OK).body(fxRate);
  }

  @GetMapping("/fx/latest")
  public ResponseEntity<FxRate> getLatestFxRate() {
    log.debug("최신 환율 조회 요청");

    var fxRate = fxRateService.getLatestRate();
    
    if (fxRate.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    return ResponseEntity.status(HttpStatus.OK).body(fxRate.get());
  }

  @PostMapping("/fx/generate")
  public ResponseEntity<Integer> generateHistoricalFxRates(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(defaultValue = "1350") BigDecimal baseRate) {
    log.info("과거 환율 데이터 생성 요청: {} ~ {}, 기준환율={}", startDate, endDate, baseRate);

    int savedCount = fxRateService.generateHistoricalRates(startDate, endDate, baseRate);
    
    return ResponseEntity.status(HttpStatus.OK).body(savedCount);
  }

  @GetMapping("/dividend/{symbol}")
  public ResponseEntity<List<DividendDto>> getDividendHistory(
      @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate) {
    log.debug("배당 이력 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    List<DividendDto> dividends = marketService.getDividendHistory(symbol, startDate, endDate);
    
    return ResponseEntity.status(HttpStatus.OK).body(dividends);
  }

  // ===== 새로운 QueryDSL 활용 API들 =====

  @GetMapping("/ohlc/multiple")
  public ResponseEntity<List<OHLCPriceDto>> getMultipleOHLCPrices(
      @RequestParam @Size(min=1, max=50) List<@Pattern(regexp = "^[A-Z]{1,16}$") String> symbols,
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate) {
    log.debug("다중 OHLC 조회 요청: symbols={}, startDate={}, endDate={}", symbols, startDate, endDate);

    List<OHLCPriceDto> result = marketService.getMultipleOHLCPrices(symbols, startDate, endDate);
    
    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/ohlc/{symbol}/with-dividends")
  public ResponseEntity<List<OHLCPriceDto>> getCandlesWithDividends(
      @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate) {
    log.debug("배당 포함 캔들 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    List<OHLCPriceDto> result = marketService.getCandlesWithDividends(symbol, startDate, endDate);
    
    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping("/dividend/high-yield")
  public ResponseEntity<List<DividendDto>> findHighDividendStocks(
      @RequestParam @Positive BigDecimal minAmount,
      @RequestParam(required = false) LocalDate fromDate) {
    log.debug("고배당주 검색 요청: minAmount={}, fromDate={}", minAmount, fromDate);

    LocalDate searchFromDate = fromDate != null ? fromDate : LocalDate.now().minusYears(1);
    List<DividendDto> result = marketService.findHighDividendStocks(minAmount, searchFromDate);
    
    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

}