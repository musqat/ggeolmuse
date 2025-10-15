package com.muscat.marketdata.provider.yf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceClient {

    @Value("${yahoo.finance.base-url:https://query1.finance.yahoo.com}")
    private String baseUrl;
    private static final String CHART_BASE_PATH = "/v8/finance/chart/";
    private static final String QUOTE_BASE_PATH = "/v7/finance/quote?symbols=";
    private static final String QUERY_PARAMS = "?interval=1d&events=div,splits&includeAdjustedClose=true";
    private static final ZoneId MARKET_TIMEZONE = ZoneId.of("America/New_York");
    @Value("${yahoo.finance.user-agent:Mozilla/5.0 (Market Data Service)}")
    private String userAgent;
    private static final byte GZIP_MAGIC_BYTE_1 = (byte) 0x1F;
    private static final byte GZIP_MAGIC_BYTE_2 = (byte) 0x8B;
    private static final int MAX_PREVIEW_LENGTH = 200;

    private final RestTemplate restTemplate;

    // ===== Public API =====
    public String getDailyChartRaw(String symbol, LocalDate fromDate, LocalDate toDate) {
        String url = buildChartUrl(symbol, fromDate, toDate);
        log.debug("Yahoo Finance 차트 API 호출: symbol={}, url={}", symbol, url);
        try {
            HttpHeaders headers = createHeaders();
            RequestEntity<Void> request = RequestEntity.get(URI.create(url)).headers(headers).build();
            ResponseEntity<byte[]> response = restTemplate.exchange(request, byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.warn("Yahoo Finance 빈 응답: symbol={}", symbol);
                return "";
            }
            String content = extractContent(response, body);
            validateJsonResponse(content);
            return content;
        } catch (IOException e) {
            log.error("응답 본문 디코딩 실패: symbol={}", symbol, e);
            return "";
        } catch (Exception e) {
            log.error("Yahoo Finance 차트 API 호출 실패: symbol={}", symbol, e);
            return "";
        }
    }

    public String getQuoteRaw(String symbol) {
        String url = baseUrl + QUOTE_BASE_PATH + symbol.toUpperCase();
        log.debug("Yahoo Finance 쿼트 API 호출: symbol={}, url={}", symbol, url);
        try {
            HttpHeaders headers = createHeaders();
            RequestEntity<Void> request = RequestEntity.get(URI.create(url)).headers(headers).build();
            ResponseEntity<byte[]> response = restTemplate.exchange(request, byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.warn("Yahoo Finance 쿼트 빈 응답: symbol={}", symbol);
                return "";
            }
            String content = extractContent(response, body);
            validateJsonResponse(content);
            return content;
        } catch (IOException e) {
            log.error("쿼트 본문 디코딩 실패: symbol={}", symbol, e);
            return "";
        } catch (Exception e) {
            log.error("Yahoo Finance 쿼트 API 호출 실패: symbol={}", symbol, e);
            return "";
        }
    }

    public String getMostActiveStocksRaw() {
        return scrapeMostActiveStocks();
    }

    /** Most Active 종목의 기초정보(심볼, 이름, 시총) 반환 */
    public List<StockInfo> getMostActiveStockInfos() {
        try {
            // Yahoo Finance Screener API 직접 호출
            String url = "https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved?scrIds=most_actives&count=25";

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, userAgent);
            headers.set(HttpHeaders.ACCEPT, "application/json");

            RequestEntity<Void> request = RequestEntity.get(URI.create(url)).headers(headers).build();
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);

            String jsonResponse = response.getBody();
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                log.warn("Screener API 빈 응답");
                return List.of();
            }

            log.debug("Screener API 응답 수신: {} bytes", jsonResponse.length());

            // JSON에서 quotes 추출
            List<StockInfo> stockInfos = parseMostActiveJson(jsonResponse);

            log.info("Most Active 종목 {} 개 수집 완료", stockInfos.size());
            return stockInfos;

        } catch (Exception e) {
            log.error("Screener API 호출 실패", e);
            return List.of();
        }
    }

    private List<StockInfo> parseMostActiveJson(String json) {
        List<StockInfo> stocks = new ArrayList<>();
        try {
            // "quotes":[{...}] 형식의 JSON 파싱
            int quotesStart = json.indexOf("\"quotes\":[");
            if (quotesStart == -1) {
                log.warn("JSON에서 quotes 배열을 찾을 수 없음");
                return stocks;
            }

            // 각 quote 객체에서 symbol, longName, marketCap 추출
            String quotesSection = json.substring(quotesStart);
            String[] quoteObjects = quotesSection.split("\\{\"symbol\":");

            for (int i = 1; i < quoteObjects.length && stocks.size() < 25; i++) {
                try {
                    String quoteJson = quoteObjects[i];

                    // 심볼 추출
                    String symbol = extractJsonValue(quoteJson, "symbol");
                    if (symbol == null || symbol.isEmpty()) continue;

                    // 이름 추출
                    String longName = extractJsonValue(quoteJson, "longName");
                    String shortName = extractJsonValue(quoteJson, "shortName");
                    String name = (longName != null && !longName.isEmpty()) ? longName :
                                 (shortName != null && !shortName.isEmpty()) ? shortName : symbol;

                    // 시가총액 추출
                    String marketCapStr = extractJsonNumber(quoteJson, "marketCap");
                    Long marketCap = null;
                    if (marketCapStr != null) {
                        try {
                            marketCap = Long.parseLong(marketCapStr);
                        } catch (NumberFormatException e) {
                            log.debug("시가총액 파싱 실패: symbol={}, value={}", symbol, marketCapStr);
                        }
                    }

                    stocks.add(new StockInfo(symbol, name, marketCap));
                    log.debug("종목 파싱: symbol={}, name={}, marketCap={}", symbol, name, marketCap);

                } catch (Exception e) {
                    log.debug("Quote 객체 파싱 실패", e);
                }
            }

        } catch (Exception e) {
            log.error("JSON 파싱 실패", e);
        }
        return stocks;
    }

    private String extractJsonValue(String json, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return null;

            start += pattern.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return null;

            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonNumber(String json, String fieldName) {
        try {
            String pattern = "\"" + fieldName + "\":";
            int start = json.indexOf(pattern);
            if (start == -1) return null;

            start += pattern.length();
            int end = start;

            // 숫자가 끝나는 지점 찾기 (쉼표, 중괄호, 대괄호 등)
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n') break;
                end++;
            }

            String value = json.substring(start, end).trim();
            // null이나 빈 값 제거
            if (value.equals("null") || value.isEmpty()) return null;

            return value;
        } catch (Exception e) {
            return null;
        }
    }

    public String scrapeMostActiveStocks() {
        try {
            List<StockInfo> infoList = getMostActiveStockInfos();
            if (infoList.isEmpty()) return "";
            StringBuilder json = new StringBuilder("{\"finance\":{\"result\":[{\"quotes\":[");
            for (int i = 0; i < infoList.size(); i++) {
                if (i > 0) json.append(",");
                json.append("{\"symbol\":\"").append(infoList.get(i).getSymbol()).append("\"}");
            }
            json.append("]}]}}");
            return json.toString();
        } catch (Exception e) {
            log.error("Yahoo Finance Most Active 처리 실패", e);
            return "";
        }
    }

    public Long scrapeMarketCap(String symbol) {
        try {
            String url = "https://finance.yahoo.com/quote/" + symbol.toUpperCase();
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            String[] selectors = {
                "td[data-test='MARKET_CAP-value']",
                "span[data-reactid*='MARKET_CAP']",
                "div[data-testid='market-cap'] span",
                "span:contains('Market Cap') + span",
                "td:contains('Market Cap') + td"
            };

            for (String selector : selectors) {
                Elements elements = doc.select(selector);
                for (Element element : elements) {
                    String marketCapText = element.text().trim();
                    Long marketCap = parseMarketCapText(marketCapText);
                    if (marketCap != null) return marketCap;
                }
            }

            Elements tables = doc.select("table");
            for (Element table : tables) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Elements cells = row.select("td, th");
                    for (int i = 0; i < cells.size() - 1; i++) {
                        String cellText = cells.get(i).text().toLowerCase();
                        if (cellText.contains("market cap") || cellText.contains("market capitalization")) {
                            String valueText = cells.get(i + 1).text().trim();
                            Long marketCap = parseMarketCapText(valueText);
                            if (marketCap != null) return marketCap;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("시가총액 스크래핑 실패: symbol={}", symbol, e);
            return null;
        }
    }

    // ===== Private helpers =====
    private String buildChartUrl(String symbol, LocalDate fromDate, LocalDate toDate) {
        long startEpoch = fromDate.atStartOfDay(MARKET_TIMEZONE).toEpochSecond();
        long endEpoch = toDate.plusDays(1).atStartOfDay(MARKET_TIMEZONE).minusSeconds(1).toEpochSecond();
        return baseUrl + CHART_BASE_PATH + symbol + QUERY_PARAMS +
                "&period1=" + startEpoch +
                "&period2=" + endEpoch;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        return headers;
    }

    private String extractContent(ResponseEntity<byte[]> response, byte[] responseBody) throws IOException {
        String contentEncoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (isGzipContent(contentEncoding, responseBody)) {
            return decompressGzip(responseBody);
        } else {
            return new String(responseBody, StandardCharsets.UTF_8);
        }
    }

    private boolean isGzipContent(String contentEncoding, byte[] data) {
        return (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) ||
               (data.length > 2 && data[0] == GZIP_MAGIC_BYTE_1 && data[1] == GZIP_MAGIC_BYTE_2);
    }

    private String decompressGzip(byte[] gzipData) throws IOException {
        try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(gzipData));
             InputStreamReader reader = new InputStreamReader(gzipStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private void validateJsonResponse(String content) {
        if (content.isBlank()) return;
        String trimmed = content.stripLeading();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            String preview = trimmed.length() > MAX_PREVIEW_LENGTH ?
                trimmed.substring(0, MAX_PREVIEW_LENGTH) + "..." : trimmed;
            log.warn("Yahoo Finance JSON 아님 응답: {}", preview);
        }
    }

    private String extractSymbolFromElement(Element element) {
        String dataSymbol = element.attr("data-symbol");
        if (!dataSymbol.isEmpty() && isValidSymbol(dataSymbol)) return dataSymbol;
        String href = element.attr("href");
        if (href.contains("/quote/")) {
            String symbol = href.substring(href.lastIndexOf("/") + 1).split("\\?")[0];
            if (isValidSymbol(symbol)) return symbol;
        }
        String text = element.text().trim();
        if (isValidSymbol(text)) return text;
        return null;
    }

    private boolean isValidSymbol(String symbol) {
        return symbol != null && !symbol.isEmpty()
            && symbol.matches("^[A-Z][A-Z0-9.-]{0,9}$")
            && !symbol.matches(".*\\d{4,}.*");
    }

    private String extractFieldFromJson(String json, String fieldName) {
        try {
            // 간단한 JSON 필드 추출 (정규식 사용)
            String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            // 숫자 값인 경우
            pattern = "\"" + fieldName + "\"\\s*:\\s*([0-9.]+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.debug("JSON 필드 추출 실패: field={}", fieldName, e);
        }
        return null;
    }

    private Long parseMarketCapText(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.replaceAll("[,$\\s]", "").toUpperCase();
        try {
            if (clean.endsWith("T")) {
                double v = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return (long) (v * 1_000_000_000_000L);
            } else if (clean.endsWith("B")) {
                double v = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return (long) (v * 1_000_000_000L);
            } else if (clean.endsWith("M")) {
                double v = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return (long) (v * 1_000_000L);
            } else if (clean.endsWith("K")) {
                double v = Double.parseDouble(clean.substring(0, clean.length() - 1));
                return (long) (v * 1_000L);
            } else {
                return Long.parseLong(clean);
            }
        } catch (NumberFormatException e) {
            log.debug("시가총액 파싱 실패: {}", text);
            return null;
        }
    }

    private StockInfo extractStockInfoFromRow(Elements cells) {
        String symbol = null;
        String name = null;
        Long marketCap = null;
        try {
            Element symbolCell = cells.get(0);
            Elements links = symbolCell.select("a");
            if (!links.isEmpty()) {
                String href = links.first().attr("href");
                if (href.contains("/quote/")) {
                    symbol = href.substring(href.lastIndexOf("/") + 1).split("\\?")[0];
                } else {
                    symbol = links.first().text().trim();
                }
            } else {
                symbol = symbolCell.text().trim();
            }
            if (cells.size() > 1) {
                name = cells.get(1).text().trim();
                if (name.isEmpty() || name.equals(symbol)) name = symbol;
            }
            if (cells.size() > 7) {
                String marketCapText = cells.get(7).text().trim();
                marketCap = parseMarketCapText(marketCapText);
            }
            if (marketCap == null) {
                for (int i = 2; i < Math.min(cells.size(), 12); i++) {
                    if (i == 7) continue;
                    String cellText = cells.get(i).text().trim();
                    Long parsed = parseMarketCapText(cellText);
                    if (parsed != null) { marketCap = parsed; break; }
                }
            }
            if (symbol != null && !symbol.isEmpty()) {
                return new StockInfo(symbol.toUpperCase(), name != null ? name : symbol, marketCap);
            }
        } catch (Exception ignore) {}
        return null;
    }

    // ===== DTO =====
    public static class StockInfo {
        private final String symbol;
        private final String name;
        private final Long marketCap;
        public StockInfo(String symbol, String name, Long marketCap) {
            this.symbol = symbol;
            this.name = name;
            this.marketCap = marketCap;
        }
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public Long getMarketCap() { return marketCap; }
    }
}
