package com.muscat.user.domain.user.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.exception.GlobalExceptionHandler;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.domain.user.dto.request.DeleteAccountRequestDto;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 회원 탈퇴")
class UserControllerDeleteTest {

  @Mock
  private UserService userService;

  @Mock
  private UserMapper userMapper;

  private MockMvc mockMvc;

  // Keycloak 은 auth_time 을 초 단위 숫자로 준다
  private static final long AUTH_TIME = 1_760_000_000L;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, userMapper))
      .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
      .setControllerAdvice(new GlobalExceptionHandler())
      .build();
    Jwt jwt = Jwt.withTokenValue("t")
      .header("alg", "none")
      .subject("kc-1")
      .claim("email", "u@example.local")
      .claim("auth_time", AUTH_TIME)
      .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("본문 없이 보내도 받는다(구글 사용자). 비밀번호 없이 토큰의 로그인 시각을 넘긴다")
  void delete_WithoutBody_PassesAuthTime() throws Exception {
    mockMvc.perform(delete("/api/users/me"))
      .andExpect(status().isOk());

    verify(userService).deleteAccount("kc-1", null, Instant.ofEpochSecond(AUTH_TIME));
  }

  @Test
  @DisplayName("비밀번호를 보내면 그대로 넘긴다")
  void delete_WithPassword_PassesPassword() throws Exception {
    String typed = UUID.randomUUID().toString();
    DeleteAccountRequestDto body = new DeleteAccountRequestDto();
    body.setPassword(typed);

    mockMvc.perform(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(body)))
      .andExpect(status().isOk());

    verify(userService).deleteAccount("kc-1", typed, Instant.ofEpochSecond(AUTH_TIME));
  }

  @Test
  @DisplayName("다시 로그인이 필요하면 403 이다(401 이면 프론트가 갱신 뒤 같은 요청을 되풀이한다)")
  void delete_ReauthRequired_Returns403() throws Exception {
    willThrow(new UserException(UserResponse.REAUTH_REQUIRED))
      .given(userService).deleteAccount("kc-1", null, Instant.ofEpochSecond(AUTH_TIME));

    mockMvc.perform(delete("/api/users/me"))
      .andExpect(status().isForbidden());
  }
}
