package com.muscat.marketdata.datasource.yf.client;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceClient {

  @Value("${yahoo.finance.base-url:https://query1.finance.yahoo.com}")
  private String baseUrl;
  @Value("${yahoo.finance.user-agent:Mozilla/5.0 (Market Data Service)}")
  private String userAgent;
  private static final String CHART_BASE_PATH = "/v8/finance/chart/";
  private static final String QUERY_PARAMS = "?interval=1d&events=div,splits&includeAdjustedClose=true";
  private static final ZoneId MARKET_TIMEZONE = ZoneId.of("America/New_York");
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

  // ===== 내부 메서드 =====
  private String buildChartUrl(String symbol, LocalDate fromDate, LocalDate toDate) {
    long startEpoch = fromDate.atStartOfDay(MARKET_TIMEZONE).toEpochSecond();
    long endEpoch = toDate.plusDays(1).atStartOfDay(MARKET_TIMEZONE).minusSeconds(1)
      .toEpochSecond();
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

  private String extractContent(ResponseEntity<byte[]> response, byte[] responseBody)
    throws IOException {
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
    if (content.isBlank()) {
      return;
    }
    String trimmed = content.stripLeading();
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
      String preview = trimmed.length() > MAX_PREVIEW_LENGTH ?
        trimmed.substring(0, MAX_PREVIEW_LENGTH) + "..." : trimmed;
      log.warn("Yahoo Finance JSON 아님 응답: {}", preview);
    }
  }
}
