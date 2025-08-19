package com.muscat.marketdata.feed;

import com.muscat.marketdata.domain.dto.BatchResult;
import com.muscat.marketdata.feed.service.CandleBatchService;
import com.muscat.marketdata.feed.service.CandleUpdateService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데이터 수집 배치 작업 REST API
 * 주식 캔들 및 배당 데이터 수집을 위한 엔드포인트 제공
 */
@RestController
@RequestMapping("/batch")
@RequiredArgsConstructor
public class DataCollectionController {

  private static final ZoneId MARKET_TIMEZONE = ZoneId.of("America/New_York");
  private static final int DEFAULT_LOOKBACK_DAYS_ALL = 365;
  private static final int DEFAULT_LOOKBACK_DAYS_SINGLE = 90;

  private final CandleBatchService batchService;
  private final CandleUpdateService candleUpdateService;

  /**
   * 전체 종목 데이터 수집
   */
  @PostMapping("/candles")
  public Map<String, Object> collectAllSymbols(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "true") boolean includeDividends) {

    LocalDate endDate = to != null ? to : LocalDate.now(MARKET_TIMEZONE);
    LocalDate startDate = from != null ? from : endDate.minusDays(DEFAULT_LOOKBACK_DAYS_ALL);

    BatchResult result = batchService.collectAll(startDate, endDate, includeDividends);

    return Map.of(
        "period", Map.of("from", startDate, "to", endDate),
        "summary", Map.of(
            "totalSymbols", result.totalSymbols(),
            "successCount", result.successCount(),
            "failureCount", result.failureCount(),
            "totalSavedRecords", result.totalSavedRecords()
        ),
        "options", Map.of("includeDividends", includeDividends)
    );
  }

  /**
   * 단일 종목 데이터 수집
   */
  @PostMapping("/candles/{symbol}")
  public Map<String, Object> collectSingleSymbol(
      @PathVariable String symbol,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "true") boolean includeDividends) {

    LocalDate endDate = to != null ? to : LocalDate.now(MARKET_TIMEZONE);
    LocalDate startDate = from != null ? from : endDate.minusDays(DEFAULT_LOOKBACK_DAYS_SINGLE);

    int savedRecords = includeDividends
        ? candleUpdateService.saveBoth(symbol, startDate, endDate)
        : candleUpdateService.saveCandles(symbol, startDate, endDate);

    return Map.of(
        "symbol", symbol.toUpperCase(),
        "period", Map.of("from", startDate, "to", endDate),
        "result", Map.of(
            "savedRecords", savedRecords,
            "includeDividends", includeDividends
        )
    );
  }

  /**
   * 배치 작업 상태 조회
   */
  @GetMapping("/status")
  public Map<String, Object> getBatchStatus() {
    List<String> symbols = batchService.loadSymbols();

    return Map.of(
        "symbolsCount", symbols.size(),
        "marketTimezone", MARKET_TIMEZONE.toString(),
        "defaultLookbackDays", Map.of(
            "allAssets", DEFAULT_LOOKBACK_DAYS_ALL,
            "singleSymbol", DEFAULT_LOOKBACK_DAYS_SINGLE
        )
    );
  }
}