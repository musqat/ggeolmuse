package com.muscat.marketdata.provider.av;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.provider.config.AlphaVantageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Alpha Vantage API 클라이언트
 * - 유료: TIME_SERIES_DAILY_ADJUSTED (배당/분할 포함)
 * - 무료: TIME_SERIES_DAILY (기본 OHLCV만)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageClient {

  private static final String BASE_URL = "https://www.alphavantage.co/query";
  private static final String FUNCTION_DAILY_ADJUSTED = "TIME_SERIES_DAILY_ADJUSTED";
  private static final String FUNCTION_DAILY = "TIME_SERIES_DAILY";
  private static final int MAX_RESPONSE_LENGTH = 240;

  private final RestClient restClient;
  private final AlphaVantageProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 유료 일봉 조회 (배당/분할 포함)
   */
  public String getDailyAdjustedRaw(String symbol, boolean fullHistory) {
    String url = buildUrl(FUNCTION_DAILY_ADJUSTED, symbol, fullHistory);
    return executeRequest(url);
  }

  /**
   * 무료 일봉 조회 (기본 OHLCV)
   */
  public String getDailyRaw(String symbol, boolean fullHistory) {
    String url = buildUrl(FUNCTION_DAILY, symbol, fullHistory);
    return executeRequest(url);
  }

  /**
   * 배당 데이터 조회 (DAILY_ADJUSTED 전체 이력 사용)
   */
  public String getDividends(String symbol) {
    return getDailyAdjustedRaw(symbol, true);
  }

  /**
   * JSON 트리 형태로 반환
   */
  public JsonNode getDailyAdjustedJson(String symbol, boolean fullHistory) {
    try {
      String raw = getDailyAdjustedRaw(symbol, fullHistory);
      return objectMapper.readTree(raw);
    } catch (Exception e) {
      throw new RuntimeException("Alpha Vantage JSON 파싱 실패: " + e.getMessage(), e);
    }
  }

  // ===== 내부 메서드 =====

  private String buildUrl(String function, String symbol, boolean fullHistory) {
    return UriComponentsBuilder.fromHttpUrl(BASE_URL)
        .queryParam("function", function)
        .queryParam("symbol", symbol)
        .queryParam("outputsize", fullHistory ? "full" : "compact")
        .queryParam("apikey", properties.apiKey())
        .queryParam("datatype", "json")
        .build(true)
        .toUriString();
  }

  private String executeRequest(String url) {
    try {
      ResponseEntity<String> response = restClient.get()
          .uri(url)
          .retrieve()
          .toEntity(String.class);

      String body = response.getBody();
      if (body == null || body.isBlank()) {
        throw new RuntimeException("Alpha Vantage 응답 본문이 비어있습니다");
      }

      validateResponse(body);
      return body;

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Alpha Vantage 요청 실패: " + e.getMessage(), e);
    }
  }

  /**
   * Alpha Vantage 에러/안내 메시지 감지
   */
  private void validateResponse(String response) {
    String lowerResponse = response.toLowerCase();

    if (lowerResponse.contains("premium endpoint")) {
      throw new RuntimeException("Alpha Vantage 프리미엄 전용 엔드포인트: " + truncateResponse(response));
    }

    if (lowerResponse.contains("\"note\"") || lowerResponse.contains("\"information\"")) {
      throw new RuntimeException("Alpha Vantage 제한/안내: " + truncateResponse(response));
    }

    if ((lowerResponse.contains("invalid") && lowerResponse.contains("api"))
        || lowerResponse.contains("invalid api key")) {
      throw new RuntimeException("Alpha Vantage API 키 오류: " + truncateResponse(response));
    }
  }

  private String truncateResponse(String response) {
    if (response == null) return "null";

    String cleaned = response.replaceAll("\\s+", " ").trim();
    return cleaned.length() > MAX_RESPONSE_LENGTH
        ? cleaned.substring(0, MAX_RESPONSE_LENGTH) + "..."
        : cleaned;
  }
}