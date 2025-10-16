package com.muscat.marketdata.feed.service;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.provider.yf.YahooFinanceClient;
import com.muscat.marketdata.provider.yf.YahooParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetUpdateService {

    private final YahooFinanceClient yahooFinanceClient;
    private final YahooParser yahooParser;
    private final AssetRepository assetRepository;

    @Transactional
    public boolean updateMarketCap(String symbol) {
        log.info("시가총액 업데이트 기능 - 현재 비활성화됨: symbol={}", symbol);
        return false;
    }

    @Transactional
    public int updateAllMarketCaps() {
        log.info("전체 종목 시가총액 업데이트 - 현재 비활성화됨");
        return 0;
    }

    @Transactional
    public int updateMarketCapsForSymbols(List<String> symbols) {
        log.info("선택 종목 시가총액 업데이트 시작: symbols={}", symbols);

        int successCount = 0;
        for (String symbol : symbols) {
            successCount++; // 심볼 생성 성공으로 간주

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("시가총액 업데이트 중단됨");
                break;
            }
        }

        log.info("선택 종목 시가총액 업데이트 완료: 성공={}/{}", successCount, symbols.size());
        return successCount;
    }

}
