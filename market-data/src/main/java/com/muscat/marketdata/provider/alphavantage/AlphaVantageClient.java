package com.muscat.marketdata.provider.alphavantage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.common.exceptions.AlphaVantageException;
import com.muscat.marketdata.common.logging.MarketDataLogger;
import com.muscat.marketdata.provider.config.AlphaVantageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AlphaVantageProperties properties;
    private final MarketDataLogger marketDataLogger;


    public JsonNode get(String function) {
        return get(function, Map.of());
    }

    public JsonNode get(String function, Map<String, String> params) {
        String url = buildUrl(function, params);
        long startTime = System.currentTimeMillis();
        String symbol = params.getOrDefault("symbol", params.getOrDefault("keywords", "UNKNOWN"));
        
        try {
            log.debug("AlphaVantage API 요청: function={}, url={}", function, url);
            String response = restTemplate.getForObject(url, String.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (response == null || response.trim().isEmpty()) {
                log.warn("AlphaVantage 빈 응답: function={}, symbol={}", function, symbol);
                marketDataLogger.logApiCall("ALPHAVANTAGE", function, symbol, false, responseTime, "Empty response");
                throw new AlphaVantageException("AlphaVantage에서 빈 응답을 받았습니다: " + function);
            }

            JsonNode jsonResponse = objectMapper.readTree(response);
            
            // AlphaVantage API 오류 응답 체크
            if (jsonResponse.has("Error Message")) {
                String errorMessage = jsonResponse.get("Error Message").asText();
                log.warn("AlphaVantage API 오류: function={}, error={}", function, errorMessage);
                marketDataLogger.logApiCall("ALPHAVANTAGE", function, symbol, false, responseTime, errorMessage);
                throw new AlphaVantageException("AlphaVantage API 오류: " + errorMessage);
            }
            
            if (jsonResponse.has("Note") && jsonResponse.get("Note").asText().contains("call frequency")) {
                String note = jsonResponse.get("Note").asText();
                log.warn("AlphaVantage API 호출 제한: function={}, note={}", function, note);
                marketDataLogger.logRateLimit("ALPHAVANTAGE", function, 60);
                throw new AlphaVantageException("AlphaVantage API 호출 제한");
            }

            marketDataLogger.logApiCall("ALPHAVANTAGE", function, symbol, true, responseTime, null);
            log.debug("AlphaVantage API 성공: function={}, responseTime={}ms", function, responseTime);
            return jsonResponse;
            
        } catch (HttpClientErrorException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            String errorMessage = handleHttpError(e, function, symbol, responseTime);
            throw new AlphaVantageException(errorMessage, e);
            
        } catch (AlphaVantageException e) {
            throw e;
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            String errorMessage = "AlphaVantage API 호출 실패: " + function;
            
            log.error("AlphaVantage API 호출 중 예상치 못한 오류: function={}, error={}", function, e.getMessage(), e);
            marketDataLogger.logApiCall("ALPHAVANTAGE", function, symbol, false, responseTime, e.getMessage());
            
            throw new AlphaVantageException(errorMessage, e);
        }
    }

    private String buildUrl(String function, Map<String, String> params) {
        StringBuilder url = new StringBuilder()
            .append(properties.getBaseUrl())
            .append("?function=").append(function)
            .append("&apikey=").append(properties.getApiKey());

        params.forEach((key, value) -> 
            url.append("&").append(key).append("=").append(value));

        return url.toString();
    }

    public String getApiKey() {
        return properties.getApiKey();
    }
    
    private String handleHttpError(HttpClientErrorException e, String function, String symbol, long responseTime) {
        HttpStatus status = (HttpStatus) e.getStatusCode();
        String errorMessage;
        
        switch (status) {
            case TOO_MANY_REQUESTS:
                marketDataLogger.logRateLimit("ALPHAVANTAGE", function, 60);
                log.warn("AlphaVantage API 호출 제한: function={}", function);
                throw new AlphaVantageException("AlphaVantage API 호출 제한");
                
            case UNAUTHORIZED:
                errorMessage = "AlphaVantage API 인증 실패: 유효하지 않은 API 키";
                log.error("AlphaVantage 인증 실패: function={}", function);
                break;
                
            case FORBIDDEN:
                errorMessage = "AlphaVantage API 접근 거부: 권한 없음 또는 구독 만료";
                log.error("AlphaVantage 접근 거부: function={}", function);
                break;
                
            case NOT_FOUND:
                errorMessage = String.format("요청한 데이터를 찾을 수 없습니다: %s (symbol: %s)", function, symbol);
                log.warn("AlphaVantage 데이터 없음: function={}, symbol={}", function, symbol);
                break;
                
            default:
                errorMessage = String.format("AlphaVantage API 오류 [%d]: %s", status.value(), e.getMessage());
                log.error("AlphaVantage HTTP 오류: function={}, status={}, error={}", 
                         function, status.value(), e.getMessage());
        }
        
        marketDataLogger.logApiCall("ALPHAVANTAGE", function, symbol, false, responseTime, errorMessage);
        return errorMessage;
    }
}