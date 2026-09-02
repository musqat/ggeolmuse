package com.muscat.marketdata.datasource.alphavantage.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.common.exceptions.AlphaVantageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
        String url = buildUrl(function, params);

        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isEmpty()) {
                throw new AlphaVantageException("Empty response");
            }

            JsonNode json = objectMapper.readTree(response);

            // 에러 체크
            if (json.has("Error Message")) {
                throw new AlphaVantageException(json.get("Error Message").asText());
            }
            if (json.has("Note") && json.get("Note").asText().contains("call frequency")) {
                throw new AlphaVantageException("API rate limit exceeded");
            }

            return json;

        } catch (AlphaVantageException e) {
            throw e;
        } catch (Exception e) {
            log.error("API call failed: function={}, error={}", function, e.getMessage());
            throw new AlphaVantageException("API call failed: " + function, e);
        }
    }

    /**
     * CSV 응답을 받는 API 호출 (LISTING_STATUS)
     */
    public String getCsv(String function, Map<String, String> params) {
        String url = buildUrl(function, params);

        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isEmpty()) {
                throw new AlphaVantageException("Empty CSV response");
            }

            // 에러 체크 (CSV 형태가 아닌 JSON 에러 응답인 경우)
            if (response.trim().startsWith("{")) {
                JsonNode json = objectMapper.readTree(response);
                if (json.has("Error Message")) {
                    throw new AlphaVantageException(json.get("Error Message").asText());
                }
                if (json.has("Note")) {
                    throw new AlphaVantageException("API rate limit exceeded");
                }
            }

            return response;

        } catch (AlphaVantageException e) {
            throw e;
        } catch (Exception e) {
            log.error("CSV API 호출 실패: function={}, error={}", function, e.getMessage());
            throw new AlphaVantageException("CSV API 호출 실패: " + function, e);
        }
    }

    private String buildUrl(String function, Map<String, String> params) {
        StringBuilder url = new StringBuilder()
            .append(baseUrl)
            .append("?function=").append(function)
            .append("&apikey=").append(apiKey);

        params.forEach((key, value) ->
            url.append("&").append(key).append("=").append(value));

        return url.toString();
    }
}
