package com.muscat.marketdata.provider.yf;

import com.muscat.marketdata.common.exceptions.YahooFinanceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceClient {

    private static final String CHART_BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String QUERY_PARAMS = "?interval=1d&events=div,splits&includeAdjustedClose=true";
    private static final ZoneId MARKET_TIMEZONE = ZoneId.of("America/New_York");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final byte GZIP_MAGIC_BYTE_1 = (byte) 0x1F;
    private static final byte GZIP_MAGIC_BYTE_2 = (byte) 0x8B;
    private static final int MAX_PREVIEW_LENGTH = 200;

    private final RestTemplate restTemplate;

    public String getDailyChartRaw(String symbol, LocalDate fromDate, LocalDate toDate) {
        String url = buildChartUrl(symbol, fromDate, toDate);
        log.debug("Yahoo Finance API 호출: symbol={}, url={}", symbol, url);

        try {
            HttpHeaders headers = createHeaders();
            RequestEntity<Void> request = RequestEntity.get(URI.create(url)).headers(headers).build();

            ResponseEntity<byte[]> response = restTemplate.exchange(request, byte[].class);
            byte[] responseBody = response.getBody();

            if (responseBody == null || responseBody.length == 0) {
                log.warn("Yahoo Finance 빈 응답: symbol={}", symbol);
                return "";
            }

            String content = extractContent(response, responseBody);
            validateJsonResponse(content);
            return content;

        } catch (IOException e) {
            log.error("Response content 추출 실패: symbol={}", symbol, e);
            return "";
        } catch (Exception e) {
            log.error("Yahoo Finance API 호출 실패: symbol={}", symbol, e);
            return "";
        }
    }

    private String buildChartUrl(String symbol, LocalDate fromDate, LocalDate toDate) {
        long startEpoch = fromDate.atStartOfDay(MARKET_TIMEZONE).toEpochSecond();
        long endEpoch = toDate.plusDays(1).atStartOfDay(MARKET_TIMEZONE).minusSeconds(1).toEpochSecond();

        return CHART_BASE_URL + symbol + QUERY_PARAMS +
                "&period1=" + startEpoch +
                "&period2=" + endEpoch;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
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
            log.warn("Yahoo Finance 비JSON 응답: {}", preview);
        }
    }
}