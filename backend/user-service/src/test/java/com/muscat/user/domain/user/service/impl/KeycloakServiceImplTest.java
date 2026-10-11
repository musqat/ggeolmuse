package com.muscat.user.domain.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.muscat.user.domain.user.dto.request.ChangePasswordRequestDto;
import com.muscat.user.domain.user.dto.response.LoginResponseDto;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeycloakService 단위 테스트")
class KeycloakServiceImplTest {

  @Mock
  private RestTemplate restTemplate;

  @Mock
  private JwtDecoder jwtDecoder;

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
  private static final String REDIRECT_URI = "http://localhost:3000/callback";

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

    // AppProperties 설정
    appProperties = new AppProperties();
    AppProperties.Oauth oauth = new AppProperties.Oauth();
    oauth.setRedirectUri(REDIRECT_URI);
    appProperties.setOauth(oauth);

    // KeycloakService 인스턴스 생성
    keycloakService = new KeycloakServiceImpl(keycloakProperties, appProperties, jwtDecoder);

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
  @DisplayName("Authorization Code 교환 테스트")
  class ExchangeCodeForTokenTests {

    @Test
    @DisplayName("정상적으로 Authorization Code를 토큰으로 교환한다")
    void exchangeCodeForToken_Success() {
      // given
      String authCode = "test_auth_code";
      Map<String, Object> tokenResponse = new HashMap<>();
      tokenResponse.put("access_token", TEST_TOKEN);
      tokenResponse.put("token_type", "Bearer");
      tokenResponse.put("expires_in", 300);

      ResponseEntity<Map> responseEntity = ResponseEntity.ok(tokenResponse);
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(responseEntity);

      // when
      String accessToken = keycloakService.exchangeCodeForToken(authCode);

      // then
      assertThat(accessToken).isEqualTo(TEST_TOKEN);
      verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("응답 body가 null이면 예외가 발생한다")
    void exchangeCodeForToken_NullResponse_ThrowsException() {
      // given
      String authCode = "test_auth_code";
      ResponseEntity<Map> responseEntity = ResponseEntity.ok(null);
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(responseEntity);

      // when & then
      assertThatThrownBy(() -> keycloakService.exchangeCodeForToken(authCode))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.API_ERROR.getMessage());
    }

    @Test
    @DisplayName("access_token이 없으면 예외가 발생한다")
    void exchangeCodeForToken_NoAccessToken_ThrowsException() {
      // given
      String authCode = "test_auth_code";
      Map<String, Object> tokenResponse = new HashMap<>();
      tokenResponse.put("token_type", "Bearer");

      ResponseEntity<Map> responseEntity = ResponseEntity.ok(tokenResponse);
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(responseEntity);

      // when & then
      assertThatThrownBy(() -> keycloakService.exchangeCodeForToken(authCode))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.API_ERROR.getMessage());
    }

    @Test
    @DisplayName("access_token이 빈 문자열이면 예외가 발생한다")
    void exchangeCodeForToken_EmptyAccessToken_ThrowsException() {
      // given
      String authCode = "test_auth_code";
      Map<String, Object> tokenResponse = new HashMap<>();
      tokenResponse.put("access_token", "   ");

      ResponseEntity<Map> responseEntity = ResponseEntity.ok(tokenResponse);
      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(responseEntity);

      // when & then
      assertThatThrownBy(() -> keycloakService.exchangeCodeForToken(authCode))
        .isInstanceOf(KeycloakException.class)
        .hasMessage(KeycloakResponse.API_ERROR.getMessage());
    }
  }

  @Nested
  @DisplayName("JWT 토큰 파싱 테스트")
  class ParseTokenClaimsTests {

    @Test
    @DisplayName("정상적으로 JWT 토큰을 파싱한다")
    void parseTokenClaims_Success() {
      // given
      Map<String, Object> claims = new HashMap<>();
      claims.put("sub", TEST_KEYCLOAK_ID);
      claims.put("email", TEST_EMAIL);
      claims.put("name", "Test User");

      Jwt jwt = Jwt.withTokenValue(TEST_TOKEN)
        .header("alg", "RS256")
        .claim("sub", TEST_KEYCLOAK_ID)
        .claim("email", TEST_EMAIL)
        .claim("name", "Test User")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build();

      given(jwtDecoder.decode(TEST_TOKEN)).willReturn(jwt);

      // when
      Map<String, Object> result = keycloakService.parseTokenClaims(TEST_TOKEN);

      // then
      assertThat(result).isNotNull();
      assertThat(result.get("email")).isEqualTo(TEST_EMAIL);
      assertThat(result.get("sub")).isEqualTo(TEST_KEYCLOAK_ID);
      verify(jwtDecoder).decode(TEST_TOKEN);
    }

    @Test
    @DisplayName("잘못된 JWT 토큰이면 예외가 발생한다")
    void parseTokenClaims_InvalidToken_ThrowsException() {
      // given
      String invalidToken = "invalid.jwt.token";
      given(jwtDecoder.decode(invalidToken))
        .willThrow(new org.springframework.security.oauth2.jwt.JwtException("Invalid JWT"));

      // when & then
      assertThatThrownBy(() -> keycloakService.parseTokenClaims(invalidToken))
        .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
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
  @DisplayName("비밀번호 변경 테스트")
  class ChangePasswordTests {

    @Test
    @DisplayName("정상적으로 비밀번호가 변경된다")
    void changePassword_Success() {
      // given
      ChangePasswordRequestDto request = new ChangePasswordRequestDto();
      request.setNewPassword("new_password");

      Map<String, Object> adminTokenResponse = new HashMap<>();
      adminTokenResponse.put("access_token", "admin_access_token");
      ResponseEntity<Map> adminTokenEntity = ResponseEntity.ok(adminTokenResponse);

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willReturn(adminTokenEntity);
      doNothing().when(restTemplate).put(anyString(), any(HttpEntity.class));

      // when
      keycloakService.changePassword(TEST_KEYCLOAK_ID, request);

      // then
      verify(restTemplate).put(anyString(), any(HttpEntity.class));
    }

    @Test
    @DisplayName("Admin 토큰 획득 실패 시 예외가 발생한다")
    void changePassword_AdminTokenFailed_ThrowsException() {
      // given
      ChangePasswordRequestDto request = new ChangePasswordRequestDto();
      request.setNewPassword("new_password");

      given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
        .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

      // when & then
      assertThatThrownBy(() -> keycloakService.changePassword(TEST_KEYCLOAK_ID, request))
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
}
