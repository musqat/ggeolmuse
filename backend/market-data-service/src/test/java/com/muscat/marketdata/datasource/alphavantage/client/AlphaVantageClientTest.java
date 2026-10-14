package com.muscat.marketdata.datasource.alphavantage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.common.exceptions.AlphaVantageException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// 종목 목록(LISTING_STATUS)이 이 클라이언트를 쓴다. 오류 · 한도 초과는 200 본문으로 와서 본문을 보고 예외로 바꾼다
@DisplayName("AlphaVantage 클라이언트 · 호출 간격")
class AlphaVantageClientTest {

  private static final String BASE = "https://av.test/query";

  private final RestTemplate restTemplate = new RestTemplate();
  private final AlphaVantageClient client = new AlphaVantageClient(restTemplate, new ObjectMapper());
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(client, "baseUrl", BASE);
    ReflectionTestUtils.setField(client, "apiKey", "test-key");
    server = MockRestServiceServer.bindTo(restTemplate).build();
  }

  private static Map<String, String> params() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("symbol", "AAPL");
    params.put("outputsize", "compact");
    return params;
  }

  private void respond(String url, String body) {
    server.expect(requestTo(url)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }

  @Nested
  @DisplayName("JSON")
  class JsonTests {

    private static final String URL = BASE + "?function=TIME_SERIES_DAILY&apikey=test-key&symbol=AAPL&outputsize=compact";

    @Test
    @DisplayName("함수 · 키 · 파라미터를 붙여 부르고 JSON 으로 돌려준다")
    void returnsJson() {
      respond(URL, "{\"Meta Data\":{\"2. Symbol\":\"AAPL\"}}");

      assertThat(client.get("TIME_SERIES_DAILY", params()).path("Meta Data").path("2. Symbol").asText()).isEqualTo("AAPL");
      server.verify();
    }

    @Test
    @DisplayName("오류 메시지 · 호출 한도 안내 · 빈 본문은 각각 이유를 담은 예외")
    void errorBodies() {
      respond(URL, "{\"Error Message\":\"Invalid API call\"}");
      respond(URL, "{\"Note\":\"Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute\"}");
      server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

      assertThatThrownBy(() -> client.get("TIME_SERIES_DAILY", params())).isInstanceOf(AlphaVantageException.class)
        .hasMessage("Invalid API call");
      assertThatThrownBy(() -> client.get("TIME_SERIES_DAILY", params())).isInstanceOf(AlphaVantageException.class)
        .hasMessage("API rate limit exceeded");
      assertThatThrownBy(() -> client.get("TIME_SERIES_DAILY", params())).isInstanceOf(AlphaVantageException.class)
        .hasMessage("Empty response");
    }

    @Test
    @DisplayName("다른 안내 문구는 그대로 돌려주고, 서버 오류나 깨진 JSON 은 함수 이름을 담은 예외")
    void otherNoteAndFailures() {
      respond(URL, "{\"Note\":\"maintenance tonight\"}");
      server.expect(requestTo(URL)).andRespond(withServerError());
      respond(URL, "{broken");

      assertThat(client.get("TIME_SERIES_DAILY", params()).has("Note")).isTrue();
      assertThatThrownBy(() -> client.get("TIME_SERIES_DAILY", params())).hasMessage("API call failed: TIME_SERIES_DAILY");
      assertThatThrownBy(() -> client.get("TIME_SERIES_DAILY", params())).hasMessage("API call failed: TIME_SERIES_DAILY");
    }
  }

  @Nested
  @DisplayName("CSV")
  class CsvTests {

    private static final String URL = BASE + "?function=LISTING_STATUS&apikey=test-key";

    @Test
    @DisplayName("CSV 본문은 그대로 돌려준다")
    void returnsCsv() {
      server.expect(requestTo(URL)).andRespond(withSuccess("symbol,name\nAAPL,Apple", MediaType.TEXT_PLAIN));

      assertThat(client.getCsv("LISTING_STATUS", Map.of())).isEqualTo("symbol,name\nAAPL,Apple");
    }

    @Test
    @DisplayName("JSON 오류 · 안내 본문이나 빈 본문은 예외, JSON 이어도 오류가 없으면 그대로 돌려준다")
    void errorBodies() {
      respond(URL, "{\"Error Message\":\"Invalid API call\"}");
      respond(URL, "{\"Note\":\"call frequency\"}");
      server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));
      respond(URL, "{\"Information\":\"premium\"}");

      assertThatThrownBy(() -> client.getCsv("LISTING_STATUS", Map.of())).hasMessage("Invalid API call");
      assertThatThrownBy(() -> client.getCsv("LISTING_STATUS", Map.of())).hasMessage("API rate limit exceeded");
      assertThatThrownBy(() -> client.getCsv("LISTING_STATUS", Map.of())).hasMessage("Empty CSV response");
      assertThat(client.getCsv("LISTING_STATUS", Map.of())).isEqualTo("{\"Information\":\"premium\"}");
    }

    @Test
    @DisplayName("서버 오류는 함수 이름을 담은 예외")
    void serverError() {
      server.expect(requestTo(URL)).andRespond(withServerError());

      assertThatThrownBy(() -> client.getCsv("LISTING_STATUS", Map.of())).hasMessage("CSV API 호출 실패: LISTING_STATUS");
    }
  }

  @Nested
  @DisplayName("호출 간격")
  class RateLimiterTests {

    @Test
    @DisplayName("분당 호출 수에서 나온 간격보다 빨리 부르면 남은 시간만큼 기다린다")
    void waitsBetweenCalls() {
      AlphaVantageRateLimiter limiter = new AlphaVantageRateLimiter(300);

      long start = System.nanoTime();
      limiter.waitIfNeeded();
      long firstMillis = (System.nanoTime() - start) / 1_000_000;
      limiter.waitIfNeeded();
      long bothMillis = (System.nanoTime() - start) / 1_000_000;

      // 분당 300 이면 200ms 간격. 첫 호출은 기다리지 않는다
      assertThat(firstMillis).isLessThan(150);
      assertThat(bothMillis).isGreaterThanOrEqualTo(150);
    }
  }
}
