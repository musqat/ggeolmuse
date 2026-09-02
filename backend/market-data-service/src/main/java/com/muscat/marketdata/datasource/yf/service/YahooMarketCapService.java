package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerClient;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerParser;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NASDAQ Screener API를 이용한 시가총액 일괄 업데이트 서비스
 * Yahoo Finance quote API가 인증 필요하여 NASDAQ 공개 API 사용
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "yahoo")
@RequiredArgsConstructor
public class YahooMarketCapService implements
  com.muscat.marketdata.datasource.common.MarketDataProvider.MarketCapSource {

    private final NasdaqScreenerClient nasdaqClient;
    private final NasdaqScreenerParser nasdaqParser;
    private final AssetRepository assetRepository;

    /**
     * NASDAQ Screener에서 전체 시가총액 조회 후 Map으로 반환
     */
    private Map<String, Long> fetchMarketCapMap() {
        Map<String, Long> marketCapMap = new HashMap<>();

        try {
            // NYSE 종목
            String nyseJson = nasdaqClient.getAllStocks("nyse", "mega,large,mid,small");
            List<Asset> nyseAssets = nasdaqParser.parseStocks(nyseJson);
            nyseAssets.stream()
                .filter(a -> a.getMarketCap() != null)
                .forEach(a -> marketCapMap.put(a.getSymbol(), a.getMarketCap()));

            Thread.sleep(500);

            // NASDAQ 종목
            String nasdaqJson = nasdaqClient.getAllStocks("nasdaq", "mega,large,mid,small");
            List<Asset> nasdaqAssets = nasdaqParser.parseStocks(nasdaqJson);
            nasdaqAssets.stream()
                .filter(a -> a.getMarketCap() != null)
                .forEach(a -> marketCapMap.put(a.getSymbol(), a.getMarketCap()));

            log.info("[YF-시총] NASDAQ Screener 수집 완료: {}개 종목", marketCapMap.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[YF-시총] NASDAQ Screener 호출 실패: {}", e.getMessage());
        }

        return marketCapMap;
    }

    /**
     * 전체 활성 종목 시가총액 업데이트 (스케줄러용)
     */
    @Override
    @Transactional
    public int updateAllMarketCaps(List<Asset> assets) {
        log.info("[YF-시총] 전체 업데이트 시작: {}개 종목", assets.size());

        Map<String, Long> marketCapMap = fetchMarketCapMap();
        if (marketCapMap.isEmpty()) {
            log.warn("[YF-시총] 시가총액 데이터 없음, 업데이트 중단");
            return 0;
        }

        int updated = 0;
        for (Asset asset : assets) {
            Long marketCap = marketCapMap.get(asset.getSymbol());
            if (marketCap != null && marketCap > 0) {
                asset.setMarketCap(marketCap);
                assetRepository.save(asset);
                updated++;
            }
        }

        log.info("[YF-시총] 전체 업데이트 완료: {}/{}개", updated, assets.size());
        return updated;
    }

    /**
     * 단일 종목 시가총액 업데이트 (관리자 UI용)
     */
    @Override
    @Transactional
    public boolean updateMarketCap(String symbol) {
        Map<String, Long> marketCapMap = fetchMarketCapMap();
        Long marketCap = marketCapMap.get(symbol.toUpperCase());

        if (marketCap == null || marketCap == 0) {
            log.warn("[YF-시총] 시가총액 없음: symbol={}", symbol);
            return false;
        }

        assetRepository.findById(symbol.toUpperCase()).ifPresent(asset -> {
            asset.setMarketCap(marketCap);
            assetRepository.save(asset);
            log.info("[YF-시총] 업데이트: symbol={}, marketCap={}", symbol, marketCap);
        });

        return true;
    }
}
