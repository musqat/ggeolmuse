package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.infra.kafka.DataCollectionEventProducer;
import com.muscat.messaging.event.AssetCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 데이터 수집을 비동기로 처리하는 서비스
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCollectionService {

    private final CandleUpdateService candleUpdateService;
    private final DataCollectionEventProducer collectionEventProducer;

    /**
     * 비동기로 데이터를 수집하는 메서드
     * 별도 스레드풀에서 실행됨
     */
    @Async
    public void collectDataAsync(AssetCreatedEvent event) {
        log.info("Starting async data collection: symbol={}", event.getSymbol());
        long startTime = System.currentTimeMillis();

        int candleCount = 0;
        int dividendCount = 0;

        try {
            if (event.isIncludeDividends()) {
                // 캔들 + 배당 동시 수집
                int totalRecords = candleUpdateService.saveBoth(
                        event.getSymbol(),
                        event.getFromDate(),
                        event.getToDate()
                );
                candleCount = totalRecords;
                log.info("Data collection completed: symbol={}, totalRecords={}",
                        event.getSymbol(), totalRecords);
            } else {
                // 캔들만 수집
                candleCount = candleUpdateService.saveCandles(
                        event.getSymbol(),
                        event.getFromDate(),
                        event.getToDate()
                );
                log.info("Candle collection completed: symbol={}, candleCount={}",
                        event.getSymbol(), candleCount);
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // 수집 완료 이벤트 발행
            collectionEventProducer.publishCompleted(
                    event.getSymbol(),
                    event.getFromDate(),
                    event.getToDate(),
                    candleCount,
                    dividendCount,
                    executionTime
            );

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Async data collection failed: symbol={}, error={}",
                    event.getSymbol(), e.getMessage(), e);

            // 수집 실패 이벤트 발행
            collectionEventProducer.publishFailed(
                    event.getSymbol(),
                    event.getFromDate(),
                    event.getToDate(),
                    e.getMessage(),
                    executionTime
            );
        }
    }
}
