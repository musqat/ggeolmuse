package com.muscat.marketdata.datasource.yf.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muscat.marketdata.domain.service.CollectionStats;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// 실제 네트워크 없이 흉내 낸 RestTemplate 응답(실패는 모두 빈 문자열, 수집 통계에 종류별로 셈)
@DisplayName("야후 차트 클라이언트")
class YahooFinanceClientTest {

  private static final String BASE = "https://yahoo.test";
  // 2024-03-04 00:00 뉴욕(EST) ~ 2024-03-05 23:59:59 뉴욕
  private static final String URL = BASE + "/v8/finance/chart/AAPL?interval=1d&events=div,splits&includeAdjustedClose=true"
    + "&period1=1709528400&period2=1709701199";
  private static final String JSON = "{\"chart\":{\"result\":[]}}";
  private static final Instant T0 = Instant.parse("2024-03-06T00:00:00Z");

  private final RestTemplate restTemplate = new RestTemplate();
  private final CollectionStats stats = mock(CollectionStats.class);
  private final YahooFinanceClient client = new YahooFinanceClient(restTemplate, stats);
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(client, "baseUrl", BASE);
    ReflectionTestUtils.setField(client, "userAgent", "test-agent");
    ReflectionTestUtils.setField(client, "clock", Clock.fixed(T0, ZoneOffset.UTC));
    server = MockRestServiceServer.bindTo(restTemplate).build();
  }

  private String fetch() {
    return client.getDailyChartRaw("AAPL", LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 5));
  }

  private void advance(Duration duration) {
    ReflectionTestUtils.setField(client, "clock", Clock.fixed(T0.plus(duration), ZoneOffset.UTC));
  }

  private static byte[] gzip(String text) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
      gz.write(text.getBytes(StandardCharsets.UTF_8));
    }
    return out.toByteArray();
  }

  @Test
  @DisplayName("뉴욕 시각 기준 기간과 배당 · 분할 이벤트를 붙여 부르고 본문을 그대로 돌려준다")
  void plainJson() {
    server.expect(once(), requestTo(URL)).andExpect(header(HttpHeaders.USER_AGENT, "test-agent"))
      .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

    assertThat(fetch()).isEqualTo(JSON);
    server.verify();
    verifyNoInteractions(stats);
  }

  @Test
  @DisplayName("gzip 으로 오면 헤더가 있든 없든 풀어서 돌려준다")
  void gzipBody() throws IOException {
    HttpHeaders gzipHeader = new HttpHeaders();
    gzipHeader.set(HttpHeaders.CONTENT_ENCODING, "gzip");
    server.expect(requestTo(URL)).andRespond(withSuccess(gzip(JSON), MediaType.APPLICATION_JSON).headers(gzipHeader));
    server.expect(requestTo(URL)).andRespond(withSuccess(gzip(JSON), MediaType.APPLICATION_OCTET_STREAM));

    assertThat(fetch()).isEqualTo(JSON);
    advance(Duration.ofMinutes(2));
    assertThat(fetch()).isEqualTo(JSON);
    server.verify();
  }

  @Test
  @DisplayName("같은 스레드가 같은 차트를 1분 안에 다시 부르면 받은 응답을 다시 쓴다")
  void sameChartWithinWindow_FetchedOnce() {
    server.expect(once(), requestTo(URL)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

    assertThat(fetch()).isEqualTo(JSON);
    advance(Duration.ofSeconds(59));
    assertThat(fetch()).isEqualTo(JSON);
    server.verify();
  }

  @Test
  @DisplayName("1분이 지나거나 기간이 다르면 다시 받는다")
  void afterWindowOrOtherPeriod_FetchedAgain() {
    String otherPeriod = BASE + "/v8/finance/chart/AAPL?interval=1d&events=div,splits&includeAdjustedClose=true"
      + "&period1=1709528400&period2=1709787599";
    server.expect(requestTo(URL)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
    server.expect(requestTo(URL)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
    server.expect(requestTo(otherPeriod)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

    fetch();
    advance(Duration.ofSeconds(61));
    fetch();
    client.getDailyChartRaw("AAPL", LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 6));
    server.verify();
  }

  @Test
  @DisplayName("JSON 이 아닌 응답은 다시 쓰지 않는다")
  void nonJson_NotReused() {
    server.expect(requestTo(URL)).andRespond(withSuccess("<html>Too Many Requests</html>", MediaType.TEXT_HTML));
    server.expect(requestTo(URL)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

    fetch();
    assertThat(fetch()).isEqualTo(JSON);
    server.verify();
  }

  @Test
  @DisplayName("gzip 표시인데 풀 수 없으면 빈 문자열")
  void brokenGzip() {
    HttpHeaders gzipHeader = new HttpHeaders();
    gzipHeader.set(HttpHeaders.CONTENT_ENCODING, "gzip");
    server.expect(requestTo(URL)).andRespond(withSuccess("not gzip", MediaType.APPLICATION_JSON).headers(gzipHeader));

    assertThat(fetch()).isEmpty();
  }

  @Test
  @DisplayName("빈 본문은 빈 문자열, JSON 이 아닌 본문은 경고만 남기고 그대로 돌려준다")
  void emptyOrNonJson() {
    server.expect(requestTo(URL)).andRespond(withSuccess());
    server.expect(requestTo(URL)).andRespond(withSuccess("<html>Too Many Requests</html>", MediaType.TEXT_HTML));

    assertThat(fetch()).isEmpty();
    assertThat(fetch()).isEqualTo("<html>Too Many Requests</html>");
  }

  @Test
  @DisplayName("404 는 상장폐지 · 티커 변경으로, 그 밖의 HTTP 오류는 따로 센다")
  void httpErrors() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThat(fetch()).isEmpty();
    assertThat(fetch()).isEmpty();
    verify(stats).recordRequestNotFound();
    verify(stats).recordRequestHttpError();
  }

  @Test
  @DisplayName("연결 실패 · 타임아웃은 닿지 않음으로 센다")
  void unreachable() {
    server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

    assertThat(fetch()).isEmpty();
    verify(stats).recordRequestUnreachable();
  }
}
