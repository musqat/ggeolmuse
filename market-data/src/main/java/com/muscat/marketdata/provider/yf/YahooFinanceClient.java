package com.muscat.marketdata.provider.yf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.zip.GZIPInputStream;

/**
 * Yahoo Finance API 클라이언트
 * 차트 데이터 (OHLCV + 배당 이벤트) 수집
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceClient {

  private static final String CHART_BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
  private static final ZoneId MARKET_TIMEZONE = ZoneId.of("America/New_York");
  private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
  private static final byte GZIP_MAGIC_BYTE_1 = (byte) 0x1F;
  private static final byte GZIP_MAGIC_BYTE_2 = (byte) 0x8B;
  private static final int MAX_PREVIEW_LENGTH = 200;

  private final RestTemplate restTemplate;

  /**
   * 일봉 차트 데이터 조회 (배당/분할 이벤트 포함)
   */
  public String getDailyChartRaw(String symbol, LocalDate fromDate, LocalDate toDate) {
    String url = buildChartUrl(symbol, fromDate, toDate);

    HttpHeaders headers = createHeaders();
    RequestEntity<Void> request = RequestEntity.get(URI.create(url)).headers(headers).build();

    try {
      ResponseEntity<byte[]> response = restTemplate.exchange(request, byte[].class);
      byte[] responseBody = response.getBody();

      if (responseBody == null || responseBody.length == 0) {
        throw new RuntimeException("Yahoo Finance 응답이 비어있습니다");
      }

      String content = extractContent(response, responseBody);
      validateJsonResponse(content);

      return content;

    } catch (Exception e) {
      throw new RuntimeException("Yahoo Finance 요청 실패: " + e.getMessage(), e);
    }
  }

  // ===== 내부 메서드 =====

  private String buildChartUrl(String symbol, LocalDate fromDate, LocalDate toDate) {
    long startEpoch = fromDate.atStartOfDay(MARKET_TIMEZONE).toEpochSecond();
    long endEpoch = toDate.plusDays(1).atStartOfDay(MARKET_TIMEZONE).minusSeconds(1).toEpochSecond();

    return CHART_BASE_URL + symbol +
        "?interval=1d&events=div,splits&includeAdjustedClose=true" +
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
    if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
      return true;
    }
    // 매직 바이트 확인
    return data.length > 2 &&
        data[0] == GZIP_MAGIC_BYTE_1 &&
        data[1] == GZIP_MAGIC_BYTE_2;
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
    String trimmed = content.stripLeading();

    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
      String preview = trimmed.length() > MAX_PREVIEW_LENGTH
          ? trimmed.substring(0, MAX_PREVIEW_LENGTH) + "..."
          : trimmed;
      throw new RuntimeException("Yahoo Finance JSON이 아닌 응답: " + preview);
    }
  }
}