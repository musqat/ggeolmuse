package com.muscat.marketdata.feed;

import com.muscat.marketdata.domain.dto.BatchResult;
import com.muscat.marketdata.feed.service.AssetUpdateService;
import com.muscat.marketdata.feed.service.CandleBatchService;
import com.muscat.marketdata.feed.service.CandleUpdateService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데이터 수집 관리용 컨트롤러 관리자 전용 API
 */
@Slf4j
@RestController
@RequestMapping("/batch")
@RequiredArgsConstructor
public class DataCollectionController {

  private static final ZoneId MARKET_TIMEZONE = ZoneId.of("America/New_York");
  private static final int DEFAULT_LOOKBACK_DAYS_ALL = 365;
  private static final int DEFAULT_LOOKBACK_DAYS_SINGLE = 90;

  private final CandleBatchService batchService;
  private final CandleUpdateService candleUpdateService;
  private final AssetUpdateService assetUpdateService;

  @PostMapping("/candles")
  public ResponseEntity<Map<String, Object>> collectAllSymbols(
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
    @RequestParam(defaultValue = "true") boolean includeDividends) {

    try {
      log.info("전체 심볼 데이터 수집 시작: from={}, to={}, includeDividends={}", from, to, includeDividends);

      LocalDate endDate = to != null ? to : LocalDate.now(MARKET_TIMEZONE);
      LocalDate startDate = from != null ? from : endDate.minusDays(DEFAULT_LOOKBACK_DAYS_ALL);

      BatchResult result = batchService.collectAll(startDate, endDate, includeDividends);

      Map<String, Object> responseData = Map.of(
        "period", Map.of("from", startDate, "to", endDate),
        "summary", Map.of(
          "totalSymbols", result.totalSymbols(),
          "successCount", result.successCount(),
          "failureCount", result.failureCount(),
          "totalSavedRecords", result.totalSavedRecords()
        ),
        "options", Map.of("includeDividends", includeDividends)
      );

      log.info("전체 심볼 데이터 수집 완료: 성공={}, 실패={}", result.successCount(), result.failureCount());
      return ResponseEntity.ok(responseData);

    } catch (Exception e) {
      log.error("전체 심볼 데이터 수집 중 오류 발생", e);
      // GlobalExceptionHandler가 처리하도록 Exception을 다시 throw
      throw e;
    }
  }

  @PostMapping("/candles/{symbol}")
  public ResponseEntity<Map<String, Object>> collectSingleSymbol(
    @PathVariable String symbol,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
    @RequestParam(defaultValue = "true") boolean includeDividends) {

    try {
      log.info("단일 심볼 데이터 수집 시작: symbol={}, from={}, to={}", symbol, from, to);

      LocalDate endDate = to != null ? to : LocalDate.now(MARKET_TIMEZONE);
      LocalDate startDate = from != null ? from : endDate.minusDays(DEFAULT_LOOKBACK_DAYS_SINGLE);

      int savedRecords = includeDividends
        ? candleUpdateService.saveBoth(symbol, startDate, endDate)
        : candleUpdateService.saveCandles(symbol, startDate, endDate);

      Map<String, Object> responseData = Map.of(
        "symbol", symbol.toUpperCase(),
        "period", Map.of("from", startDate, "to", endDate),
        "result", Map.of(
          "savedRecords", savedRecords,
          "includeDividends", includeDividends
        )
      );

      log.info("단일 심볼 데이터 수집 완료: symbol={}, savedRecords={}", symbol, savedRecords);
      return ResponseEntity.ok(responseData);

    } catch (Exception e) {
      log.error("단일 심볼 데이터 수집 중 오류 발생: symbol={}", symbol, e);
      throw e;
    }
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getBatchStatus() {
    try {
      log.debug("배치 상태 조회 요청");

      List<String> symbols = batchService.loadSymbols();

      Map<String, Object> responseData = Map.of(
        "symbolsCount", symbols.size(),
        "marketTimezone", MARKET_TIMEZONE.toString(),
        "defaultLookbackDays", Map.of(
          "allAssets", DEFAULT_LOOKBACK_DAYS_ALL,
          "singleSymbol", DEFAULT_LOOKBACK_DAYS_SINGLE
        )
      );

      return ResponseEntity.ok(responseData);

    } catch (Exception e) {
      log.error("배치 상태 조회 중 오류 발생", e);
      throw e;
    }
  }

  @PostMapping("/market-cap")
  public ResponseEntity<Map<String, Object>> updateAllMarketCaps() {
    try {
      log.info("전체 시가총액 업데이트 요청");

      int successCount = assetUpdateService.updateAllMarketCaps();

      Map<String, Object> responseData = Map.of(
        "message", "시가총액 업데이트 완료",
        "successCount", successCount
      );

      log.info("전체 시가총액 업데이트 완료: 성공={}", successCount);
      return ResponseEntity.ok(responseData);

    } catch (Exception e) {
      log.error("전체 시가총액 업데이트 중 오류 발생", e);
      throw e;
    }
  }

  @PostMapping("/market-cap/{symbol}")
  public ResponseEntity<Map<String, Object>> updateSingleMarketCap(@PathVariable String symbol) {
    try {
      log.info("단일 시가총액 업데이트 요청: symbol={}", symbol);

      boolean success = assetUpdateService.updateMarketCap(symbol);

      Map<String, Object> responseData = Map.of(
        "symbol", symbol.toUpperCase(),
        "success", success,
        "message", success ? "시가총액 업데이트 성공" : "시가총액 업데이트 실패"
      );

      log.info("단일 시가총액 업데이트 완료: symbol={}, success={}", symbol, success);
      return ResponseEntity.ok(responseData);

    } catch (Exception e) {
      log.error("단일 시가총액 업데이트 중 오류 발생: symbol={}", symbol, e);
      throw e;
    }
  }

}
