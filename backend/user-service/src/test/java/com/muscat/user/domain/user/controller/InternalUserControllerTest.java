package com.muscat.user.domain.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.commonlib.security.ServiceSecurity;
import com.muscat.user.common.exception.GlobalExceptionHandler;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(InternalUserController.class)
@ContextConfiguration(classes = {InternalUserController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("InternalUserController 요청 · 응답")
class InternalUserControllerTest {

  private static final String ADMIN_ROLE = "/api/internal/users/admin-role";
  private static final RequestPostProcessor SERVICE =
    jwt().authorities(new SimpleGrantedAuthority(ServiceSecurity.SERVICE_ROLE));

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthUtil authUtil;

  @Test
  @DisplayName("토큰이 없으면 401, 서비스 역할이 없는 관리자 토큰도 403 이고 역할을 찾지 않는다")
  void withoutServiceToken_Rejected() throws Exception {
    mockMvc.perform(get(ADMIN_ROLE).param("keycloakId", "kc-1")).andExpect(status().isUnauthorized());
    mockMvc.perform(get(ADMIN_ROLE).param("keycloakId", "kc-1")
        .with(jwt().authorities(new SimpleGrantedAuthority("admin"))))
      .andExpect(status().isForbidden());

    verify(authUtil, never()).isActiveAdmin(anyString());
  }

  @Test
  @DisplayName("Keycloak ID 가 없으면 400 이고 역할을 찾지 않는다")
  void missingKeycloakId_BadRequest() throws Exception {
    mockMvc.perform(get(ADMIN_ROLE).with(SERVICE)).andExpect(status().isBadRequest());

    verify(authUtil, never()).isActiveAdmin(anyString());
  }

  @Test
  @DisplayName("받은 Keycloak ID 사용자의 관리자 여부를 admin 칸으로 돌려준다")
  void adminRole_OfGivenUser() throws Exception {
    given(authUtil.isActiveAdmin("kc-1")).willReturn(true);
    given(authUtil.isActiveAdmin("kc-2")).willReturn(false);

    mockMvc.perform(get(ADMIN_ROLE).param("keycloakId", "kc-1").with(SERVICE))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.admin").value(true));
    mockMvc.perform(get(ADMIN_ROLE).param("keycloakId", "kc-2").with(SERVICE))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.admin").value(false));
  }
}
