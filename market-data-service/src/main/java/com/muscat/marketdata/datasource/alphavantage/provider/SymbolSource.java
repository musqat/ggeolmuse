package com.muscat.marketdata.datasource.alphavantage.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.datasource.alphavantage.dto.SymbolListingDto;
import com.muscat.marketdata.datasource.common.MarketDataProvider;
import com.muscat.marketdata.domain.entity.Asset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AlphaVantage 종목 정보 소스
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class SymbolSource implements MarketDataProvider.SymbolSource {

    private final AlphaVantageClient client;
    private final AlphaVantageRateLimiter rateLimiter;

    @Value("${marketdata.symbol-listing.only-major-exchanges:true}")
    private boolean onlyMajorExchanges;

    @Value("${marketdata.symbol-listing.max-symbols:0}")
    private int maxSymbols;

    /**
     * LISTING_STATUS API를 사용하여 전체 종목 목록 조회
     * 시가총액 정보는 포함되지 않음
     */
    @Override
    public List<Asset> fetchSymbols() {
        log.info("[AV-SymbolSource] LISTING_STATUS API 호출 시작");

        rateLimiter.waitIfNeeded();

        try {
            String csv = client.getCsv("LISTING_STATUS", Map.of());

            List<SymbolListingDto> listings = parseCsv(csv);
            log.info("[AV-SymbolSource] CSV 파싱 완료: 전체 {}개 종목", listings.size());

            // 필터링: Active 상태, 주요 거래소만
            List<SymbolListingDto> filtered = listings.stream()
                .filter(SymbolListingDto::isActive)
                .filter(dto -> dto.isStock() || dto.isETF())
                .filter(dto -> !onlyMajorExchanges || dto.isMajorExchange())
                .limit(maxSymbols > 0 ? maxSymbols : Integer.MAX_VALUE)
                .collect(Collectors.toList());

            long stockCount = filtered.stream().filter(SymbolListingDto::isStock).count();
            long etfCount = filtered.stream().filter(SymbolListingDto::isETF).count();
            log.info("[AV-SymbolSource] 필터링 완료: {}개 종목 (Stock: {}, ETF: {})",
                     filtered.size(), stockCount, etfCount);

            // ETF 샘플 출력 (디버깅용)
            filtered.stream()
                .filter(SymbolListingDto::isETF)
                .limit(5)
                .forEach(dto -> log.info("[AV-SymbolSource] ETF 샘플: symbol={}, name={}, assetType={}",
                                          dto.getSymbol(), dto.getName(), dto.getAssetType()));

            // Asset 엔티티로 변환 (marketCap은 null - 추후 수집)
            List<Asset> assets = filtered.stream()
                .map(this::toAsset)
                .collect(Collectors.toList());

            log.info("[AV-SymbolSource] Asset 변환 완료: {}개", assets.size());
            return assets;

        } catch (Exception e) {
            log.error("[AV-SymbolSource] LISTING_STATUS API 호출 실패", e);
            return List.of();
        }
    }

    /**
     * CSV를 파싱하여 SymbolListingDto 리스트로 변환
     */
    private List<SymbolListingDto> parseCsv(String csv) {
        List<SymbolListingDto> result = new ArrayList<>();
        String[] lines = csv.split("\n");

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            SymbolListingDto dto = SymbolListingDto.fromCsvLine(line);
            if (dto != null) {
                result.add(dto);
            }
        }

        return result;
    }

    /**
     * SymbolListingDto를 Asset 엔티티로 변환
     */
    private Asset toAsset(SymbolListingDto dto) {
        return Asset.builder()
            .symbol(dto.getSymbol())
            .name(dto.getName())
            .country(mapExchangeToCountry(dto.getExchange()))
            .currency("USD")
            .assetType(dto.isETF() ? "ETF" : "EQUITY")
            .marketCap(null)
            .build();
    }

    private String mapExchangeToCountry(String exchange) {
        if (exchange == null) return "US";
        if (exchange.equalsIgnoreCase("NYSE") ||
            exchange.equalsIgnoreCase("NASDAQ") ||
            exchange.equalsIgnoreCase("NYSE ARCA")) {
            return "US";
        }
        return "US";  // 기본값
    }

    public Asset getAsset(String symbol) {
        rateLimiter.waitIfNeeded();

        JsonNode response = client.get("OVERVIEW", Map.of("symbol", symbol));

        if (!response.has("Symbol")) {
            return null;
        }

        return Asset.builder()
            .symbol(getString(response, "Symbol"))
            .name(getString(response, "Name"))
            .country(mapCountry(getString(response, "Country")))
            .currency(getString(response, "Currency"))
            .assetType(mapAssetType(getString(response, "AssetType")))
            .marketCap(getLong(response, "MarketCapitalization"))
            .build();
    }

    private String getString(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }

    private Long getLong(JsonNode node, String field) {
        if (!node.has(field)) return null;
        try {
            return Long.parseLong(node.get(field).asText());
        } catch (Exception e) {
            return null;
        }
    }

    private String mapCountry(String country) {
        if (country == null) return "US";
        if (country.contains("United States") || country.equals("USA")) return "US";
        if (country.contains("Korea")) return "KR";
        if (country.length() == 2) return country;
        return country.substring(0, 2).toUpperCase();
    }

    private String mapAssetType(String type) {
        if (type == null) return "EQUITY";
        if (type.contains("Stock") || type.equals("Common Stock")) return "EQUITY";
        if (type.equals("ETF")) return "ETF";
        return "EQUITY";
    }
}
