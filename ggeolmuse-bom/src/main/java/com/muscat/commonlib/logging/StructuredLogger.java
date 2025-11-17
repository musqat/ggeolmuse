package com.muscat.commonlib.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StructuredLogger {

  private final ObjectMapper objectMapper;

  public void logStructured(Marker marker, String action, Map<String, Object> data) {
    // 공통 정보 추가
    Map<String, Object> enrichedData = new HashMap<>(data);
    enrichedData.putIfAbsent("timestamp", LocalDateTime.now());
    enrichedData.putIfAbsent("action", action);

    try {
      String jsonLog = objectMapper.writeValueAsString(enrichedData);
      log.info(marker, "STRUCTURED_EVENT action={} data={}", action, jsonLog);
    } catch (JsonProcessingException e) {
      log.error("구조화 로그 생성 실패: action={}", action, e);
      log.info(marker, "STRUCTURED_EVENT action={} data={}", action, enrichedData.toString());
    }
  }

  public void logStructured(String action, Map<String, Object> data) {
    logStructured(null, action, data);
  }

  public void logApiCall(String method, String uri, String userId, int statusCode, long duration) {
    Map<String, Object> apiLog = new HashMap<>();
    apiLog.put("eventType", "API_CALL");
    apiLog.put("method", method);
    apiLog.put("uri", uri);
    apiLog.put("userId", userId);
    apiLog.put("statusCode", statusCode);
    apiLog.put("duration", duration);

    logStructured("API_CALLED", apiLog);
  }

  public void logError(String errorType, String message, String userId,
    Map<String, Object> context) {
    Map<String, Object> errorLog = new HashMap<>();
    errorLog.put("eventType", "ERROR");
    errorLog.put("errorType", errorType);
    errorLog.put("message", message);
    errorLog.put("userId", userId);
    if (context != null) {
      errorLog.putAll(context);
    }

    logStructured("ERROR_OCCURRED", errorLog);
  }
}
