package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.infra.kafka.DataCollectionEventProducer;
import com.muscat.messaging.event.AssetCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 종목 하나의 캔들·배당을 수집한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCollectionService {

    private final CandleUpdateService candleUpdateService;
    private final DataCollectionEventProducer collectionEventProducer;

    /**
     * 컨슈머 스레드에서 그대로 수집한다.
     * 병렬도는 리스너 동시성이 정하고, 그래야 ACK 를 수집 뒤로 미룰 수 있다.
     */
    public void collect(AssetCreatedEvent event) {
        log.debug("수집 시작: symbol={}", event.getSymbol());
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
                log.debug("Data collection completed: symbol={}, totalRecords={}",
                        event.getSymbol(), totalRecords);
            } else {
                // 캔들만 수집
                candleCount = candleUpdateService.saveCandles(
                        event.getSymbol(),
                        event.getFromDate(),
                        event.getToDate()
                );
                log.debug("Candle collection completed: symbol={}, candleCount={}",
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
            log.error("수집 실패: symbol={}, error={}", event.getSymbol(), e.getMessage(), e);

            collectionEventProducer.publishFailed(
                    event.getSymbol(),
                    event.getFromDate(),
                    event.getToDate(),
                    e.getMessage(),
                    executionTime
            );

            // 종목 하나의 실패는 saveCandles 가 이미 삼킨다. 여기까지 오면 예상 밖이라
            // 재시도와 .DLT 로 보낸다
            throw e;
        }
    }
}
