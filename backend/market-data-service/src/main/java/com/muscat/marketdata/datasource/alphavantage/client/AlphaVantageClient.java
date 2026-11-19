package com.muscat.marketdata.datasource.alphavantage.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import com.muscat.marketdata.common.exception.AlphaVantageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AlphaVantage API 클라이언트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageClient {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${alphavantage.base-url:https://www.alphavantage.co/query}")
  private String baseUrl;

  @Value("${alphavantage.api-key}")
  private String apiKey;

  public JsonNode get(String function, Map<String, String> params) {
    URI uri = buildUri(function, params);

    try {
      String response = restTemplate.getForObject(uri, String.class);
      if (response == null || response.isEmpty()) {
        throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_ERROR);
      }

      JsonNode json = objectMapper.readTree(response);

      // 에러 체크
      if (json.has("Error Message")) {
        log.warn("Alpha Vantage 오류 응답: function={}, message={}", function, json.get("Error Message").asText());
        throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_ERROR);
      }
      if (json.has("Note") && json.get("Note").asText().contains("call frequency")) {
        throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_RATE_LIMITED);
      }

      return json;

    } catch (AlphaVantageException e) {
      throw e;
    } catch (Exception e) {
      log.error("API call failed: function={}, error={}", function, e.getMessage());
      throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_ERROR, e);
    }
  }

  /**
   * CSV 응답을 받는 API 호출 (LISTING_STATUS)
   */
  public String getCsv(String function, Map<String, String> params) {
    URI uri = buildUri(function, params);

    try {
      String response = restTemplate.getForObject(uri, String.class);
      if (response == null || response.isEmpty()) {
        throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_ERROR);
      }

      // 에러 체크 (CSV 형태가 아닌 JSON 에러 응답인 경우)
      if (response.trim().startsWith("{")) {
        JsonNode json = objectMapper.readTree(response);
        if (json.has("Error Message")) {
          log.warn("Alpha Vantage 오류 응답: function={}, message={}", function, json.get("Error Message").asText());
          throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_ERROR);
        }
        if (json.has("Note")) {
          throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_RATE_LIMITED);
        }
      }

      return response;

    } catch (AlphaVantageException e) {
      throw e;
    } catch (Exception e) {
      log.error("CSV API 호출 실패: function={}, error={}", function, e.getMessage());
      throw new AlphaVantageException(MarketDataResponse.ALPHA_VANTAGE_ERROR, e);
    }
  }

  // 값마다 예약 문자까지 인코딩한 쿼리를 ? 뒤에 둔 요청 주소
  private URI buildUri(String function, Map<String, String> params) {
    StringBuilder query = new StringBuilder("function=").append(encode(function))
      .append("&apikey=").append(encode(apiKey));

    params.forEach((key, value) -> query.append('&').append(encode(key)).append('=').append(encode(value)));

    return URI.create(baseUrl + "?" + query);
  }

  private static String encode(String value) {
    return UriUtils.encode(value, StandardCharsets.UTF_8);
  }
}
