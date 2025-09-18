package com.muscat.user.domain.user.service.impl;

import com.muscat.user.common.enums.responses.KeycloakResponse;
import com.muscat.user.common.exceptions.KeycloakException;
import com.muscat.user.domain.user.dto.request.ChangePasswordRequestDto;
import com.muscat.user.domain.user.dto.response.LoginResponseDto;
import com.muscat.user.domain.user.service.KeycloakService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class KeycloakServiceImpl implements KeycloakService {

  @Value("${keycloak.auth-server-url}")
  private String keycloakUrl;

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.resource}")
  private String clientId;

  @Value("${keycloak.credentials.secret}")
  private String clientSecret;


  @Value("${app.oauth.redirect-uri}")
  private String redirectUri;

  private final JwtDecoder jwtDecoder;
  private final RestTemplate restTemplate;

  public KeycloakServiceImpl(JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;

    // RestTemplate을 타임아웃과 함께 설정
    RestTemplateBuilder builder = new RestTemplateBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(5))
        .readTimeout(java.time.Duration.ofSeconds(10));
    this.restTemplate = builder.build();
  }

  @Override
  public String login(String email, String password) {
    log.info("Keycloak 로그인 시도: {}", email);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "password");
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);
    body.add("username", email);
    body.add("password", password);

    HttpEntity<MultiValueMap<String, String>> request = createFormRequest(body);
    ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
        getTokenUrl(), request, LoginResponseDto.class);

    log.info("Keycloak 로그인 성공: {}", email);
    return Objects.requireNonNull(response.getBody()).getAccessToken();
  }

  @Override
  public String exchangeCodeForToken(String authorizationCode) {
    String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

    log.debug("Authorization Code 교환 요청: {} | Redirect URI: {}", tokenUrl, redirectUri);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);
    body.add("code", authorizationCode);
    body.add("redirect_uri", redirectUri);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

    log.info("Authorization Code를 토큰으로 교환 중");

    ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

    if (response.getBody() == null) {
      log.error("토큰 교환 응답이 null입니다");
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }

    Map<String, Object> tokenResponse = response.getBody();
    String accessToken = (String) tokenResponse.get("access_token");

    if (accessToken == null || accessToken.trim().isEmpty()) {
      log.error("토큰 교환 응답에 access_token이 없습니다: {}", tokenResponse);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }

    log.info("토큰 교환 성공");
    return accessToken;
  }

  @Override
  public Map<String, Object> parseTokenClaims(String jwtToken) {
    Jwt jwt = jwtDecoder.decode(jwtToken);
    log.info("JWT 토큰 검증 및 파싱 완료: {}", jwt.getClaimAsString("email"));
    return jwt.getClaims();
  }

  @Override
  public String createUser(String email, String password) {
    // 1. Admin 토큰 획득
    String adminToken = getAdminToken();

    // 2. 사용자 생성 요청
    String userUrl = keycloakUrl + "/admin/realms/" + realm + "/users";

    log.debug("Keycloak 사용자 생성 요청: {} | URL: {}", email, userUrl);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(adminToken);

    Map<String, Object> userRequest = Map.of(
        "username", email,
        "email", email,
        "enabled", true,
        "emailVerified", true,
        "credentials", List.of(Map.of(
            "type", "password",
            "value", password,
            "temporary", false
        ))
    );

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(userRequest, headers);

    ResponseEntity<Void> response = restTemplate.postForEntity(userUrl, entity, Void.class);

    // 3. Location 헤더에서 사용자 ID 추출
    String location = response.getHeaders().getFirst("Location");
    if (location == null || location.trim().isEmpty()) {
      log.error("사용자 생성 응답에 Location 헤더가 없습니다: {}", email);
      throw new KeycloakException(KeycloakResponse.USER_CREATE_FAILED);
    }

    String keycloakId = location.substring(location.lastIndexOf('/') + 1);
    if (keycloakId.trim().isEmpty()) {
      log.error("Location 헤더에서 Keycloak ID 추출 실패: {} | Location: {}", email, location);
      throw new KeycloakException(KeycloakResponse.USER_CREATE_FAILED);
    }

    log.info("Keycloak 사용자 생성 완료: {} | ID: {}", email, keycloakId);
    return keycloakId;
  }

  @Override
  public void changePassword(String keycloakId, ChangePasswordRequestDto request) {
    String adminToken = getAdminToken();
    String passwordUrl =
        keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/reset-password";

    log.debug("Keycloak 비밀번호 변경 요청: {} | URL: {}", keycloakId, passwordUrl);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(adminToken);

    Map<String, Object> passwordRequest = Map.of(
        "type", "password",
        "value", request.getNewPassword(),
        "temporary", false
    );

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(passwordRequest, headers);
    restTemplate.put(passwordUrl, entity);

    log.info("Keycloak 사용자 비밀번호 변경 완료: {}", keycloakId);
  }

  @Override
  public void deleteUser(String keycloakId) {
    if (keycloakId == null || keycloakId.trim().isEmpty()) {
      log.warn("Keycloak ID가 null이거나 비어있음");
      return;
    }

    try {
      String adminToken = getAdminToken();
      String deleteUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakId;

      log.info("Keycloak 사용자 삭제 시도: {} -> {}", keycloakId, deleteUrl);

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(adminToken);

      HttpEntity<Void> entity = new HttpEntity<>(headers);
      restTemplate.delete(deleteUrl, entity);

      log.info("Keycloak 사용자 삭제 완료: {}", keycloakId);

    } catch (HttpClientErrorException e) {
      log.error("Keycloak 사용자 삭제 실패 - HTTP {}: {} | URL: {}",
          e.getStatusCode(), e.getResponseBodyAsString(), keycloakUrl);

      if (e.getStatusCode().value() == 404) {
        log.warn("Keycloak에서 사용자를 찾을 수 없음: {}", keycloakId);
        return; // 404는 이미 삭제된 것으로 간주
      }

      throw new KeycloakException(KeycloakResponse.USER_DELETE_FAILED);

    } catch (Exception e) {
      log.error("Keycloak 사용자 삭제 중 예상치 못한 오류: {} | Keycloak ID: {} | URL: {}",
          e.getMessage(), keycloakId, keycloakUrl, e);
      throw new KeycloakException(KeycloakResponse.USER_DELETE_FAILED);
    }
  }

  private String getAdminToken() {
    try {
      log.debug("Keycloak Admin 토큰 요청");

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("grant_type", "client_credentials");
      body.add("client_id", clientId);
      body.add("client_secret", clientSecret);

      HttpEntity<MultiValueMap<String, String>> request = createFormRequest(body);
      ResponseEntity<Map> response = restTemplate.postForEntity(getTokenUrl(), request, Map.class);

      String token = extractAccessToken(response, "Admin 토큰 획득");
      log.debug("Keycloak Admin 토큰 획득 성공");
      return token;

    } catch (HttpClientErrorException e) {
      log.error("Keycloak Admin 토큰 획득 실패 - HTTP {}: {}",
          e.getStatusCode(), e.getResponseBodyAsString());
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    } catch (Exception e) {
      log.error("Keycloak Admin 토큰 획득 중 예상치 못한 오류: {}", e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }
  }

  private String getTokenUrl() {
    return keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
  }

  private HttpEntity<MultiValueMap<String, String>> createFormRequest(MultiValueMap<String, String> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    return new HttpEntity<>(body, headers);
  }

  private String extractAccessToken(ResponseEntity<Map> response, String operation) {
    if (response.getBody() == null) {
      log.error("{} 응답이 null입니다", operation);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }

    String accessToken = (String) response.getBody().get("access_token");
    if (accessToken == null || accessToken.trim().isEmpty()) {
      log.error("{} 응답에 access_token이 없습니다: {}", operation, response.getBody());
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }

    log.info("{} 성공", operation);
    return accessToken;
  }
}