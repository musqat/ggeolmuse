package com.muscat.marketdata.feed.service;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.BatchResult;
import com.muscat.marketdata.domain.repository.AssetRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleBatchService {

  private final AssetRepository assetRepository;
  private final CandleUpdateService candleUpdateService;
  private final MarketDataProperties properties;

  private static final int BACKOFF_MULTIPLIER = 2;

  @Value("${feed.rpm:5}")
  private int requestsPerMinute;

  @Value("${feed.retry.max:3}")
  private int maxRetryAttempts;

  @Value("${feed.retry.backoffMs:2000}")
  private long initialBackoffMs;

  public List<String> loadSymbols() {
    return assetRepository.findAll().stream()
        .map(asset -> asset.getSymbol())
        .distinct()
        .sorted(String::compareToIgnoreCase)
        .toList();
  }

  public BatchResult collectAll(LocalDate fromDate, LocalDate toDate, boolean includeDividends) {
    return executeCollectionBatch(fromDate, toDate, includeDividends);
  }

  private BatchResult executeCollectionBatch(LocalDate fromDate, LocalDate toDate,
      boolean includeDividends) {
    List<String> symbols = loadSymbols();
    long sleepIntervalMs = calculateSleepInterval();

    int successCount = 0;
    int failureCount = 0;
    int totalSavedRecords = 0;

    log.info("배치 수집 시작: 심볼수={}, 기간=[{}~{}], RPM={}, 배당포함={}",
        symbols.size(), fromDate, toDate, requestsPerMinute, includeDividends);

    for (String symbol : symbols) {
      try {
        int savedRecords = tryWithRetry(() ->
            includeDividends
                ? candleUpdateService.saveBoth(symbol, fromDate, toDate)
                : candleUpdateService.saveCandles(symbol, fromDate, toDate));

        totalSavedRecords += savedRecords;
        successCount++;
      } catch (Exception e) {
        failureCount++;
        log.warn("심볼 {} 수집 실패: {}", symbol, e.getMessage());
      } finally {
        sleepQuietly(sleepIntervalMs);
      }
    }

    BatchResult result = new BatchResult(
        symbols.size(),
        successCount,
        failureCount,
        totalSavedRecords,
        fromDate,
        toDate
    );

    log.info("배치 수집 완료: {}", result);
    return result;
  }

  private long calculateSleepInterval() {
    int effectiveRpm = Math.max(requestsPerMinute, properties.getBatch().getMinRpm());
    return Math.max(0,
        Math.round((double) properties.getBatch().getMillisPerMinute() / effectiveRpm));
  }

  private <T> T tryWithRetry(RetryableTask<T> task) throws Exception {
    int attempt = 0;
    long currentBackoff = initialBackoffMs;
    Exception lastException = null;

    while (attempt < maxRetryAttempts) {
      try {
        return task.execute();
      } catch (Exception e) {
        lastException = e;
        attempt++;

        if (attempt >= maxRetryAttempts) {
          break;
        }

        log.debug("재시도 {}/{}: {}", attempt, maxRetryAttempts, e.getMessage());
        sleepQuietly(currentBackoff);
        currentBackoff = Math.min(currentBackoff * BACKOFF_MULTIPLIER, properties.getBatch().getMaxBackoffMs());
      }
    }

    throw lastException != null ? lastException : new RuntimeException("알 수 없는 오류");
  }

  private void sleepQuietly(long milliseconds) {
      if (milliseconds <= 0) {
          return;
      }

    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Sleep 중단됨");
    }
  }

  @FunctionalInterface
  private interface RetryableTask<T> {

    T execute() throws Exception;
  }
}