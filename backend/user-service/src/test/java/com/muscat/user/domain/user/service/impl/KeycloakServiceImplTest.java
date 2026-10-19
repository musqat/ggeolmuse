package com.muscat.user.domain.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.config.KeycloakProperties;
import com.muscat.user.common.enums.responses.KeycloakResponse;
import com.muscat.user.common.exceptions.KeycloakException;
import com.muscat.user.config.AppProperties;
import com.muscat.user.domain.user.dto.response.LoginResponseDto;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeycloakService 단위 테스트")
class KeycloakServiceImplTest {

  @Mock
  private RestTemplate restTemplate;

  private KeycloakServiceImpl keycloakService;

  private KeycloakProperties keycloakProperties;
  private AppProperties appProperties;

  private static final String TEST_EMAIL = "test@example.com";
  private static final String TEST_PASSWORD = "password123";
  private static final String TEST_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test.token";
  private static final String TEST_KEYCLOAK_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String AUTH_SERVER_URL = "http://localhost:8080";
  private static final String REALM = "test-realm";
  private static final String CLIENT_ID = "test-client";
  private static final String CLIENT_SECRET = "test-secret";

  @BeforeEach
  void setUp() {
    // KeycloakProperties 설정
    keycloakProperties = new KeycloakProperties();
    keycloakProperties.setAuthServerUrl(AUTH_SERVER_URL);
    keycloakProperties.setRealm(REALM);
    keycloakProperties.setResource(CLIENT_ID);

    KeycloakProperties.Credentials credentials = new KeycloakProperties.Credentials();
    credentials.setSecret(CLIENT_SECRET);
    keycloakProperties.setCredentials(credentials);

    appProperties = new AppProperties();

    // KeycloakService 인스턴스 생성
    keycloakService = new KeycloakServiceImpl(keycloakProperties, appProperties);

    // RestTemplate을 Mock으로 교체 (리플렉션 사용)
    ReflectionTestUtils.setField(keycloakService, "restTemplate", restTemplate);
  }

  @Nested
  @DisplayName("로그인 테스트")
  class LoginTests {

    @Test
    @DisplayName("정상적으로 로그인되고 access · refresh token 이 같이 반환된다")
    void login_Success() {
      // given
      LoginResponseDto loginResponse = new LoginResponseDto(
        TEST_TOKEN,      // accessToken
        "refresh_token", // refreshToken
        300,             // expiresIn
        "Bearer",        // tokenType
        1800             // refreshExpiresIn
      );

      ResponseEntity<LoginResponseDto> responseEntity = ResponseEntity.ok(loginResponse);
      given(
        restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willReturn(responseEntity);

      // when
      TokenResponseDto tokens = keycloakService.login(TEST_EMAIL, TEST_PASSWORD);

      // then
      assertThat(tokens).isEqualTo(new TokenResponseDto(TEST_TOKEN, "refresh_token", 300, 1800));
      verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class),
        eq(LoginResponseDto.class));
    }

    @Test
    @DisplayName("잘못된 자격증명으로 로그인 시 예외가 발생한다")
    void login_InvalidCredentials_ThrowsException() {
      // given
      given(
        restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

      // when & then
      assertThatThrownBy(() -> keycloakService.login(TEST_EMAIL, "wrong_password"))
        .isInstanceOf(HttpClientErrorException.class);
    }
  }

  @Nested
  @DisplayName("구글 로그인 PKCE 코드 교환 테스트")
  class ExchangePkceCodeTests {

    @Test
    @DisplayName("public client 로 secret 없이 code · code_verifier · redirect_uri 를 보내 두 토큰을 받는다")
    void exchangePkceCode_UsesPublicClientWithVerifier() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willReturn(ResponseEntity.ok(new LoginResponseDto("a", "r", 3600, "Bearer", 7200)));

      TokenResponseDto tokens = keycloakService.exchangePkceCode("code-1", "verifier-1", "https://app.example/oauth/callback");

      assertThat(tokens).isEqualTo(new TokenResponseDto("a", "r", 3600, 7200));
      MultiValueMap<String, String> body = capturedForm(LoginResponseDto.class);
      assertThat(body.getFirst("grant_type")).isEqualTo("authorization_code");
      assertThat(body.getFirst("client_id")).isEqualTo("ggeolmuse-frontend");
      assertThat(body.getFirst("code")).isEqualTo("code-1");
      assertThat(body.getFirst("code_verifier")).isEqualTo("verifier-1");
      assertThat(body.getFirst("redirect_uri")).isEqualTo("https://app.example/oauth/callback");
      assertThat(body.containsKey("client_secret")).isFalse();
    }

    @Test
    @DisplayName("Keycloak 이 코드를 거절하면 CODE_EXCHANGE_FAILED")
    void exchangePkceCode_Rejected_Throws() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "invalid_grant"));

      assertThatThrownBy(() -> keycloakService.exchangePkceCode("used", "v", "https://app.example/oauth/callback"))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.CODE_EXCHANGE_FAILED.getMessage());
    }
  }

  @Nested
  @DisplayName("사용자 생성 테스트")
  class CreateUserTests {

    @Test
    @DisplayName("정상적으로 Keycloak에 사용자가 생성된다")
    void createUser_Success() {
      // given
      // Admin 토큰 응답 (getAdminToken 호출)
      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      // 사용자 생성 응답 (Location 헤더 포함)
      HttpHeaders headers = new HttpHeaders();
      headers.add("Location",
        AUTH_SERVER_URL + "/admin/realms/" + REALM + "/users/" + TEST_KEYCLOAK_ID);
      ResponseEntity<Void> createUserEntity = new ResponseEntity<>(headers, HttpStatus.CREATED);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
        .willReturn(createUserEntity);

      // when
      String keycloakId = keycloakService.createUser(TEST_EMAIL, TEST_PASSWORD);

      // then
      assertThat(keycloakId).isEqualTo(TEST_KEYCLOAK_ID);
      verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class),
        eq(Map.class));
      verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class),
        eq(Void.class));
    }

    @Test
    @DisplayName("Location 헤더가 없으면 예외가 발생한다")
    void createUser_NoLocationHeader_ThrowsException() {
      // given
      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      ResponseEntity<Void> createUserEntity = new ResponseEntity<>(HttpStatus.CREATED);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
        .willReturn(createUserEntity);

      // when & then
      assertThatThrownBy(() -> keycloakService.createUser(TEST_EMAIL, TEST_PASSWORD))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.USER_CREATE_FAILED.getMessage());
    }

    @Test
    @DisplayName("Location 헤더에서 Keycloak ID 추출 실패 시 예외가 발생한다")
    void createUser_InvalidLocationHeader_ThrowsException() {
      // given
      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      HttpHeaders headers = new HttpHeaders();
      headers.add("Location", AUTH_SERVER_URL + "/admin/realms/" + REALM + "/users/");
      ResponseEntity<Void> createUserEntity = new ResponseEntity<>(headers, HttpStatus.CREATED);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
        .willReturn(createUserEntity);

      // when & then
      assertThatThrownBy(() -> keycloakService.createUser(TEST_EMAIL, TEST_PASSWORD))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.USER_CREATE_FAILED.getMessage());
    }

    @Test
    @DisplayName("Admin 토큰 획득 실패 시 예외가 발생한다")
    void createUser_AdminTokenFailed_ThrowsException() {
      // given
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

      // when & then
      assertThatThrownBy(() -> keycloakService.createUser(TEST_EMAIL, TEST_PASSWORD))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.API_ERROR.getMessage());
    }
  }

  @Nested
  @DisplayName("사용자 삭제 테스트")
  class DeleteUserTests {

    @Test
    @DisplayName("정상적으로 사용자가 삭제된다")
    void deleteUser_Success() {
      // given
      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      doNothing().when(restTemplate).delete(anyString(), any(HttpEntity.class));

      // when
      keycloakService.deleteUser(TEST_KEYCLOAK_ID);

      // then
      verify(restTemplate).delete(anyString(), any(HttpEntity.class));
    }

    @Test
    @DisplayName("Keycloak ID가 null이면 삭제하지 않는다")
    void deleteUser_NullKeycloakId_DoesNothing() {
      // when
      keycloakService.deleteUser(null);

      // then
      verify(restTemplate, never()).delete(anyString(), any(HttpEntity.class));
    }

    @Test
    @DisplayName("Keycloak ID가 빈 문자열이면 삭제하지 않는다")
    void deleteUser_EmptyKeycloakId_DoesNothing() {
      // when
      keycloakService.deleteUser("   ");

      // then
      verify(restTemplate, never()).delete(anyString(), any(HttpEntity.class));
    }

    @Test
    @DisplayName("404 응답이면 예외 없이 정상 처리된다")
    void deleteUser_NotFound_NoException() {
      // given
      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      doThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND))
        .when(restTemplate).delete(anyString(), any(HttpEntity.class));

      // when
      keycloakService.deleteUser(TEST_KEYCLOAK_ID);

      // then - 예외 없이 정상 종료
      verify(restTemplate).delete(anyString(), any(HttpEntity.class));
    }

    @Test
    @DisplayName("404가 아닌 HTTP 에러 시 예외가 발생한다")
    void deleteUser_OtherHttpError_ThrowsException() {
      // given
      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      doThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restTemplate).delete(anyString(), any(HttpEntity.class));

      // when & then
      assertThatThrownBy(() -> keycloakService.deleteUser(TEST_KEYCLOAK_ID))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.USER_DELETE_FAILED.getMessage());
    }

    @Test
    @DisplayName("예상치 못한 예외 발생 시 KeycloakException으로 변환된다")
    void deleteUser_UnexpectedException_ThrowsKeycloakException() {
      // given
      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      doThrow(new RuntimeException("Unexpected error"))
        .when(restTemplate).delete(anyString(), any(HttpEntity.class));

      // when & then
      assertThatThrownBy(() -> keycloakService.deleteUser(TEST_KEYCLOAK_ID))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.USER_DELETE_FAILED.getMessage());
    }
  }

  @Nested
  @DisplayName("토큰 갱신 테스트")
  class RefreshTokenTests {

    @Test
    @DisplayName("refresh_token 으로 갱신하고 Keycloak 이 준 새 refresh token 까지 돌려준다")
    void refreshToken_ReturnsBothTokens() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willReturn(ResponseEntity.ok(new LoginResponseDto("new-access", "new-refresh", 3600, "Bearer", 7200)));

      TokenResponseDto tokens = keycloakService.refreshToken("old-refresh");

      assertThat(tokens).isEqualTo(new TokenResponseDto("new-access", "new-refresh", 3600, 7200));
      MultiValueMap<String, String> body = capturedForm(LoginResponseDto.class);
      assertThat(body.getFirst("grant_type")).isEqualTo("refresh_token");
      assertThat(body.getFirst("refresh_token")).isEqualTo("old-refresh");
      assertThat(body.getFirst("client_id")).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("Keycloak 이 거절하면 TOKEN_REFRESH_FAILED")
    void refreshToken_Rejected_Throws() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "invalid_grant"));

      assertThatThrownBy(() -> keycloakService.refreshToken("expired"))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.TOKEN_REFRESH_FAILED.getMessage());
    }

    @Test
    @DisplayName("구글 로그인 토큰(azp=public client)은 그 client 로 secret 없이 갱신한다")
    void refreshToken_PublicClientToken_UsesPublicClient() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willReturn(ResponseEntity.ok(new LoginResponseDto("a", "r", 3600, "Bearer", 7200)));

      keycloakService.refreshToken(tokenIssuedFor("ggeolmuse-frontend"));

      MultiValueMap<String, String> body = capturedForm(LoginResponseDto.class);
      assertThat(body.getFirst("client_id")).isEqualTo("ggeolmuse-frontend");
      assertThat(body.containsKey("client_secret")).isFalse();
    }

    @Test
    @DisplayName("이메일 로그인 토큰(azp=confidential client)은 client 인증을 붙여 갱신한다")
    void refreshToken_ConfidentialToken_UsesClientSecret() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willReturn(ResponseEntity.ok(new LoginResponseDto("a", "r", 3600, "Bearer", 7200)));

      keycloakService.refreshToken(tokenIssuedFor(CLIENT_ID));

      MultiValueMap<String, String> body = capturedForm(LoginResponseDto.class);
      assertThat(body.getFirst("client_id")).isEqualTo(CLIENT_ID);
      assertThat(body.getFirst("client_secret")).isEqualTo(CLIENT_SECRET);
    }
  }

  @Nested
  @DisplayName("로그아웃 테스트")
  class LogoutTests {

    @Test
    @DisplayName("realm 의 logout 엔드포인트에 client 인증과 refresh_token 을 보낸다")
    void logout_PostsToLogoutEndpoint() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
        .willReturn(ResponseEntity.noContent().build());

      keycloakService.logout("refresh-1");

      ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(url.capture(), any(HttpEntity.class), eq(Void.class));
      assertThat(url.getValue()).isEqualTo(AUTH_SERVER_URL + "/realms/" + REALM + "/protocol/openid-connect/logout");
      MultiValueMap<String, String> body = capturedForm(Void.class);
      assertThat(body.getFirst("refresh_token")).isEqualTo("refresh-1");
      assertThat(body.getFirst("client_id")).isEqualTo(CLIENT_ID);
      assertThat(body.getFirst("client_secret")).isEqualTo(CLIENT_SECRET);
    }

    @Test
    @DisplayName("만료 · 이미 끊긴 세션(400)이면 예외 없이 끝난다")
    void logout_AlreadyEnded_Ignored() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
        .willThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "invalid_grant", HttpHeaders.EMPTY, null, null));

      keycloakService.logout("expired");
    }

    @Test
    @DisplayName("client 인증 실패(401) 같은 설정 문제는 API_ERROR 로 올린다")
    void logout_ClientAuthFailure_Throws() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
        .willThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "unauthorized_client", HttpHeaders.EMPTY, null, null));

      assertThatThrownBy(() -> keycloakService.logout("refresh-1"))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.API_ERROR.getMessage());
    }

    @Test
    @DisplayName("구글 로그인 토큰은 public client 로 secret 없이 세션을 끊는다")
    void logout_PublicClientToken_UsesPublicClient() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
        .willReturn(ResponseEntity.noContent().build());

      keycloakService.logout(tokenIssuedFor("ggeolmuse-frontend"));

      MultiValueMap<String, String> body = capturedForm(Void.class);
      assertThat(body.getFirst("client_id")).isEqualTo("ggeolmuse-frontend");
      assertThat(body.containsKey("client_secret")).isFalse();
    }
  }

  // azp 만 든 JWT 모양 문자열(서명은 Keycloak 이 보니 여기서는 안 본다)
  private static String tokenIssuedFor(String azp) {
    String payload = Base64.getUrlEncoder().withoutPadding()
      .encodeToString(("{\"azp\":\"" + azp + "\",\"typ\":\"Refresh\"}").getBytes(StandardCharsets.UTF_8));
    return "header." + payload + ".signature";
  }

  // restTemplate 에 보낸 폼 본문
  @SuppressWarnings("unchecked")
  private <T> MultiValueMap<String, String> capturedForm(Class<T> responseType) {
    ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> request = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate).postForEntity(anyString(), request.capture(), eq(responseType));
    return request.getValue().getBody();
  }

  // 관리자 토큰 요청(postForEntity ... Map.class)이 돌려줄 응답
  private void adminToken(Map<String, Object> body) {
    given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
      .willReturn(ResponseEntity.ok(body));
  }

  private void adminTokenOk() {
    adminToken(Map.of("access_token", "admin_access_token"));
  }

  @Nested
  @DisplayName("관리자 API · 실패 경로")
  class AdminApiTests {

    @Test
    @DisplayName("비밀번호 재설정은 관리자 토큰으로 그 사용자의 reset-password 에 영구 비밀번호를 보낸다")
    @SuppressWarnings("unchecked")
    void resetPassword_PutsPermanentPassword() {
      adminTokenOk();

      keycloakService.resetPassword(TEST_KEYCLOAK_ID, "N3w-pass!");

      ArgumentCaptor<HttpEntity<Map<String, Object>>> entity = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate).put(eq(AUTH_SERVER_URL + "/admin/realms/" + REALM + "/users/" + TEST_KEYCLOAK_ID
        + "/reset-password"), entity.capture());
      assertThat(entity.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer admin_access_token");
      assertThat(entity.getValue().getBody())
        .containsEntry("type", "password")
        .containsEntry("value", "N3w-pass!")
        .containsEntry("temporary", false);
    }

    @Test
    @DisplayName("활성 상태 변경은 관리자 토큰으로 그 사용자에 enabled 만 보낸다")
    @SuppressWarnings("unchecked")
    void setEnabled_PutsEnabledFlag() {
      adminTokenOk();

      keycloakService.setEnabled(TEST_KEYCLOAK_ID, false);

      ArgumentCaptor<HttpEntity<Map<String, Object>>> entity = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate).put(eq(AUTH_SERVER_URL + "/admin/realms/" + REALM + "/users/" + TEST_KEYCLOAK_ID),
        entity.capture());
      assertThat(entity.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer admin_access_token");
      assertThat(entity.getValue().getBody()).isEqualTo(Map.of("enabled", false));
    }

    @Test
    @DisplayName("관리자 토큰 응답이 비었거나 access_token 이 없으면 API_ERROR 다")
    void adminToken_BadResponse_ApiError() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(ResponseEntity.ok(null))
        .willReturn(ResponseEntity.ok(Map.of("token_type", "Bearer")))
        .willThrow(new IllegalStateException("connection reset"));

      for (int i = 0; i < 3; i++) {
        assertThatThrownBy(() -> keycloakService.resetPassword(TEST_KEYCLOAK_ID, "N3w-pass!"))
          .isInstanceOf(KeycloakException.class)
          .hasMessage(KeycloakResponse.API_ERROR.getMessage());
      }
      verify(restTemplate, never()).put(anyString(), any(HttpEntity.class));
    }

    @Test
    @DisplayName("이메일로 사용자를 찾으면 id, 없거나 조회가 실패하면 null 이다")
    void findUserByEmail() {
      adminTokenOk();
      given(restTemplate.exchange(contains("email=found@example.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of(Map.of("id", TEST_KEYCLOAK_ID))));
      given(restTemplate.exchange(contains("email=none@example.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of()));
      given(restTemplate.exchange(contains("email=down@example.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

      assertThat(keycloakService.findUserByEmail("found@example.com")).isEqualTo(TEST_KEYCLOAK_ID);
      assertThat(keycloakService.findUserByEmail("none@example.com")).isNull();
      assertThat(keycloakService.findUserByEmail("down@example.com")).isNull();
    }

    @Test
    @DisplayName("사용자에게 부여할 수 있는 역할 중 그 역할을 사용자 역할 매핑에 추가하고, realm 역할 목록은 읽지 않는다")
    @SuppressWarnings("unchecked")
    void assignRealmRole_PostsRoleMapping() {
      adminTokenOk();
      Map<String, Object> role = Map.of("id", "role-1", "name", "admin");
      String mappings = AUTH_SERVER_URL + "/admin/realms/" + REALM + "/users/" + TEST_KEYCLOAK_ID + "/role-mappings/realm";
      given(restTemplate.exchange(eq(mappings), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of()));
      given(restTemplate.exchange(eq(mappings + "/available"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of(Map.of("id", "role-2", "name", "user"), role)));

      keycloakService.assignRealmRole(TEST_KEYCLOAK_ID, "admin");

      ArgumentCaptor<HttpEntity<List<Map<String, Object>>>> entity = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate).postForEntity(eq(mappings), entity.capture(), eq(Void.class));
      assertThat(entity.getValue().getBody()).containsExactly(role);
      verify(restTemplate, never()).exchange(contains("/roles/"), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("이미 그 역할을 가진 사용자면 다시 넣지 않는다")
    void assignRealmRole_AlreadyMapped_NoPost() {
      adminTokenOk();
      given(restTemplate.exchange(endsWith("/role-mappings/realm"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of(Map.of("id", "role-1", "name", "admin"))));

      keycloakService.assignRealmRole(TEST_KEYCLOAK_ID, "admin");

      verify(restTemplate, never()).postForEntity(contains("/role-mappings/realm"), any(), eq(Void.class));
    }

    @Test
    @DisplayName("부여할 수 있는 역할에 없거나 조회가 실패하면 역할을 추가하지 않고 API_ERROR 다")
    void assignRealmRole_RoleMissing_ApiError() {
      adminTokenOk();
      given(restTemplate.exchange(endsWith("/role-mappings/realm"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of()));
      given(restTemplate.exchange(endsWith("/available"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of()))
        .willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

      for (int i = 0; i < 2; i++) {
        assertThatThrownBy(() -> keycloakService.assignRealmRole(TEST_KEYCLOAK_ID, "admin"))
          .isInstanceOf(KeycloakException.class)
          .hasMessage(KeycloakResponse.API_ERROR.getMessage());
      }
      verify(restTemplate, never()).postForEntity(contains("/role-mappings/realm"), any(), eq(Void.class));
    }

    @Test
    @DisplayName("관리자 생성은 사용자를 만들고 admin 역할을 넣는다. 역할을 못 넣으면 USER_CREATE_FAILED 다")
    void createAdminUser() {
      adminTokenOk();
      HttpHeaders created = new HttpHeaders();
      created.add("Location", AUTH_SERVER_URL + "/admin/realms/" + REALM + "/users/" + TEST_KEYCLOAK_ID);
      given(restTemplate.postForEntity(contains("/users"), any(HttpEntity.class), eq(Void.class)))
        .willReturn(new ResponseEntity<>(created, HttpStatus.CREATED));
      given(restTemplate.exchange(endsWith("/role-mappings/realm"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of()));
      given(restTemplate.exchange(endsWith("/available"), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of(Map.of("id", "role-1", "name", "admin"))))
        .willReturn(ResponseEntity.ok(List.of()));

      assertThat(keycloakService.createAdminUser("admin@example.com", "Adm1n-pass!")).isEqualTo(TEST_KEYCLOAK_ID);
      assertThatThrownBy(() -> keycloakService.createAdminUser("admin@example.com", "Adm1n-pass!"))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.USER_CREATE_FAILED.getMessage());
    }

    @Test
    @DisplayName("코드 교환 · 토큰 갱신 중 HTTP 오류가 아닌 예외도 정해진 오류로 바꾼다")
    void unexpectedErrors_Mapped() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willThrow(new IllegalStateException("connection reset"));

      assertThatThrownBy(() -> keycloakService.exchangePkceCode("code", "verifier", "https://example.com/callback"))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.API_ERROR.getMessage());
      assertThatThrownBy(() -> keycloakService.refreshToken("old-refresh"))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.TOKEN_REFRESH_FAILED.getMessage());
    }

    @Test
    @DisplayName("payload 를 못 읽는 refresh token 은 confidential client 로 갱신한다")
    void refreshToken_UnreadableToken_UsesClientSecret() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(LoginResponseDto.class)))
        .willReturn(ResponseEntity.ok(new LoginResponseDto("a", "r", 3600, "Bearer", 7200)));

      keycloakService.refreshToken("header.%%%not-base64%%%.signature");

      MultiValueMap<String, String> body = capturedForm(LoginResponseDto.class);
      assertThat(body.getFirst("client_id")).isEqualTo(CLIENT_ID);
      assertThat(body.getFirst("client_secret")).isEqualTo(CLIENT_SECRET);
    }
  }

  @Nested
  @DisplayName("realm role 해제 · 사용자 세션 종료")
  class RealmRoleRemovalTests {

    private final String usersUrl = AUTH_SERVER_URL + "/admin/realms/" + REALM + "/users/" + TEST_KEYCLOAK_ID;
    private final Map<String, Object> adminRole = Map.of("id", "role-1", "name", "admin");

    private void givenAdminToken() {
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(ResponseEntity.ok(Map.of("access_token", "admin_access_token")));
    }

    @Test
    @DisplayName("사용자가 가진 realm 역할에서 그 역할을 찾아 본문에 담고 역할 매핑에서 DELETE 한다")
    void removeRealmRole_DeletesRoleMapping() {
      givenAdminToken();
      given(restTemplate.exchange(eq(usersUrl + "/role-mappings/realm"), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(List.class))).willReturn(ResponseEntity.ok(List.of(adminRole)));

      keycloakService.removeRealmRole(TEST_KEYCLOAK_ID, "admin");

      ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate).exchange(eq(usersUrl + "/role-mappings/realm"), eq(HttpMethod.DELETE), request.capture(),
        eq(Void.class));
      assertThat(request.getValue().getBody()).isEqualTo(List.of(adminRole));
      assertThat(request.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer admin_access_token");
      verify(restTemplate, never()).exchange(contains("/roles/"), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("그 역할이 없는 사용자면 지우지 않는다")
    void removeRealmRole_NotMapped_NoDelete() {
      givenAdminToken();
      given(restTemplate.exchange(eq(usersUrl + "/role-mappings/realm"), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(List.class))).willReturn(ResponseEntity.ok(List.of()));

      keycloakService.removeRealmRole(TEST_KEYCLOAK_ID, "admin");

      verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    @DisplayName("사용자 이름이 정확히 같은 Keycloak 사용자의 ID 를 돌려주고, 없으면 null, 조회가 실패하면 Keycloak 오류다")
    void findUserIdByUsername() {
      givenAdminToken();
      given(restTemplate.exchange(contains("username=found@example.com&exact=true"), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(List.class))).willReturn(ResponseEntity.ok(List.of(Map.of("id", TEST_KEYCLOAK_ID, "username", "found@example.com"))));
      given(restTemplate.exchange(contains("username=none@example.com"), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(List.class))).willReturn(ResponseEntity.ok(List.of()));
      given(restTemplate.exchange(contains("username=down@example.com"), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(List.class))).willThrow(new ResourceAccessException("down"));

      assertThat(keycloakService.findUserIdByUsername("found@example.com")).isEqualTo(TEST_KEYCLOAK_ID);
      assertThat(keycloakService.findUserIdByUsername("none@example.com")).isNull();
      assertThatThrownBy(() -> keycloakService.findUserIdByUsername("down@example.com"))
        .isInstanceOf(KeycloakException.class);
    }

    @Test
    @DisplayName("역할 해제 실패 시 Keycloak 예외가 발생한다")
    void removeRealmRole_Fails_Throws() {
      givenAdminToken();
      given(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
        .willReturn(ResponseEntity.ok(List.of(adminRole)));
      given(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
        .willThrow(new ResourceAccessException("down"));

      assertThatThrownBy(() -> keycloakService.removeRealmRole(TEST_KEYCLOAK_ID, "admin"))
        .isInstanceOf(KeycloakException.class);
    }

    @Test
    @DisplayName("사용자 세션 전체를 종료하는 admin logout 을 POST 한다")
    void logoutUserSessions_PostsUserLogout() {
      givenAdminToken();
      given(restTemplate.postForEntity(eq(usersUrl + "/logout"), any(HttpEntity.class), eq(Void.class)))
        .willReturn(ResponseEntity.noContent().build());

      keycloakService.logoutUserSessions(TEST_KEYCLOAK_ID);

      verify(restTemplate).postForEntity(eq(usersUrl + "/logout"), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    @DisplayName("세션 종료 실패 시 Keycloak 예외가 발생한다")
    void logoutUserSessions_Fails_Throws() {
      givenAdminToken();
      given(restTemplate.postForEntity(eq(usersUrl + "/logout"), any(HttpEntity.class), eq(Void.class)))
        .willThrow(new ResourceAccessException("down"));

      assertThatThrownBy(() -> keycloakService.logoutUserSessions(TEST_KEYCLOAK_ID))
        .isInstanceOf(KeycloakException.class);
    }
  }
}
