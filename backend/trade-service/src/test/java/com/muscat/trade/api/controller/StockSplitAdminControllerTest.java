package com.muscat.trade.api.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.trade.config.security.SecurityConfig;
import com.muscat.trade.domain.service.StockSplitBackfillService;
import com.muscat.trade.domain.service.StockSplitBackfillService.Result;
import com.muscat.trade.domain.service.StockSplitBackfillService.Status;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(StockSplitAdminController.class)
@ContextConfiguration(classes = {StockSplitAdminController.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("분할 관리자 경로")
class StockSplitAdminControllerTest {

  private static final String BACKFILL = "/api/admin/trade/splits/backfill";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private StockSplitBackfillService stockSplitBackfillService;

  // Keycloak realm 역할 admin 이 권한 admin 으로 바뀐 토큰
  private static RequestPostProcessor admin() {
    return jwt().authorities(new SimpleGrantedAuthority("admin"));
  }

  @Test
  @DisplayName("토큰이 없으면 401, admin 권한이 없으면 403, 서비스 호출 없음")
  void requiresAdminAuthority() throws Exception {
    mockMvc.perform(post(BACKFILL)).andExpect(status().isUnauthorized());
    mockMvc.perform(post(BACKFILL).with(jwt())).andExpect(status().isForbidden());
    mockMvc.perform(get(BACKFILL).with(jwt())).andExpect(status().isForbidden());

    verifyNoInteractions(stockSplitBackfillService);
  }

  @Test
  @DisplayName("지난 분할 백필 시작은 202 와 진행 중 상태 · 마지막 결과")
  void backfill_StartsInBackground() throws Exception {
    given(stockSplitBackfillService.start())
      .willReturn(new Status(true, new Result(5, 1, 2, LocalDateTime.of(2026, 11, 12, 12, 0))));

    mockMvc.perform(post(BACKFILL).with(admin()))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.running").value(true))
      .andExpect(jsonPath("$.last.changed").value(5))
      .andExpect(jsonPath("$.last.failed").value(1))
      .andExpect(jsonPath("$.last.reconcileFailed").value(2));
  }

  @Test
  @DisplayName("지난 분할 백필 상태는 진행 중 여부와 마지막 결과(아직 없으면 빈 값)")
  void backfillStatus_ReturnsRunningAndLast() throws Exception {
    given(stockSplitBackfillService.status()).willReturn(new Status(false, null));

    mockMvc.perform(get(BACKFILL).with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.running").value(false))
      .andExpect(jsonPath("$.last").isEmpty());
  }
}
