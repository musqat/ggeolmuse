package com.muscat.commonlib.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.security.ServiceTokenProvider;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.RetryableException;
import feign.Retryer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("서비스 공통 Feign 설정")
class FeignConfigTest {

  private final ServiceTokenProvider serviceTokens = mock(ServiceTokenProvider.class);
  private final RequestInterceptor interceptor = new FeignConfig().jwtTokenInterceptor(serviceTokens);

  private static RetryableException connectFailure() {
    Request request = Request.create(Request.HttpMethod.GET, "http://market-data-service:8083/api/internal/market/fx/latest",
      Map.of(), null, StandardCharsets.UTF_8, null);
    return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request);
  }

  private static void signedInAs(String token) {
    Jwt jwt = Jwt.withTokenValue(token).header("alg", "none").subject("user-1").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private RequestTemplate send(String path) {
    RequestTemplate template = new RequestTemplate().uri(path);
    interceptor.apply(template);
    return template;
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("연결이 실패해도 Feign 은 다시 보내지 않는다")
  void retryer_PropagatesFirstFailure() {
    Retryer retryer = new FeignConfig().retryer();
    RetryableException failure = connectFailure();

    assertThatThrownBy(() -> retryer.continueOrPropagate(failure)).isSameAs(failure);
  }

  @Test
  @DisplayName("내부 경로 호출에는 사용자 토큰 대신 서비스 토큰을 싣는다")
  void internalPath_ServiceToken() {
    signedInAs("user-token");
    given(serviceTokens.token()).willReturn("service-token");

    RequestTemplate template = send("/api/internal/market/fx/latest");

    assertThat(template.headers().get("Authorization")).containsExactly("Bearer service-token");
  }

  @Test
  @DisplayName("사용자 토큰이 없어도 내부 경로 호출에는 서비스 토큰을 싣는다")
  void internalPathWithoutUser_ServiceToken() {
    given(serviceTokens.token()).willReturn("service-token");

    RequestTemplate template = send("/api/internal/market/prices?symbols=AAPL");

    assertThat(template.headers().get("Authorization")).containsExactly("Bearer service-token");
  }

  @Test
  @DisplayName("내부가 아닌 경로는 요청한 사용자의 토큰을 그대로 싣고 서비스 토큰은 받지 않는다")
  void otherPath_UserToken() {
    signedInAs("user-token");

    RequestTemplate template = send("/api/accounts/3/balance");

    assertThat(template.headers().get("Authorization")).containsExactly("Bearer user-token");
    verify(serviceTokens, never()).token();
  }

  @Test
  @DisplayName("사용자 토큰이 없으면 내부가 아닌 경로에는 토큰을 싣지 않는다")
  void otherPathWithoutUser_NoToken() {
    RequestTemplate template = send("/api/admin/users/dividend-settlements");

    assertThat(template.headers()).doesNotContainKey("Authorization");
  }

  @Test
  @DisplayName("서비스 토큰을 못 받으면 내부 경로 호출을 토큰 없이 보내지 않고 그 오류를 낸다")
  void serviceTokenFailure_NotSent() {
    given(serviceTokens.token()).willThrow(new IllegalStateException("keycloak down"));
    RequestTemplate template = new RequestTemplate().uri("/api/internal/market/fx/latest");

    assertThatThrownBy(() -> interceptor.apply(template)).isInstanceOf(IllegalStateException.class)
      .hasMessage("keycloak down");
    assertThat(template.headers()).doesNotContainKey("Authorization");
  }
}
