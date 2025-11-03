package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.BatchResult;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Yahoo Finance 배치 데이터 수집 서비스
 *
 * 여러 종목의 캔들 및 배당 데이터를 배치로 수집합니다.
 * Rate limit 및 재시도 로직을 포함합니다.
 */
@Slf4j
@Service("yfCandleBatchService")
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "yahoo"
)
@RequiredArgsConstructor
public class CandleBatchService {

    private final AssetRepository assetRepository;
    private final YahooCandleUpdateService candleUpdateService;
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
                .map(Asset::getSymbol)
                .distinct()
                .sorted()
                .toList();
    }

    public BatchResult collectAll(LocalDate fromDate, LocalDate toDate, boolean includeDividends) {
        return executeCollectionBatch(fromDate, toDate, includeDividends);
    }

    private BatchResult executeCollectionBatch(LocalDate fromDate, LocalDate toDate, boolean includeDividends) {
        List<String> symbols = loadSymbols();
        long sleepIntervalMs = calculateSleepInterval();

        int successCount = 0;
        int failureCount = 0;
        int totalSavedRecords = 0;

        log.info("[YF-배치수집] 시작: 심볼수={}, 기간=[{}~{}], RPM={}, 배당포함={}",
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
                log.warn("[YF-배치수집] 심볼 {} 수집 실패: {}", symbol, e.getMessage());
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

        log.info("[YF-배치수집] 완료: {}", result);
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

                log.debug("[YF-배치수집] 재시도 {}/{}: {}", attempt, maxRetryAttempts, e.getMessage());
                sleepQuietly(currentBackoff);
                currentBackoff = Math.min(currentBackoff * BACKOFF_MULTIPLIER,
                        properties.getBatch().getMaxBackoffMs());
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
            log.warn("[YF-배치수집] Sleep 중단됨");
        }
    }

    @FunctionalInterface
    private interface RetryableTask<T> {
        T execute() throws Exception;
    }
}
