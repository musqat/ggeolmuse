package com.muscat.user.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.user.common.exceptions.GlobalExceptionHandler;
import com.muscat.user.config.security.SecurityConfig;
import com.muscat.user.domain.user.dto.response.UserResponseDto;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.domain.user.service.UserService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = {UserController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("UserController 프로필 수정 · 비밀번호 변경")
class UserControllerRequestTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private KeycloakService keycloakService;

  @MockitoBean
  private UserMapper userMapper;

  private static RequestPostProcessor me() {
    return jwt().jwt(j -> j.subject("kc-1").claim("email", "user@example.com"));
  }

  private static MockHttpServletRequestBuilder json(String path, String body) {
    return put(path).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @Test
  @DisplayName("토큰이 없으면 401 이고 바꾸지 않는다")
  void noToken_Unauthorized() throws Exception {
    mockMvc.perform(json("/api/users/me", """
        {"nickname":"새이름"}
        """))
      .andExpect(status().isUnauthorized());

    verify(userService, never()).updateProfile(anyString(), any());
  }

  @Test
  @DisplayName("프로필 수정은 토큰의 이메일로 바꾸고 바뀐 프로필을 돌려준다")
  void updateProfile_UsesTokenEmail() throws Exception {
    User updated = User.builder().email("user@example.com").nickname("새이름").build();
    given(userService.updateProfile(eq("user@example.com"), any())).willReturn(updated);
    UserResponseDto dto = new UserResponseDto(1L, "user@example.com", "새이름", null, null, "LOCAL", true, null, null, true);
    given(userMapper.toResponseDto(updated)).willReturn(dto);

    mockMvc.perform(json("/api/users/me", """
        {"nickname":"새이름"}
        """).with(me()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nickname").value("새이름"));

    verify(userService).updateProfile(eq("user@example.com"), argThat(r -> "새이름".equals(r.getNickname())));
  }

  @Test
  @DisplayName("닉네임에 공백 · 기호가 있으면 400 이다")
  void updateProfile_InvalidNickname_BadRequest() throws Exception {
    mockMvc.perform(json("/api/users/me", """
        {"nickname":"새 이름!"}
        """).with(me()))
      .andExpect(status().isBadRequest());

    verify(userService, never()).updateProfile(anyString(), any());
  }

  @Test
  @DisplayName("비밀번호 변경은 토큰의 Keycloak id 로 바꾸고, 형식이 틀리면 400 이다")
  void changePassword() throws Exception {
    mockMvc.perform(json("/api/users/me/password", """
        {"newPassword":"Passw0rd!"}
        """).with(me()))
      .andExpect(status().isOk());
    mockMvc.perform(json("/api/users/me/password", """
        {"newPassword":"password"}
        """).with(me()))
      .andExpect(status().isBadRequest());

    verify(keycloakService).changePassword(eq("kc-1"), argThat(r -> "Passw0rd!".equals(r.getNewPassword())));
  }
}
