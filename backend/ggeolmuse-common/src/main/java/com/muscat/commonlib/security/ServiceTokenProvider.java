package com.muscat.commonlib.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.muscat.commonlib.exception.BusinessException;
import com.muscat.commonlib.exception.CommonResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// 서비스 사이 호출에 싣는 Keycloak 서비스 토큰(client_credentials, 만료 30초 전까지 재사용, 받기 실패 뒤 5초는 다시 요청 안 함)
public class ServiceTokenProvider {

  public static final String DEFAULT_CLIENT_ID = "ggeolmuse-service";

  private static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

  private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(5);

  private final RestClient restClient;
  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;
  private final Clock clock;
  private final ReentrantLock lock = new ReentrantLock();
  private String token;
  private Instant refreshAt = Instant.MIN;
  private Instant expiresAt = Instant.MIN;
  private Instant retryAt = Instant.MIN;

  public ServiceTokenProvider(RestClient.Builder builder, String tokenUrl, String clientId, String clientSecret,
                              Clock clock) {
    this.restClient = builder.build();
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clock = clock;
  }

  // 토큰 주소는 KEYCLOAK_AUTH_SERVER_URL · KEYCLOAK_REALM, 클라이언트는 KEYCLOAK_SERVICE_CLIENT_ID · KEYCLOAK_SERVICE_CLIENT_SECRET
  public static ServiceTokenProvider fromEnvironment(Environment environment) {
    String authServerUrl = environment.getProperty("KEYCLOAK_AUTH_SERVER_URL", "http://localhost:8080");
    String realm = environment.getProperty("KEYCLOAK_REALM", "muscathan");
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(10));
    return new ServiceTokenProvider(RestClient.builder().requestFactory(requestFactory),
      authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token",
      clientId(environment), environment.getProperty("KEYCLOAK_SERVICE_CLIENT_SECRET", ""), Clock.systemUTC());
  }

  static String clientId(Environment environment) {
    return environment.getProperty("KEYCLOAK_SERVICE_CLIENT_ID", DEFAULT_CLIENT_ID);
  }

  // 새로 받기에 실패해도 만료 전이면 받아 둔 토큰
  public String token() {
    lock.lock();
    try {
      Instant now = clock.instant();
      if (token != null && now.isBefore(refreshAt)) {
        return token;
      }
      if (now.isBefore(retryAt)) {
        return unexpiredOr(now, new BusinessException(CommonResponse.SERVICE_TOKEN_RETRY_WAIT));
      }
      try {
        TokenResponse response = requestToken();
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
          throw new BusinessException(CommonResponse.SERVICE_TOKEN_MISSING);
        }
        token = response.accessToken();
        expiresAt = now.plusSeconds(response.expiresIn());
        refreshAt = expiresAt.minus(REFRESH_MARGIN);
        return token;
      } catch (RuntimeException e) {
        retryAt = now.plus(RETRY_AFTER_FAILURE);
        return unexpiredOr(now, e);
      }
    } finally {
      lock.unlock();
    }
  }

  private String unexpiredOr(Instant now, RuntimeException failure) {
    if (token != null && now.isBefore(expiresAt)) {
      return token;
    }
    throw failure;
  }

  private TokenResponse requestToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    return restClient.post()
      .uri(tokenUrl)
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .retrieve()
      .body(TokenResponse.class);
  }

  record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
  }
}
