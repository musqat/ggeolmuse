package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.messaging.event.AssetCreatedEvent;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 종목 하나의 캔들·배당을 수집한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCollectionService {

    private final CandleUpdateService candleUpdateService;
    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final MarketDataProperties properties;

    // 컨슈머 스레드에서 그대로 수집. 병렬도는 리스너 동시성이 정하고 ACK 는 수집 뒤
    public void collect(AssetCreatedEvent event) {
        log.debug("수집 시작: symbol={}", event.getSymbol());

        int candleCount = 0;
        int dividendCount = 0;

        try {
            // 이 종목의 캔들이 처음 들어오는 수집인지
            boolean firstCollection = candleRepository.findFirstBySymbolOrderByDateDesc(event.getSymbol()).isEmpty();
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
            if (firstCollection && candleCount > 0) {
                deactivateThinEtf(event.getSymbol());
            }
            log.debug("수집 완료: symbol={}, 캔들={}, 배당={}", event.getSymbol(), candleCount, dividendCount);
        } catch (Exception e) {
            log.error("수집 실패: symbol={}, error={}", event.getSymbol(), e.getMessage(), e);

            // 종목 하나의 실패는 saveCandles · saveDividends 가 이미 삼킨다. 여기까지 오면 예상 밖이라
            // 재시도와 .DLT 로 보낸다
            throw e;
        }
    }

    // 거래가 거의 없는 ETF 는 첫 수집 뒤 내림. 갱신 대상과 화면에서 빠짐
    private void deactivateThinEtf(String symbol) {
        Asset asset = assetRepository.findById(symbol).orElse(null);
        if (asset == null || !"ETF".equals(asset.getAssetType()) || !Boolean.TRUE.equals(asset.getActive())) {
            return;
        }
        MarketDataProperties.Etf rule = properties.getEtf();
        List<Candle> recent = candleRepository.findBySymbolOrderByDateDesc(symbol, PageRequest.of(0, rule.getVolumeWindow()));
        OptionalDouble average = recent.stream().map(Candle::getVolume).filter(Objects::nonNull)
            .mapToLong(Long::longValue).average();
        if (average.isPresent() && average.getAsDouble() < rule.getMinAverageVolume()) {
            asset.setActive(false);
            assetRepository.save(asset);
            log.info("평균 거래량이 기준 아래라 ETF 비활성: symbol={}, 평균={}", symbol, (long) average.getAsDouble());
        }
    }
}
