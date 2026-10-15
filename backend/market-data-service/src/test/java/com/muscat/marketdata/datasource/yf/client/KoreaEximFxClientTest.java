package com.muscat.marketdata.datasource.yf.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// 수출입은행 환율 API. 응답은 통화별 배열이고, USD 의 기준환율(쉼표 포함 문자열)만 쓴다
@DisplayName("수출입은행 환율 클라이언트")
class KoreaEximFxClientTest {

  private static final String URL = "https://exim.test/exchangeJSON?searchdate=20240304&data=AP01&authkey=test-key";

  private final RestTemplate restTemplate = new RestTemplate();
  private final KoreaEximFxClient client = new KoreaEximFxClient(restTemplate, new ObjectMapper());
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(client, "baseUrl", "https://exim.test/exchangeJSON");
    ReflectionTestUtils.setField(client, "authKey", "test-key");
    server = MockRestServiceServer.bindTo(restTemplate).build();
  }

  private void respond(String body) {
    server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName("날짜 · 자료 구분 · 인증키를 붙여 부르고, 여러 통화 중 USD 기준환율을 쉼표를 떼어 숫자로 준다")
  void usdRate() {
    respond("""
      [
        {"cur_unit":"JPY(100)","deal_bas_r":"901.2","cur_nm":"일본 옌"},
        {"cur_unit":"USD","deal_bas_r":"1,331.50","cur_nm":"미국 달러"}
      ]
      """);

    assertThat(client.fetchFx(LocalDate.of(2024, 3, 4))).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("1331.50"));
    server.verify();
  }

  @Test
  @DisplayName("USD 기준환율이 숫자가 아니면 그 항목을 건너뛴다")
  void skipsUnparsableUsd() {
    respond("""
      [ {"cur_unit":"USD","deal_bas_r":"-"}, {"cur_unit":"USD","deal_bas_r":null} ]
      """);

    assertThat(client.fetchFx(LocalDate.of(2024, 3, 4))).isEmpty();
  }

  @Test
  @DisplayName("USD 기준환율이 정상 범위(500 ~ 2000원) 밖이면 버린다")
  void dropsOutOfRange() {
    respond("""
      [ {"cur_unit":"USD","deal_bas_r":"13.315"} ]
      """);

    assertThat(client.fetchFx(LocalDate.of(2024, 3, 4))).isEmpty();
  }

  @Test
  @DisplayName("휴일처럼 빈 응답이거나 배열이 아니거나 서버 오류면 비운다")
  void emptyResults() {
    server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
    respond("{\"result\":4}");
    server.expect(requestTo(URL)).andRespond(withServerError());

    for (int i = 0; i < 3; i++) {
      assertThat(client.fetchFxRate(LocalDate.of(2024, 3, 4))).isEmpty();
    }
  }
}
