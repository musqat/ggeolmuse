package com.muscat.trade.api.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.trade.config.security.SecurityConfig;
import com.muscat.trade.domain.service.HoldingsBackfillService;
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

@WebMvcTest(HoldingsAdminController.class)
@ContextConfiguration(classes = {HoldingsAdminController.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("보유 관리자 경로")
class HoldingsAdminControllerTest {

  private static final String BASE = "/api/admin/trade/holdings";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private HoldingsBackfillService holdingsBackfillService;

  // Keycloak realm 역할 admin 이 권한 admin 으로 바뀐 토큰
  private static RequestPostProcessor admin() {
    return jwt().authorities(new SimpleGrantedAuthority("admin"));
  }

  @Test
  @DisplayName("토큰이 없으면 401, admin 권한이 없으면 403, 서비스 호출 없음")
  void requiresAdminAuthority() throws Exception {
    mockMvc.perform(post(BASE + "/rebuild")).andExpect(status().isUnauthorized());
    mockMvc.perform(post(BASE + "/rebuild").with(jwt())).andExpect(status().isForbidden());

    verifyNoInteractions(holdingsBackfillService);
  }

  @Test
  @DisplayName("보유 다시 쌓기 응답은 바뀐 보유 행 수")
  void rebuildAll_ReturnsChangedCount() throws Exception {
    given(holdingsBackfillService.rebuildAll()).willReturn(2);

    mockMvc.perform(post(BASE + "/rebuild").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.changed").value(2));
  }
}
