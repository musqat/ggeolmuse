package com.muscat.marketdata.datasource.alphavantage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * AlphaVantage 시가총액 수집 서비스
 * OVERVIEW API를 호출하여 시가총액을 수집합니다.
 * Rate Limit: 75 calls/min (무료 플랜)
 * - Background job으로 실행하여 점진적으로 업데이트
 */
@Slf4j
@Service
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class MarketCapCollectionService {

    private final AlphaVantageClient client;
    private final AlphaVantageRateLimiter rateLimiter;
    private final AssetRepository assetRepository;

    @Value("${marketdata.market-cap-collection.enabled:true}")
    private boolean enabled;

    @Value("${marketdata.market-cap-collection.batch-size:100}")
    private int batchSize;

    @Value("${marketdata.market-cap-collection.log-interval:10}")
    private int logInterval;

    /**
     * 시가총액이 null인 종목들에 대해 시가총액 수집
     * 비동기로 실행되며, Rate Limit을 준수합니다.
     */
    @Async
    @Transactional
    public void collectMarketCaps() {
        if (!enabled) {
            log.info("[AV-MarketCap] 비활성화됨 (marketdata.market-cap-collection.enabled=false)");
            return;
        }

        List<Asset> assetsWithoutMarketCap = assetRepository.findByMarketCapIsNull();

        if (assetsWithoutMarketCap.isEmpty()) {
            log.info("[AV-MarketCap] 시가총액 수집 필요 없음 (모든 종목에 시가총액 존재)");
            return;
        }

        int totalCount = assetsWithoutMarketCap.size();
        int processCount = Math.min(totalCount, batchSize);

        log.info("[AV-MarketCap] 시가총액 수집 시작: 대상 {}개 / 이번 배치 {}개",
                 totalCount, processCount);

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < processCount; i++) {
            Asset asset = assetsWithoutMarketCap.get(i);

            try {
                Long marketCap = fetchMarketCap(asset.getSymbol());

                if (marketCap != null) {
                    asset.setMarketCap(marketCap);
                    assetRepository.save(asset);
                    successCount++;
                } else {
                    failCount++;
                }

                // 진행 상황 로깅
                if ((i + 1) % logInterval == 0) {
                    log.info("[AV-MarketCap] 진행: {}/{} (성공: {}, 실패: {})",
                             i + 1, processCount, successCount, failCount);
                }

            } catch (Exception e) {
                log.error("[AV-MarketCap] 실패: symbol={}", asset.getSymbol(), e);
                failCount++;
            }
        }

        log.info("[AV-MarketCap] 완료: {}/{} 성공, {} 실패, 남은 종목: {}",
                 successCount, processCount, failCount, totalCount - processCount);

        if (totalCount > processCount) {
            log.info("[AV-MarketCap] 다음 배치에서 계속 수집 예정 (남은 {}개)",
                     totalCount - processCount);
        }
    }

    /**
     * OVERVIEW API를 통해 특정 종목의 시가총액 조회
     */
    private Long fetchMarketCap(String symbol) {
        rateLimiter.waitIfNeeded();

        try {
            JsonNode response = client.get("OVERVIEW", Map.of("symbol", symbol));

            if (!response.has("Symbol")) {
                log.warn("[AV-MarketCap] 종목 정보 없음: {}", symbol);
                return null;
            }

            if (response.has("MarketCapitalization")) {
                String marketCapStr = response.get("MarketCapitalization").asText();
                try {
                    return Long.parseLong(marketCapStr);
                } catch (NumberFormatException e) {
                    log.warn("[AV-MarketCap] 시가총액 파싱 실패: symbol={}, value={}",
                             symbol, marketCapStr);
                    return null;
                }
            }

            return null;

        } catch (Exception e) {
            log.error("[AV-MarketCap] API 호출 실패: symbol={}", symbol, e);
            throw e;
        }
    }

    /**
     * 특정 종목의 시가총액만 업데이트
     */
    @Transactional
    public void updateMarketCap(String symbol) {
        Asset asset = assetRepository.findById(symbol).orElse(null);
        if (asset == null) {
            log.warn("[AV-MarketCap] 종목을 찾을 수 없음: {}", symbol);
            return;
        }

        try {
            Long marketCap = fetchMarketCap(symbol);
            if (marketCap != null) {
                asset.setMarketCap(marketCap);
                assetRepository.save(asset);
                log.info("[AV-MarketCap] 업데이트 성공: symbol={}, marketCap={}",
                         symbol, marketCap);
            }
        } catch (Exception e) {
            log.error("[AV-MarketCap] 업데이트 실패: symbol={}", symbol, e);
        }
    }

    /**
     * 모든 활성 종목의 시가총액 업데이트
     * 스케줄러에서 호출되며, Rate Limit을 준수합니다.
     */
    @Transactional
    public void updateAllMarketCaps() {
        List<Asset> assets = assetRepository.findByActiveTrue();

        if (assets.isEmpty()) {
            log.info("[AV-MarketCap] 업데이트할 활성 종목 없음");
            return;
        }

        log.info("[AV-MarketCap] 전체 업데이트 시작: 대상 {}개", assets.size());

        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;

        for (Asset asset : assets) {
            try {
                Long marketCap = fetchMarketCap(asset.getSymbol());

                if (marketCap != null && marketCap > 0) {
                    asset.setMarketCap(marketCap);
                    assetRepository.save(asset);
                    successCount++;

                    if (successCount % logInterval == 0) {
                        log.info("[AV-MarketCap] 진행: {}/{} (성공: {}, 실패: {}, 스킵: {})",
                                 successCount + failCount + skipCount, assets.size(),
                                 successCount, failCount, skipCount);
                    }
                } else {
                    skipCount++;
                    log.debug("[AV-MarketCap] 시가총액 정보 없음: {}", asset.getSymbol());
                }

            } catch (Exception e) {
                failCount++;
                log.warn("[AV-MarketCap] 업데이트 실패: symbol={}, error={}",
                         asset.getSymbol(), e.getMessage());
            }
        }

        log.info("[AV-MarketCap] 전체 업데이트 완료: 성공 {}개, 실패 {}개, 스킵 {}개",
                 successCount, failCount, skipCount);
    }
}
