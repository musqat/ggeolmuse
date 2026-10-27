package com.muscat.user.domain.user.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.commonlib.config.KeycloakProperties;
import com.muscat.user.common.enums.response.KeycloakResponse;
import com.muscat.user.common.exception.KeycloakException;
import com.muscat.user.config.AppProperties;
import com.muscat.user.domain.user.dto.response.LoginResponseDto;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import com.muscat.user.domain.user.service.KeycloakService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class KeycloakServiceImpl implements KeycloakService {

  private final KeycloakProperties keycloakProperties;
  private final AppProperties appProperties;
  private final RestTemplate restTemplate;
  private static final ObjectMapper JSON = new ObjectMapper();

  public KeycloakServiceImpl(KeycloakProperties keycloakProperties, AppProperties appProperties) {
    this.keycloakProperties = keycloakProperties;
    this.appProperties = appProperties;

    // RestTemplate을 타임아웃과 함께 설정
    RestTemplateBuilder builder = new RestTemplateBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(5))
      .readTimeout(java.time.Duration.ofSeconds(10));
    this.restTemplate = builder.build();
  }

  @Override
  public void warmUp() {
    getAdminToken();
  }

  @Override
  public TokenResponseDto login(String email, String password) {
    log.info("Keycloak 로그인 시도: {}", email);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "password");
    body.add("client_id", keycloakProperties.getResource());
    body.add("client_secret", keycloakProperties.getCredentials().getSecret());
    body.add("username", email);
    body.add("password", password);

    HttpEntity<MultiValueMap<String, String>> request = createFormRequest(body);
    ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
      getTokenUrl(), request, LoginResponseDto.class);

    log.info("Keycloak 로그인 성공: {}", email);
    return TokenResponseDto.from(Objects.requireNonNull(response.getBody()));
  }

  @Override
  public TokenResponseDto exchangePkceCode(String code, String codeVerifier, String redirectUri) {
    // 인가 요청을 public client 가 했으니 같은 client 로, secret 없이 verifier 로 증명한다
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("client_id", appProperties.getOauth().getPublicClientId());
    body.add("code", code);
    body.add("code_verifier", codeVerifier);
    body.add("redirect_uri", redirectUri);

    try {
      ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
        getTokenUrl(), createFormRequest(body), LoginResponseDto.class);
      log.info("구글 로그인 코드 교환 성공");
      return TokenResponseDto.from(Objects.requireNonNull(response.getBody()));
    } catch (HttpClientErrorException e) {
      log.warn("구글 로그인 코드 교환 실패 - HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new KeycloakException(KeycloakResponse.CODE_EXCHANGE_FAILED);
    } catch (Exception e) {
      log.error("구글 로그인 코드 교환 중 오류: {}", e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }
  }

  @Override
  public String createUser(String email, String password) {
    // 1. Admin 토큰 획득
    String adminToken = getAdminToken();

    // 2. 사용자 생성 요청
    String userUrl =
      keycloakProperties.getAuthServerUrl() + "/admin/realms/" + keycloakProperties.getRealm()
        + "/users";

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
  public void resetPassword(String keycloakId, String newPassword) {
    String adminToken = getAdminToken();
    String passwordUrl =
      keycloakProperties.getAuthServerUrl() + "/admin/realms/" + keycloakProperties.getRealm()
        + "/users/" + keycloakId + "/reset-password";

    log.debug("Keycloak 비밀번호 재설정 요청: {} | URL: {}", keycloakId, passwordUrl);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(adminToken);

    Map<String, Object> passwordRequest = Map.of(
      "type", "password",
      "value", newPassword,
      "temporary", false
    );

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(passwordRequest, headers);
    restTemplate.put(passwordUrl, entity);

    log.info("Keycloak 사용자 비밀번호 재설정 완료: {}", keycloakId);
  }

  @Override
  public void setEnabled(String keycloakId, boolean enabled) {
    String adminToken = getAdminToken();
    String userUrl =
      keycloakProperties.getAuthServerUrl() + "/admin/realms/" + keycloakProperties.getRealm()
        + "/users/" + keycloakId;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(adminToken);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("enabled", enabled), headers);
    restTemplate.put(userUrl, entity);

    log.info("Keycloak 사용자 활성 상태 변경: {} -> {}", keycloakId, enabled);
  }

  @Override
  public String createAdminUser(String email, String password) {
    // 1. 일반 사용자 생성
    String keycloakId = createUser(email, password);

    // 2. admin role 부여 (Keycloak realm role은 소문자)
    try {
      assignRealmRole(keycloakId, "admin");
      log.info("Keycloak admin 사용자 생성 및 role 할당 완료: {} | ID: {}", email, keycloakId);
      return keycloakId;

    } catch (Exception e) {
      log.error("Admin role 할당 실패, 사용자는 생성되었으나 권한 부여 실패: {} | ID: {}", email, keycloakId, e);
      throw new KeycloakException(KeycloakResponse.USER_CREATE_FAILED);
    }
  }

  @Override
  public void deleteUser(String keycloakId) {
    if (keycloakId == null || keycloakId.trim().isEmpty()) {
      log.warn("Keycloak ID가 null이거나 비어있음");
      return;
    }

    try {
      String adminToken = getAdminToken();
      String deleteUrl =
        keycloakProperties.getAuthServerUrl() + "/admin/realms/" + keycloakProperties.getRealm()
          + "/users/" + keycloakId;

      log.info("Keycloak 사용자 삭제 시도: {} -> {}", keycloakId, deleteUrl);

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(adminToken);

      HttpEntity<Void> entity = new HttpEntity<>(headers);
      restTemplate.exchange(deleteUrl, HttpMethod.DELETE, entity, Void.class);

      log.info("Keycloak 사용자 삭제 완료: {}", keycloakId);

    } catch (HttpClientErrorException e) {
      log.error("Keycloak 사용자 삭제 실패 - HTTP {}: {} | URL: {}",
        e.getStatusCode(), e.getResponseBodyAsString(), keycloakProperties.getAuthServerUrl());

      if (e.getStatusCode().value() == 404) {
        log.warn("Keycloak에서 사용자를 찾을 수 없음: {}", keycloakId);
        return; // 404는 이미 삭제된 것으로 간주
      }

      throw new KeycloakException(KeycloakResponse.USER_DELETE_FAILED);

    } catch (Exception e) {
      log.error("Keycloak 사용자 삭제 중 예상치 못한 오류: {} | Keycloak ID: {} | URL: {}",
        e.getMessage(), keycloakId, keycloakProperties.getAuthServerUrl(), e);
      throw new KeycloakException(KeycloakResponse.USER_DELETE_FAILED);
    }
  }

  @Override
  public String findUserIdByUsername(String username) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(getAdminToken());
      ResponseEntity<List> response = restTemplate.exchange(
        getAdminRealmUrl() + "/users?username=" + username + "&exact=true", HttpMethod.GET,
        new HttpEntity<>(headers), List.class);
      List<?> users = response.getBody() == null ? List.of() : response.getBody();
      return users.isEmpty() ? null : (String) ((Map<?, ?>) users.getFirst()).get("id");

    } catch (Exception e) {
      log.error("Keycloak 사용자 이름 조회 실패: {} | Error: {}", username, e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }
  }

  // 역할 표현은 사용자의 역할 매핑 API 에서 받음(realm 역할 목록은 view-realm 이 필요)
  @Override
  public void assignRealmRole(String keycloakId, String roleName) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(getAdminToken());
      String mappingsUrl = getAdminRealmUrl() + "/users/" + keycloakId + "/role-mappings/realm";
      if (findRole(mappingsUrl, roleName, headers) != null) {
        log.info("이미 가진 Keycloak realm role: Role: {} | Keycloak ID: {}", roleName, keycloakId);
        return;
      }
      Map<String, Object> role = findRole(mappingsUrl + "/available", roleName, headers);
      if (role == null) {
        log.error("부여할 수 있는 Keycloak realm role 이 아님: {}", roleName);
        throw new KeycloakException(KeycloakResponse.API_ERROR);
      }

      headers.setContentType(MediaType.APPLICATION_JSON);
      restTemplate.postForEntity(mappingsUrl, new HttpEntity<>(List.of(role), headers), Void.class);

      log.info("Keycloak realm role 할당 완료: Role: {} | Keycloak ID: {}", roleName, keycloakId);

    } catch (Exception e) {
      log.error("Keycloak realm role 할당 실패: {} | Role: {} | Error: {}", keycloakId, roleName,
        e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }
  }

  @Override
  public void removeRealmRole(String keycloakId, String roleName) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(getAdminToken());
      String mappingsUrl = getAdminRealmUrl() + "/users/" + keycloakId + "/role-mappings/realm";
      Map<String, Object> role = findRole(mappingsUrl, roleName, headers);
      if (role == null) {
        log.info("이미 없는 Keycloak realm role: Role: {} | Keycloak ID: {}", roleName, keycloakId);
        return;
      }

      headers.setContentType(MediaType.APPLICATION_JSON);
      restTemplate.exchange(mappingsUrl, HttpMethod.DELETE, new HttpEntity<>(List.of(role), headers), Void.class);

      log.info("Keycloak realm role 해제 완료: Role: {} | Keycloak ID: {}", roleName, keycloakId);

    } catch (Exception e) {
      log.error("Keycloak realm role 해제 실패: {} | Role: {} | Error: {}", keycloakId, roleName,
        e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }
  }

  @Override
  public void logoutUserSessions(String keycloakId) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(getAdminToken());
      restTemplate.postForEntity(getAdminRealmUrl() + "/users/" + keycloakId + "/logout",
        new HttpEntity<>(headers), Void.class);

      log.info("Keycloak 사용자 세션 종료 완료: Keycloak ID: {}", keycloakId);

    } catch (Exception e) {
      log.error("Keycloak 사용자 세션 종료 실패: {} | Error: {}", keycloakId, e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }
  }

  // 역할 목록 API 에서 그 이름의 역할 표현. 없으면 null
  @SuppressWarnings("unchecked")
  private Map<String, Object> findRole(String url, String roleName, HttpHeaders headers) {
    ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
    List<?> roles = response.getBody() == null ? List.of() : response.getBody();
    return roles.stream()
      .map(role -> (Map<String, Object>) role)
      .filter(role -> roleName.equals(role.get("name")))
      .findFirst()
      .orElse(null);
  }

  @Override
  public TokenResponseDto refreshToken(String refreshToken) {
    log.info("Keycloak 토큰 갱신 시도");

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "refresh_token");
    addClientFor(body, refreshToken);
    body.add("refresh_token", refreshToken);

    HttpEntity<MultiValueMap<String, String>> request = createFormRequest(body);

    try {
      ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
        getTokenUrl(), request, LoginResponseDto.class);

      log.info("Keycloak 토큰 갱신 성공");
      return TokenResponseDto.from(Objects.requireNonNull(response.getBody()));

    } catch (HttpClientErrorException e) {
      log.error("Keycloak 토큰 갱신 실패 - HTTP {}: {}",
        e.getStatusCode(), e.getResponseBodyAsString());
      throw new KeycloakException(KeycloakResponse.TOKEN_REFRESH_FAILED);
    } catch (Exception e) {
      log.error("Keycloak 토큰 갱신 중 예상치 못한 오류: {}", e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.TOKEN_REFRESH_FAILED);
    }
  }

  @Override
  public void logout(String refreshToken) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    addClientFor(body, refreshToken);
    body.add("refresh_token", refreshToken);

    try {
      restTemplate.postForEntity(getLogoutUrl(), createFormRequest(body), Void.class);
      log.info("Keycloak 세션 종료");
    } catch (HttpClientErrorException.BadRequest e) {
      // 만료 · 이미 끊긴 세션의 토큰은 400(invalid_grant)이 온다. 끊을 세션이 없으니 로그아웃은 끝난 것으로 본다.
      // client 인증 실패(401) 같은 설정 문제는 아래로 보내 숨기지 않는다
      log.warn("Keycloak 세션 종료 생략 - HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
    } catch (Exception e) {
      log.error("Keycloak 세션 종료 중 오류: {}", e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.API_ERROR);
    }
  }

  private String getAdminToken() {
    try {
      log.debug("Keycloak Admin 토큰 요청");

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("grant_type", "client_credentials");
      body.add("client_id", keycloakProperties.getResource());
      body.add("client_secret", keycloakProperties.getCredentials().getSecret());

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
    return keycloakProperties.getAuthServerUrl() + "/realms/" + keycloakProperties.getRealm()
      + "/protocol/openid-connect/token";
  }

  private String getLogoutUrl() {
    return keycloakProperties.getAuthServerUrl() + "/realms/" + keycloakProperties.getRealm()
      + "/protocol/openid-connect/logout";
  }

  // realm 관리 API 주소
  private String getAdminRealmUrl() {
    return keycloakProperties.getAuthServerUrl() + "/admin/realms/" + keycloakProperties.getRealm();
  }

  // refresh token 은 받은 client 로 보내야 한다. 다르면 Keycloak 이 400 "Token client and authorized client don't match".
  // 구글 로그인은 브라우저가 public client 로 받아 secret 없이 보내고, 이메일 로그인은 여기서 받은 confidential client 다
  private void addClientFor(MultiValueMap<String, String> body, String refreshToken) {
    String publicClientId = appProperties.getOauth().getPublicClientId();
    if (publicClientId != null && publicClientId.equals(issuedFor(refreshToken))) {
      body.add("client_id", publicClientId);
      return;
    }
    body.add("client_id", keycloakProperties.getResource());
    body.add("client_secret", keycloakProperties.getCredentials().getSecret());
  }

  // 토큰을 받은 client(azp). 서명 검증은 Keycloak 이 하니 여기서는 payload 만 읽는다. 못 읽으면 null
  private String issuedFor(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        return null;
      }
      JsonNode payload = JSON.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
      return payload.path("azp").asText(null);
    } catch (Exception e) {
      return null;
    }
  }

  private HttpEntity<MultiValueMap<String, String>> createFormRequest(
    MultiValueMap<String, String> body) {
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
