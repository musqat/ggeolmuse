package com.muscat.user.domain.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.exception.GlobalExceptionHandler;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.config.security.SecurityConfig;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.service.UserService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 가입 · 이메일 인증 · 비밀번호 재설정은 토큰 없이 부르는 경로라 토큰 없이 친다
@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = {AuthController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("AuthController 가입 · 이메일 인증 · 비밀번호 재설정")
class AuthControllerRequestTest {

  private static final String BASE = "/api/auth";
  private static final String PASSWORD = "Passw0rd!";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private RefreshTokenCookie refreshTokenCookie;

  private static MockHttpServletRequestBuilder json(String path, String body) {
    return post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @Test
  @DisplayName("가입은 토큰 없이 201 이고 입력값으로 사용자를 만든다")
  void register_Created() throws Exception {
    given(userService.registerUser("user@example.com", PASSWORD, "투자자"))
      .willReturn(User.builder().email("user@example.com").build());

    mockMvc.perform(json("/register", """
        {"email":"user@example.com","password":"%s","nickname":"투자자"}
        """.formatted(PASSWORD)))
      .andExpect(status().isCreated());

    verify(userService).registerUser("user@example.com", PASSWORD, "투자자");
  }

  @Test
  @DisplayName("가입 비밀번호에 특수문자가 없거나 이메일 형식이 틀리면 400 이고 만들지 않는다")
  void register_Invalid_BadRequest() throws Exception {
    mockMvc.perform(json("/register", """
        {"email":"user@example.com","password":"Passw0rdd","nickname":"투자자"}
        """))
      .andExpect(status().isBadRequest());
    mockMvc.perform(json("/register", """
        {"email":"not-an-email","password":"%s","nickname":"투자자"}
        """.formatted(PASSWORD)))
      .andExpect(status().isBadRequest());

    verify(userService, never()).registerUser(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("이메일 인증은 성공하면 200, 토큰이 틀리면 400 으로 HTML 을 돌려준다")
  void verifyEmail_Html() throws Exception {
    given(userService.verifyEmail("good")).willReturn(User.builder().email("user@example.com").build());
    given(userService.verifyEmail("bad")).willThrow(new UserException(UserResponse.EMAIL_TOKEN_INVALID));

    mockMvc.perform(get(BASE + "/verify-email").param("token", "good"))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
      .andExpect(content().string(Matchers.containsString("이메일 인증이 완료되었습니다")));
    mockMvc.perform(get(BASE + "/verify-email").param("token", "bad"))
      .andExpect(status().isBadRequest())
      .andExpect(content().string(Matchers.containsString("인증에 실패했습니다")));
  }

  @Test
  @DisplayName("인증 메일 재발송 · 비밀번호 재설정 메일 · 재설정은 입력값으로 부른다")
  void resendForgotReset() throws Exception {
    mockMvc.perform(json("/resend-verification", """
        {"email":"user@example.com"}
        """))
      .andExpect(status().isOk());
    mockMvc.perform(json("/forgot-password", """
        {"email":"user@example.com"}
        """))
      .andExpect(status().isOk());
    mockMvc.perform(json("/reset-password", """
        {"token":"reset-token","newPassword":"%s"}
        """.formatted(PASSWORD)))
      .andExpect(status().isOk());

    verify(userService).resendVerificationEmail("user@example.com");
    verify(userService).requestPasswordReset("user@example.com");
    verify(userService).resetPassword("reset-token", PASSWORD);
  }

  @Test
  @DisplayName("재설정 비밀번호가 8자 미만이면 400 이고 바꾸지 않는다")
  void resetPassword_TooShort_BadRequest() throws Exception {
    mockMvc.perform(json("/reset-password", """
        {"token":"reset-token","newPassword":"Pa0!"}
        """))
      .andExpect(status().isBadRequest());

    verify(userService, never()).resetPassword(anyString(), anyString());
  }
}
