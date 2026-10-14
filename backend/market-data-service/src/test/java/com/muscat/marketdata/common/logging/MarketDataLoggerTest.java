package com.muscat.marketdata.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 구조화 로그로 남기는 키 이름을 고정한다. 로그 검색이 이 키로 걸러진다
@DisplayName("시세 구조화 로그")
class MarketDataLoggerTest {

  private final ObjectMapper objectMapper = mock(ObjectMapper.class);
  private final MarketDataLogger logger = new MarketDataLogger(objectMapper);

  @SuppressWarnings("unchecked")
  private Map<String, Object> logged() throws JsonProcessingException {
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(objectMapper).writeValueAsString(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("외부 API 호출은 공급자 · 경로 · 종목 · 성공 여부 · 응답 시간을 남기고, 오류는 실패 때만 싣는다")
  void apiCall() throws Exception {
    logger.logApiCall("yahoo", "/v8/chart", "AAPL", false, 120L, "429");

    assertThat(logged()).containsEntry("eventType", "API_CALL").containsEntry("provider", "yahoo")
      .containsEntry("endpoint", "/v8/chart").containsEntry("symbol", "AAPL").containsEntry("success", false)
      .containsEntry("responseTimeMs", 120L).containsEntry("error", "429").containsKey("timestamp");
  }

  @Test
  @DisplayName("오류가 없으면 오류 키를 넣지 않는다")
  void apiCallWithoutError() throws Exception {
    logger.logApiCall("yahoo", "/v8/chart", "AAPL", true, 80L, null);

    assertThat(logged()).doesNotContainKey("error");
  }

  @Test
  @DisplayName("데이터 수집 · 일괄 작업 · 호출 한도 · 설정 적재 로그의 키")
  void otherEvents() throws Exception {
    ObjectMapper mapper = mock(ObjectMapper.class);
    MarketDataLogger other = new MarketDataLogger(mapper);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

    other.logDataCollection("yahoo", "AAPL", "CANDLE", 250, false, "timeout");
    other.logBatchOperation("candle-refresh", 100, 98, 2, 5000L);
    other.logRateLimit("alphavantage", "OVERVIEW", 60);
    other.logConfigurationLoad("yahoo", false, "missing key");
    other.logDataCollection("yahoo", "AAPL", "CANDLE", 250, true, null);
    other.logConfigurationLoad("yahoo", true, null);

    verify(mapper, org.mockito.Mockito.times(6)).writeValueAsString(captor.capture());
    assertThat(captor.getAllValues()).extracting(map -> map.get("eventType"))
      .containsExactly("DATA_COLLECTION", "BATCH_OPERATION", "RATE_LIMIT", "CONFIGURATION_LOAD", "DATA_COLLECTION", "CONFIGURATION_LOAD");
    assertThat(captor.getAllValues().get(0)).containsEntry("recordCount", 250).containsEntry("error", "timeout");
    assertThat(captor.getAllValues().get(1)).containsEntry("successCount", 98).containsEntry("failureCount", 2);
    assertThat(captor.getAllValues().get(2)).containsEntry("retryAfterSeconds", 60);
    assertThat(captor.getAllValues().get(3)).containsEntry("error", "missing key");
    assertThat(captor.getAllValues().get(4)).doesNotContainKey("error");
    assertThat(captor.getAllValues().get(5)).doesNotContainKey("error");
  }

  @Test
  @DisplayName("JSON 으로 못 바꿔도 예외 없이 문자열로 남긴다")
  void serializationFailure() throws Exception {
    given(objectMapper.writeValueAsString(any())).willThrow(new JsonProcessingException("boom") {});

    assertThatCode(() -> logger.logRateLimit("alphavantage", "OVERVIEW", 60)).doesNotThrowAnyException();
  }
}
