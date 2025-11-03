package com.muscat.marketdata.datasource.yf.collector;

import com.muscat.marketdata.datasource.yf.provider.YfSymbolSource;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Yahoo Finance 종목 자동 수집 (이벤트 기반)
 *
 * Asset 저장 후 Kafka 이벤트를 발행하여 비동기로 캔들 데이터를 수집합니다.
 * NASDAQ API (우선) 또는 CSV 파일 (fallback)에서 종목 정보를 로드합니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "yahoo"
)
@RequiredArgsConstructor
public class SymbolCollector {

    private final YfSymbolSource symbolSource;
    private final AssetRepository assetRepository;
    private final AssetEventProducer assetEventProducer;

    @Value("${marketdata.symbol-collection.enabled:false}")
    private boolean enabled;

    @Value("${marketdata.symbol-collection.lookback-days:365}")
    private int lookbackDays;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    @Transactional
    public void collectSymbols() {
        if (!enabled) {
            log.info("[YF-종목수집] 비활성화됨 (marketdata.symbol-collection.enabled=false)");
            return;
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(lookbackDays);

        // 기존 심볼이 있는지 확인
        long existingCount = assetRepository.count();

        if (existingCount > 0) {
            // 기존 심볼들에 대해서만 데이터 수집 이벤트 발행 (심볼 추가 안 함)
            log.info("[YF-종목수집] 기존 {}개 종목 발견 - 데이터 업데이트만 수행 (종목 추가 안 함)", existingCount);

            List<Asset> existingAssets = assetRepository.findAll();
            int eventCount = 0;

            for (Asset asset : existingAssets) {
                try {
                    // Kafka 이벤트 발행 (비동기 캔들 수집)
                    assetEventProducer.publishAssetCreated(
                        asset,
                        true,           // collectData
                        from,
                        to,
                        true            // includeDividends
                    );
                    eventCount++;

                    if (eventCount % 100 == 0) {
                        log.info("[YF-종목수집] 진행 중: {}/{} 이벤트 발행", eventCount, existingAssets.size());
                    }
                } catch (Exception e) {
                    log.error("[YF-종목수집] 이벤트 발행 실패: symbol={}", asset.getSymbol(), e);
                }
            }

            log.info("[YF-종목수집] 완료: {}개 기존 종목에 대해 데이터 수집 이벤트 발행", eventCount);
            return;
        }

        // 기존 심볼이 없으면 새로 로드
        log.info("[YF-종목수집] 신규 종목 로드 시작: NASDAQ API → CSV fallback");

        try {
            // SymbolSource를 통해 종목 로드 (API → CSV fallback)
            List<Asset> symbols = symbolSource.fetchSymbols();

            if (symbols.isEmpty()) {
                log.warn("[YF-종목수집] 실패: 종목을 찾을 수 없음");
                return;
            }

            log.info("[YF-종목수집] 종목 로드 완료: {}개", symbols.size());

            int savedCount = 0;
            int eventCount = 0;

            for (Asset asset : symbols) {
                try {
                    // Asset 저장
                    Asset saved = assetRepository.save(asset);
                    savedCount++;

                    // Kafka 이벤트 발행 (비동기 캔들 수집)
                    assetEventProducer.publishAssetCreated(
                        saved,
                        true,           // collectData
                        from,
                        to,
                        true            // includeDividends
                    );
                    eventCount++;

                    if (savedCount % 100 == 0) {
                        log.info("[YF-종목수집] 진행 중: {}/{} 저장 완료", savedCount, symbols.size());
                    }

                } catch (Exception e) {
                    log.error("[YF-종목수집] 실패: symbol={}, error={}", asset.getSymbol(), e.getMessage());
                }
            }

            log.info("[YF-종목수집] 완료: Asset {}개 저장, Kafka 이벤트 {}개 발행", savedCount, eventCount);

        } catch (Exception e) {
            log.error("[YF-종목수집] 전체 실패", e);
        }
    }
}
