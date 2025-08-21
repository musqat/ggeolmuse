package com.muscat.user.keycloak;

import com.muscat.user.domain.dto.LoginResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class KeycloakService {

  @Value("${keycloak.auth-server-url}")
  private String keycloakUrl;

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.resource}")
  private String clientId;

  @Value("${keycloak.credentials.secret}")
  private String clientSecret;

  private final RestTemplate restTemplate = new RestTemplate();

  public String login(String email, String password) {
    try {
      String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

      log.info("Attempting login to: {}", tokenUrl);
      log.info("Client ID: {}, Realm: {}", clientId, realm);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("grant_type", "password");
      body.add("client_id", clientId);
      body.add("client_secret", clientSecret);
      body.add("username", email);
      body.add("password", password);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<LoginResponse> response = restTemplate.postForEntity(tokenUrl, request, LoginResponse.class);

      log.info("Login successful for user: {}", email);
      return Objects.requireNonNull(response.getBody()).getAccessToken();

    } catch (Exception e) {
      log.error("Keycloak login failed for user {}: {}", email, e.getMessage());
      log.error("Full exception: ", e);
      throw new RuntimeException("Invalid credentials: " + e.getMessage());
    }
  }

  public String createUser(String email, String password) {
    try {
      // 1. Admin 토큰 획득
      String adminToken = getAdminToken();

      // 2. 사용자 생성 요청
      String userUrl = keycloakUrl + "/admin/realms/" + realm + "/users";

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
      return location.substring(location.lastIndexOf('/') + 1);

    } catch (Exception e) {
      log.error("Failed to create Keycloak user: {}", e.getMessage());
      throw new RuntimeException("Failed to create user in Keycloak");
    }
  }

  private String getAdminToken() {
    String tokenUrl = keycloakUrl + "/realms/master/protocol/openid-connect/token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "password");
    body.add("client_id", "admin-cli");
    body.add("username", "admin");
    body.add("password", "admin");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

    ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
    return (String) response.getBody().get("access_token");
  }

  // 비밀번호 변경
  public void changePassword(String keycloakId, String newPassword) {
    try {
      String adminToken = getAdminToken();
      String passwordUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/reset-password";

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

      log.info("Password changed for Keycloak user: {}", keycloakId);

    } catch (Exception e) {
      log.error("Failed to change password for Keycloak user {}: {}", keycloakId, e.getMessage());
      throw new RuntimeException("Failed to change password in Keycloak");
    }
  }

  // 사용자 삭제
  public void deleteUser(String keycloakId) {
    try {
      String adminToken = getAdminToken();
      String deleteUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakId;

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(adminToken);

      HttpEntity<Void> entity = new HttpEntity<>(headers);
      restTemplate.delete(deleteUrl, entity);

      log.info("Deleted Keycloak user: {}", keycloakId);

    } catch (Exception e) {
      log.error("Failed to delete Keycloak user {}: {}", keycloakId, e.getMessage());
      throw new RuntimeException("Failed to delete user from Keycloak");
    }
  }

}