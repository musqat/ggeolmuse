package com.muscat.user.domain.user.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.user.common.enums.response.KeycloakResponse;
import com.muscat.user.common.exception.GlobalExceptionHandler;
import com.muscat.user.common.exception.KeycloakException;
import com.muscat.user.config.AppProperties;
import com.muscat.user.domain.user.dto.response.TokenResponseDto;
import com.muscat.user.domain.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 토큰 · 쿠키")
class AuthControllerTokenTest {

  @Mock
  private UserService userService;

  private MockMvc mockMvc;

  private static final TokenResponseDto TOKENS = new TokenResponseDto("access-1", "refresh-1", 3600, 7200);

  @BeforeEach
  void setUp() {
    RefreshTokenCookie cookie = new RefreshTokenCookie(new AppProperties());
    AuthController controller = new AuthController(userService, cookie);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
      .setControllerAdvice(new GlobalExceptionHandler(new MockEnvironment()))
      .build();
  }

  @Test
  @DisplayName("로그인은 access token 을 본문으로, refresh token 을 HttpOnly · Secure · SameSite=Strict 쿠키로 준다")
  void login_SetsRefreshCookieAndHidesRefreshInBody() throws Exception {
    given(userService.login("u@example.local", "pw-1")).willReturn(TOKENS);

    mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"u@example.local\",\"password\":\"pw-1\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").value("access-1"))
      .andExpect(jsonPath("$.expiresIn").value(3600))
      .andExpect(jsonPath("$.refreshToken").doesNotExist())
      .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
        containsString("refresh_token=refresh-1"),
        containsString("Path=/api/auth"),
        containsString("Max-Age=7200"),
        containsString("HttpOnly"),
        containsString("Secure"),
        containsString("SameSite=Strict"))));
  }

  @Test
  @DisplayName("갱신은 쿠키의 refresh token 을 쓰고, 쿠키를 새 refresh token 으로 바꾼다")
  void refresh_UsesCookieAndRotatesIt() throws Exception {
    given(userService.refreshToken("old-refresh")).willReturn(new TokenResponseDto("access-2", "new-refresh", 3600, 7200));

    mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, "old-refresh")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").value("access-2"))
      .andExpect(jsonPath("$.refreshToken").doesNotExist())
      .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=new-refresh")));
  }

  @Test
  @DisplayName("쿠키가 없으면 갱신은 401")
  void refresh_WithoutCookie_401() throws Exception {
    mockMvc.perform(post("/api/auth/refresh"))
      .andExpect(status().isUnauthorized());

    verify(userService, never()).refreshToken(anyString());
  }

  @Test
  @DisplayName("로그아웃은 쿠키의 세션을 끊고 쿠키를 지운다")
  void logout_EndsSessionAndClearsCookie() throws Exception {
    mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, "refresh-1")))
      .andExpect(status().isNoContent())
      .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString("refresh_token="), containsString("Max-Age=0"))));

    verify(userService).logout("refresh-1");
  }

  @Test
  @DisplayName("쿠키가 없어도 로그아웃은 204 이고 쿠키를 지운다")
  void logout_WithoutCookie_StillClears() throws Exception {
    mockMvc.perform(post("/api/auth/logout"))
      .andExpect(status().isNoContent())
      .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    verify(userService, never()).logout(anyString());
  }

  @Test
  @DisplayName("Keycloak 세션 종료가 실패해도 쿠키는 지운다")
  void logout_KeycloakFailure_StillClears() throws Exception {
    willThrow(new KeycloakException(KeycloakResponse.API_ERROR)).given(userService).logout("refresh-1");

    mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, "refresh-1")))
      .andExpect(status().isNoContent())
      .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
  }

  @Test
  @DisplayName("구글 인가 코드를 바꾸면 access token 은 본문, refresh token 은 쿠키로 준다")
  void exchangeGoogleCode_SetsCookie() throws Exception {
    given(userService.exchangeGoogleCode("code-1", "verifier-1", "https://app.example/oauth/callback")).willReturn(TOKENS);

    mockMvc.perform(post("/api/auth/social/google/token").contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"code-1\",\"codeVerifier\":\"verifier-1\",\"redirectUri\":\"https://app.example/oauth/callback\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").value("access-1"))
      .andExpect(jsonPath("$.refreshToken").doesNotExist())
      .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString("refresh_token=refresh-1"), containsString("HttpOnly"))));
  }

  @Test
  @DisplayName("구글 교환 요청에 code_verifier 가 비면 400 이고 교환하지 않는다")
  void exchangeGoogleCode_MissingVerifier_400() throws Exception {
    mockMvc.perform(post("/api/auth/social/google/token").contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"code-1\",\"codeVerifier\":\"\",\"redirectUri\":\"https://app.example/oauth/callback\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    verify(userService, never()).exchangeGoogleCode(anyString(), anyString(), anyString());
  }
}
