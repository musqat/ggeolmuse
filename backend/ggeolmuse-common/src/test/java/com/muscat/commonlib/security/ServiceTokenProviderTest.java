package com.muscat.commonlib.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.commonlib.exception.CommonResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@DisplayName("서비스 토큰 받기")
class ServiceTokenProviderTest {

  private static final String TOKEN_URL = "http://keycloak:8080/realms/r/protocol/openid-connect/token";

  private final MovableClock clock = new MovableClock(Instant.parse("2026-11-13T00:00:00Z"));
  private MockRestServiceServer keycloak;
  private ServiceTokenProvider provider;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    keycloak = MockRestServiceServer.bindTo(builder).build();
    provider = new ServiceTokenProvider(builder, TOKEN_URL, "svc", "s3cret", clock);
  }

  private static String tokenBody(String token, int expiresIn) {
    return "{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + ",\"token_type\":\"Bearer\"}";
  }

  @Test
  @DisplayName("client_credentials 로 받은 토큰을 만료 30초 전까지 다시 쓴다")
  void reusesTokenUntilNearExpiry() {
    keycloak.expect(once(), requestTo(TOKEN_URL))
      .andExpect(method(HttpMethod.POST))
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
      .andExpect(content().formDataContains(Map.of(
        "grant_type", "client_credentials", "client_id", "svc", "client_secret", "s3cret")))
      .andRespond(withSuccess(tokenBody("t1", 300), MediaType.APPLICATION_JSON));

    assertThat(provider.token()).isEqualTo("t1");
    clock.advance(Duration.ofSeconds(269));
    assertThat(provider.token()).isEqualTo("t1");
    keycloak.verify();
  }

  @Test
  @DisplayName("만료 30초 전부터는 새 토큰을 받는다")
  void refreshesNearExpiry() {
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(tokenBody("t1", 300), MediaType.APPLICATION_JSON));
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(tokenBody("t2", 300), MediaType.APPLICATION_JSON));

    assertThat(provider.token()).isEqualTo("t1");
    clock.advance(Duration.ofSeconds(270));
    assertThat(provider.token()).isEqualTo("t2");
    keycloak.verify();
  }

  @Test
  @DisplayName("Keycloak 이 거절하거나 응답에 토큰이 없으면 예외이고 5초 뒤 호출에서 다시 받는다")
  void failureThrowsAndRetriesAfterWait() {
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(tokenBody("t1", 300), MediaType.APPLICATION_JSON));

    assertThatThrownBy(provider::token).isInstanceOf(RestClientException.class);
    clock.advance(Duration.ofSeconds(5));
    assertThatThrownBy(provider::token).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(CommonResponse.SERVICE_TOKEN_MISSING);
    clock.advance(Duration.ofSeconds(5));
    assertThat(provider.token()).isEqualTo("t1");
    keycloak.verify();
  }

  @Test
  @DisplayName("받기에 실패하면 5초 동안은 Keycloak 에 다시 요청하지 않는다")
  void failureWaitsBeforeRequesting() {
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(tokenBody("t1", 300), MediaType.APPLICATION_JSON));

    assertThatThrownBy(provider::token).isInstanceOf(RestClientException.class);
    clock.advance(Duration.ofSeconds(4));
    assertThatThrownBy(provider::token).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(CommonResponse.SERVICE_TOKEN_RETRY_WAIT);
    clock.advance(Duration.ofSeconds(1));
    assertThat(provider.token()).isEqualTo("t1");
    keycloak.verify();
  }

  @Test
  @DisplayName("Keycloak 이 느리게 실패해도 5초 기다림은 실패한 시각부터 센다")
  void slowFailureWaitsFromFailure() {
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(request -> {
      clock.advance(Duration.ofSeconds(10));
      return withUnauthorizedRequest().createResponse(request);
    });

    assertThatThrownBy(provider::token).isInstanceOf(RestClientException.class);
    clock.advance(Duration.ofSeconds(4));
    assertThatThrownBy(provider::token).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(CommonResponse.SERVICE_TOKEN_RETRY_WAIT);
    keycloak.verify();
  }

  @Test
  @DisplayName("새로 받기에 실패해도 받아 둔 토큰이 만료 전이면 받아 둔 토큰을 쓴다")
  void refreshFailureKeepsUnexpiredToken() {
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(tokenBody("t1", 300), MediaType.APPLICATION_JSON));
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());
    keycloak.expect(once(), requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());

    assertThat(provider.token()).isEqualTo("t1");
    clock.advance(Duration.ofSeconds(280));
    assertThat(provider.token()).isEqualTo("t1");
    clock.advance(Duration.ofSeconds(2));
    assertThat(provider.token()).isEqualTo("t1");
    clock.advance(Duration.ofSeconds(18));
    assertThatThrownBy(provider::token).isInstanceOf(RestClientException.class);
    keycloak.verify();
  }

  // 테스트가 시각을 옮기는 시계
  private static final class MovableClock extends Clock {

    private Instant now;

    private MovableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
