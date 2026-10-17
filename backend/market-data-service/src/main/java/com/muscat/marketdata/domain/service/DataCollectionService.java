package com.muscat.marketdata.domain.service;

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

    // 컨슈머 스레드에서 그대로 수집. 병렬도는 리스너 동시성이 정하고 ACK 는 수집 뒤
    public void collect(AssetCreatedEvent event) {
        log.debug("수집 시작: symbol={}", event.getSymbol());

        int candleCount = 0;
        int dividendCount = 0;

        try {
            candleCount = candleUpdateService.saveCandles(
                    event.getSymbol(),
                    event.getFromDate(),
                    event.getToDate()
            );
            if (event.isIncludeDividends()) {
                dividendCount = candleUpdateService.saveDividends(
                        event.getSymbol(),
                        event.getFromDate(),
                        event.getToDate()
                );
            }
            log.debug("수집 완료: symbol={}, 캔들={}, 배당={}", event.getSymbol(), candleCount, dividendCount);
        } catch (Exception e) {
            log.error("수집 실패: symbol={}, error={}", event.getSymbol(), e.getMessage(), e);

            // 종목 하나의 실패는 saveCandles · saveDividends 가 이미 삼킨다. 여기까지 오면 예상 밖이라
            // 재시도와 .DLT 로 보낸다
            throw e;
        }
    }
}
