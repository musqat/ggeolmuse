package com.muscat.marketdata.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataLogger {

  private static final Marker MARKET_DATA_EVENT = MarkerFactory.getMarker("MARKET_DATA_EVENT");
  private final ObjectMapper objectMapper;

  public void logApiCall(String provider, String endpoint, String symbol,
    boolean success, Long responseTime, String error) {
    Map<String, Object> apiLog = new HashMap<>();
    apiLog.put("eventType", "API_CALL");
    apiLog.put("provider", provider);
    apiLog.put("endpoint", endpoint);
    apiLog.put("symbol", symbol);
    apiLog.put("success", success);
    apiLog.put("responseTimeMs", responseTime);
    if (error != null) {
      apiLog.put("error", error);
    }
    apiLog.put("timestamp", LocalDateTime.now());

    logStructured("API_CALLED", apiLog);
  }

  public void logDataCollection(String provider, String symbol, String dataType,
    int recordCount, boolean success, String error) {
    Map<String, Object> collectionLog = new HashMap<>();
    collectionLog.put("eventType", "DATA_COLLECTION");
    collectionLog.put("provider", provider);
    collectionLog.put("symbol", symbol);
    collectionLog.put("dataType", dataType);
    collectionLog.put("recordCount", recordCount);
    collectionLog.put("success", success);
    if (error != null) {
      collectionLog.put("error", error);
    }
    collectionLog.put("timestamp", LocalDateTime.now());

    logStructured("DATA_COLLECTED", collectionLog);
  }

  public void logBatchOperation(String operation, int totalSymbols, int successCount,
    int failureCount, Long durationMs) {
    Map<String, Object> batchLog = new HashMap<>();
    batchLog.put("eventType", "BATCH_OPERATION");
    batchLog.put("operation", operation);
    batchLog.put("totalSymbols", totalSymbols);
    batchLog.put("successCount", successCount);
    batchLog.put("failureCount", failureCount);
    batchLog.put("durationMs", durationMs);
    batchLog.put("timestamp", LocalDateTime.now());

    logStructured("BATCH_COMPLETED", batchLog);
  }

  public void logRateLimit(String provider, String endpoint, int retryAfterSeconds) {
    Map<String, Object> rateLimitLog = new HashMap<>();
    rateLimitLog.put("eventType", "RATE_LIMIT");
    rateLimitLog.put("provider", provider);
    rateLimitLog.put("endpoint", endpoint);
    rateLimitLog.put("retryAfterSeconds", retryAfterSeconds);
    rateLimitLog.put("timestamp", LocalDateTime.now());

    logStructured("RATE_LIMITED", rateLimitLog);
  }

  public void logConfigurationLoad(String provider, boolean success, String error) {
    Map<String, Object> configLog = new HashMap<>();
    configLog.put("eventType", "CONFIGURATION_LOAD");
    configLog.put("provider", provider);
    configLog.put("success", success);
    if (error != null) {
      configLog.put("error", error);
    }
    configLog.put("timestamp", LocalDateTime.now());

    logStructured("CONFIG_LOADED", configLog);
  }

  private void logStructured(String action, Map<String, Object> data) {
    try {
      String jsonLog = objectMapper.writeValueAsString(data);
      log.info(MARKET_DATA_EVENT, "MARKET_DATA_EVENT action={} data={}", action, jsonLog);
    } catch (JsonProcessingException e) {
      log.error("구조화 로그 생성 실패: action={}", action, e);
      log.info(MARKET_DATA_EVENT, "MARKET_DATA_EVENT action={} data={}", action, data.toString());
    }
  }
}
