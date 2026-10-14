package com.muscat.marketdata.datasource.yf.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@DisplayName("NASDAQ 스크리너 클라이언트")
class NasdaqScreenerClientTest {

  private static final String BASE = "https://api.nasdaq.com/api/screener/stocks?tableonly=true";

  private final RestTemplate restTemplate = new RestTemplate();
  private final NasdaqScreenerClient client = new NasdaqScreenerClient(restTemplate);
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    server = MockRestServiceServer.bindTo(restTemplate).build();
  }

  @Test
  @DisplayName("거래소는 대문자로, 시총 필터의 쉼표 · 파이프는 %7C 로 바꿔 붙이고 본문을 그대로 돌려준다")
  void buildsUrl() {
    server.expect(requestTo(BASE + "&limit=100&offset=200&exchange=NASDAQ&marketcap=mega%7Clarge%7Cmid"))
      .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
      .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

    assertThat(client.getStocks("nasdaq", "Mega,Large|mid", 100, 200)).isEqualTo("{\"data\":{}}");
    server.verify();
  }

  @Test
  @DisplayName("전체 조회는 시총 필터 없이 한 번에 1만 건을 받는다")
  void allStocksWithoutFilter() {
    server.expect(requestTo(BASE + "&limit=10000&offset=0&exchange=NYSE"))
      .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

    assertThat(client.getAllStocks("NYSE", "mega,large")).isEqualTo("{\"data\":{}}");
    server.verify();
  }

  @Test
  @DisplayName("거래소를 비우면 거래소 조건을 빼고, 빈 응답이나 오류면 빈 객체 {} 를 돌려준다")
  void emptyAndError() {
    server.expect(requestTo(BASE + "&limit=10&offset=0")).andRespond(withSuccess());
    server.expect(requestTo(BASE + "&limit=10&offset=0")).andRespond(withServerError());

    assertThat(client.getStocks("", null, 10, 0)).isEqualTo("{}");
    assertThat(client.getStocks(null, "", 10, 0)).isEqualTo("{}");
    server.verify();
  }
}
