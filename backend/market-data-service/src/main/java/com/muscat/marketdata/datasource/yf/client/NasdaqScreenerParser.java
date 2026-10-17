package com.muscat.marketdata.datasource.yf.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.domain.entity.Asset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.List;

// NASDAQ Screener 응답을 Asset 목록으로 바꿈
@Slf4j
@Component
public class NasdaqScreenerParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * NASDAQ API 응답을 Asset 리스트로 파싱
     */
    public List<Asset> parseStocks(String json) {
        List<Asset> assets = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode data = root.path("data");
            JsonNode table = data.path("table");
            JsonNode rows = table.path("rows");

            if (!rows.isArray()) {
                log.warn("NASDAQ API 응답에 rows 배열이 없음");
                return assets;
            }

            log.info("NASDAQ API 응답 파싱 시작: 총 {}개 종목", rows.size());

            for (JsonNode row : rows) {
                try {
                    Asset asset = parseRow(row);
                    if (asset != null) {
                        assets.add(asset);
                    }
                } catch (Exception e) {
                    log.warn("종목 파싱 실패: row={}, error={}", row, e.getMessage());
                }
            }

            log.info("NASDAQ API 응답 파싱 완료: 성공 {}개", assets.size());

        } catch (Exception e) {
            log.error("NASDAQ API 응답 파싱 실패", e);
        }

        return assets;
    }

    private Asset parseRow(JsonNode row) {
        // Symbol (필수)
        String symbol = getTextValue(row, "symbol");
        if (symbol == null || symbol.isEmpty()) {
            log.debug("심볼이 없는 행 스킵");
            return null;
        }

        // Name (필수)
        String name = getTextValue(row, "name");
        if (name == null || name.isEmpty()) {
            name = symbol; // fallback
        }

        // Market Cap (선택)
        Long marketCap = parseMarketCap(row);

        // Country (선택) - ISO 2-letter code
        String country = getTextValue(row, "country");
        if (country == null || country.isEmpty() || "United States".equals(country)) {
            country = "US"; // ISO country code
        }

        // Sector (선택, 로그용)
        String sector = getTextValue(row, "sector");

        // Industry (선택, ETF 판별용)
        String industry = getTextValue(row, "industry");

        // Asset Type 판별 (EQUITY vs ETF)
        String assetType = determineAssetType(name, industry);

        // Asset 생성
        Asset asset = Asset.builder()
                .symbol(symbol)
                .name(name)
                .country(country)
                .currency("USD")
                .assetType(assetType)
                .marketCap(marketCap)
                .build();

        log.debug("종목 파싱 완료: symbol={}, name={}, assetType={}, marketCap={}, sector={}",
                 symbol, name, assetType, marketCap, sector);

        return asset;
    }

    private static final Pattern ETF_WORD = Pattern.compile("\\betf\\b", Pattern.CASE_INSENSITIVE);

    // 이름에 ETF 가 있으면 ETF. trust · fund 는 산업 칸이 빌 때만 ETF(은행 · 리츠 제외)
    public static String determineAssetType(String name, String industry) {
        if (name == null) {
            return "EQUITY";
        }
        String nameLower = name.toLowerCase();
        if (ETF_WORD.matcher(name).find() || nameLower.contains("exchange traded fund")) {
            return "ETF";
        }
        boolean trustOrFund = nameLower.contains(" trust") || nameLower.contains(" fund");
        if (trustOrFund && (industry == null || industry.isBlank())) {
            return "ETF";
        }
        return "EQUITY";
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        String value = fieldNode.asText();
        return (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) ? null : value;
    }

    private Long parseMarketCap(JsonNode row) {
        String marketCapStr = getTextValue(row, "marketCap");
        if (marketCapStr == null || marketCapStr.isEmpty()) {
            return null;
        }

        try {
            // "2,170,292,977,079" 또는 "3899609280300.00" 형식 파싱
            // 콤마/공백/$ 제거 후 소수점 앞부분만 Long 변환
            String cleaned = marketCapStr
                .replace(",", "")
                .replace("$", "")
                .trim()
                .split("\\.")[0];
            if (cleaned.isEmpty()) {
                return null;
            }
            long value = Long.parseLong(cleaned);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            log.debug("시가총액 파싱 실패: marketCap={}", marketCapStr);
            return null;
        }
    }
}
