package com.muscat.marketdata.provider.alphavantage;

import com.fasterxml.jackson.databind.JsonNode;
import com.muscat.marketdata.common.exceptions.DataNotFoundException;
import com.muscat.marketdata.common.exceptions.MarketDataException;
import com.muscat.marketdata.common.logging.MarketDataLogger;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AlphaVantageSymbolSource {

    private final AlphaVantageClient client;
    private final MarketDataLogger marketDataLogger;

    // NASDAQ 100 주요 종목들
    private static final List<String> NASDAQ_100_SYMBOLS = Arrays.asList(
        "AAPL", "MSFT", "NVDA", "AMZN", "META", "GOOGL", "GOOG", "TSLA",
        "AVGO", "COST", "NFLX", "AMD", "ADBE", "PEP", "QCOM", "TMUS",
        "INTC", "CMCSA", "HON", "TXN", "AMGN", "SBUX", "INTU", "ISRG",
        "BKNG", "PANW", "AMAT", "ADP", "VRTX", "GILD", "MU", "MELI",
        "KLAC", "MDLZ", "LRCX", "PYPL", "REGN", "ABNB", "SNPS", "CDNS",
        "MAR", "MRVL", "ORLY", "FTNT", "CSX", "DASH", "ADSK", "ASML",
        "ROP", "CHTR", "NXPI", "WDAY", "MNST", "FANG", "TTD", "FAST",
        "ROST", "ODFL", "BZ", "VRSK", "EXC", "LULU", "KDP", "TEAM",
        "GEHC", "CSGP", "AEP", "XEL", "CTSH", "KHC", "IDXX", "FSLR",
        "ZS", "BIIB", "DDOG", "ANSS", "ON", "GFS", "WBD", "ARM",
        "ILMN", "MDB", "DLTR", "CDW", "ZM", "SMCI", "ALGN", "MRNA",
        "CRWD", "WBA", "SIRI", "LCID", "SGEN", "ENPH", "BMRN", "OKTA"
    );

    public List<Asset> fetchNasdaq100() {
        try {
            log.info("AlphaVantage에서 NASDAQ 100 종목 수집 시작");
            
            List<Asset> nasdaq100Assets = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (String symbol : NASDAQ_100_SYMBOLS) {
                try {
                    Asset asset = fetchSymbolInfo(symbol);
                    if (asset != null) {
                        nasdaq100Assets.add(asset);
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.warn("종목 {} 정보 조회 실패: {}", symbol, e.getMessage());
                    nasdaq100Assets.add(MarketDataMapper.toAsset(symbol, symbol)); // 기본값으로라도 추가
                    failureCount++;
                }
                
                // API 호출 제한을 위해 잠시 대기 (AlphaVantage 무료 플랜 고려)
                try {
                    Thread.sleep(200); // 200ms 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (nasdaq100Assets.isEmpty()) {
                throw new DataNotFoundException("NASDAQ100", "SYMBOLS");
            }
            
            marketDataLogger.logDataCollection("ALPHAVANTAGE", "NASDAQ100", "SYMBOLS", 
                nasdaq100Assets.size(), true, null);
            log.info("AlphaVantage NASDAQ 100 종목 수집 완료: {}개 (성공: {}, 실패: {})", 
                nasdaq100Assets.size(), successCount, failureCount);
            
            return nasdaq100Assets;
            
        } catch (Exception e) {
            String errorMsg = "NASDAQ 100 종목 수집 실패";
            log.error("{}: error={}", errorMsg, e.getMessage(), e);
            marketDataLogger.logDataCollection("ALPHAVANTAGE", "NASDAQ100", "SYMBOLS", 0, false, e.getMessage());
            throw new MarketDataException(errorMsg, e);
        }
    }

    private Asset fetchSymbolInfo(String symbol) {
        try {
            Map<String, String> params = Map.of("keywords", symbol);
            JsonNode response = client.get("SYMBOL_SEARCH", params);
            
            if (!response.has("bestMatches")) {
                log.debug("종목 {} 검색 결과 없음", symbol);
                return MarketDataMapper.toAsset(symbol, symbol);
            }
            
            JsonNode bestMatches = response.get("bestMatches");
            if (!bestMatches.isArray() || bestMatches.size() == 0) {
                log.debug("종목 {} 매치 결과 없음", symbol);
                return MarketDataMapper.toAsset(symbol, symbol);
            }
            
            // 첫 번째 매치에서 정확한 심볼 찾기
            for (JsonNode match : bestMatches) {
                String matchSymbol = match.get("1. symbol").asText();
                String matchName = match.get("2. name").asText();
                String region = match.get("4. region").asText();
                
                if (matchSymbol.equalsIgnoreCase(symbol) && "United States".equalsIgnoreCase(region)) {
                    return MarketDataMapper.toAsset(matchSymbol, matchName);
                }
            }
            
            // 정확한 매치가 없으면 기본값 반환
            return MarketDataMapper.toAsset(symbol, symbol);
            
        } catch (Exception e) {
            log.debug("종목 {} 정보 조회 중 오류: {}", symbol, e.getMessage());
            return MarketDataMapper.toAsset(symbol, symbol);
        }
    }

    public List<Asset> searchSymbols(String keywords) {
        try {
            log.debug("AlphaVantage 심볼 검색: keywords={}", keywords);
            
            Map<String, String> params = Map.of("keywords", keywords);
            JsonNode response = client.get("SYMBOL_SEARCH", params);
            
            if (!response.has("bestMatches")) {
                return Collections.emptyList();
            }
            
            JsonNode bestMatches = response.get("bestMatches");
            if (!bestMatches.isArray()) {
                return Collections.emptyList();
            }
            
            List<Asset> assets = new ArrayList<>();
            for (JsonNode match : bestMatches) {
                try {
                    String symbol = match.get("1. symbol").asText();
                    String name = match.get("2. name").asText();
                    String region = match.get("4. region").asText();
                    
                    if ("United States".equalsIgnoreCase(region)) {
                        assets.add(MarketDataMapper.toAsset(symbol, name));
                    }
                } catch (Exception e) {
                    log.debug("심볼 검색 결과 파싱 실패: {}", match, e);
                }
            }
            
            log.debug("AlphaVantage 심볼 검색 완료: keywords={}, results={}", keywords, assets.size());
            return assets;
            
        } catch (Exception e) {
            String errorMsg = "심볼 검색 실패";
            log.error("{}: keywords={}, error={}", errorMsg, keywords, e.getMessage());
            throw new MarketDataException(errorMsg, e);
        }
    }
}