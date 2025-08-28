package com.muscat.user.domain.user.service.impl;

import com.muscat.user.common.exceptions.AuthenticationException;
import com.muscat.user.common.exceptions.KeycloakException;
import com.muscat.user.common.responses.KeycloakResponse;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.domain.user.dto.response.LoginResponseDto;
import com.muscat.user.domain.user.dto.request.ChangePasswordRequestDto;
import com.muscat.user.domain.user.service.KeycloakService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.boot.web.client.RestTemplateBuilder;

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

  @Value("${keycloak.admin.username}")
  private String adminUsername;

  @Value("${keycloak.admin.password}")
  private String adminPassword;

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
    try {
      String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

      log.info("Keycloak 로그인 시도: {}", email);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("grant_type", "password");
      body.add("client_id", clientId);
      body.add("client_secret", clientSecret);
      body.add("username", email);
      body.add("password", password);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(tokenUrl, request,
          LoginResponseDto.class);

      log.info("Keycloak 로그인 성공: {}", email);
      return Objects.requireNonNull(response.getBody()).getAccessToken();

    } catch (HttpClientErrorException.Unauthorized e) {
      log.warn("Keycloak 로그인 실패 - 인증 오류: {}", email);
      throw new AuthenticationException(UserResponse.INVALID_CREDENTIALS);
    } catch (Exception e) {
      log.error("Keycloak 로그인 처리 중 오류 발생: {}", e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.LOGIN_FAILED, e);
    }
  }

  @Override
  public String exchangeCodeForToken(String authorizationCode) {
    try {
      String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
      String redirectUri = "http://localhost:8080/api/auth/social/google/callback";

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

    } catch (HttpClientErrorException e) {
      log.error("토큰 교환 실패 - HTTP {}: {} | URL: {}", 
          e.getStatusCode(), e.getResponseBodyAsString(), keycloakUrl);
      throw new KeycloakException(KeycloakResponse.API_ERROR, e);
    } catch (Exception e) {
      log.error("토큰 교환 중 예상치 못한 오류: {} | URL: {}", e.getMessage(), keycloakUrl, e);
      throw new KeycloakException(KeycloakResponse.API_ERROR, e);
    }
  }

  @Override
  public Map<String, Object> parseTokenClaims(String jwtToken) {
    try {
      Jwt jwt = jwtDecoder.decode(jwtToken);
      log.info("JWT 토큰 검증 및 파싱 완료: {}", jwt.getClaimAsString("email"));
      return jwt.getClaims();

    } catch (Exception e) {
      log.error("JWT 토큰 파싱 실패: {}", e.getMessage(), e);
      throw new KeycloakException(KeycloakResponse.TOKEN_PARSE_FAILED, e);
    }
  }

  @Override
  public String createUser(String email, String password) {
    try {
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

    } catch (HttpClientErrorException e) {
      log.error("Keycloak 사용자 생성 실패 - HTTP {}: {} | Email: {} | URL: {}", 
          e.getStatusCode(), e.getResponseBodyAsString(), email, keycloakUrl);
      throw new KeycloakException(KeycloakResponse.USER_CREATE_FAILED, e);
    } catch (Exception e) {
      log.error("Keycloak 사용자 생성 중 예상치 못한 오류: {} | Email: {} | URL: {}", 
          e.getMessage(), email, keycloakUrl, e);
      throw new KeycloakException(KeycloakResponse.USER_CREATE_FAILED, e);
    }
  }

  @Override
  public void changePassword(String keycloakId, ChangePasswordRequestDto request) {
    try {
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

    } catch (HttpClientErrorException e) {
      log.error("비밀번호 변경 실패 - HTTP {}: {} | Keycloak ID: {} | URL: {}", 
          e.getStatusCode(), e.getResponseBodyAsString(), keycloakId, keycloakUrl);
      throw new KeycloakException(KeycloakResponse.PASSWORD_CHANGE_FAILED, e);
    } catch (Exception e) {
      log.error("비밀번호 변경 중 예상치 못한 오류: {} | Keycloak ID: {} | URL: {}", 
          e.getMessage(), keycloakId, keycloakUrl, e);
      throw new KeycloakException(KeycloakResponse.PASSWORD_CHANGE_FAILED, e);
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
      throw new KeycloakException(KeycloakResponse.USER_DELETE_FAILED, e);
    }
  }

  private String getAdminToken() {
    try {
      String tokenUrl = keycloakUrl + "/realms/master/protocol/openid-connect/token";
      
      log.debug("Keycloak Admin 토큰 요청: {} | Username: {}", tokenUrl, adminUsername);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("grant_type", "password");
      body.add("client_id", "admin-cli");
      body.add("username", adminUsername);
      body.add("password", adminPassword);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
      
      if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
        log.error("Keycloak Admin 토큰 응답에 access_token 없음: {}", response.getBody());
        throw new KeycloakException(KeycloakResponse.API_ERROR);
      }
      
      String token = (String) response.getBody().get("access_token");
      log.debug("Keycloak Admin 토큰 획득 성공");
      return token;

    } catch (HttpClientErrorException e) {
      log.error("Keycloak Admin 토큰 획득 실패 - HTTP {}: {} | URL: {} | Username: {}", 
          e.getStatusCode(), e.getResponseBodyAsString(), keycloakUrl, adminUsername);
      throw new KeycloakException(KeycloakResponse.API_ERROR, e);
    } catch (Exception e) {
      log.error("Keycloak Admin 토큰 획득 중 예상치 못한 오류: {} | URL: {} | Username: {}", 
          e.getMessage(), keycloakUrl, adminUsername, e);
      throw new KeycloakException(KeycloakResponse.API_ERROR, e);
    }
  }
}