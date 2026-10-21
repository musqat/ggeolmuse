package com.muscat.trade.api.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.trade.config.security.SecurityConfig;
import com.muscat.trade.domain.dto.response.PayoutDiffResponse;
import com.muscat.trade.domain.service.DividendBackfillService;
import java.math.BigDecimal;
import java.util.List;
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

@WebMvcTest(DividendAdminController.class)
@ContextConfiguration(classes = {DividendAdminController.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("배당 몫 관리자 경로")
class DividendAdminControllerTest {

  private static final String BASE = "/api/admin/trade/dividends";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DividendBackfillService dividendBackfillService;

  // Keycloak realm 역할 admin 이 권한 admin 으로 바뀐 토큰
  private static RequestPostProcessor admin() {
    return jwt().authorities(new SimpleGrantedAuthority("admin"));
  }

  @Test
  @DisplayName("토큰이 없으면 401, admin 권한이 없으면 403 이고 서비스까지 가지 않는다")
  void requiresAdminAuthority() throws Exception {
    mockMvc.perform(get(BASE + "/payout-diff")).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/payout-diff").with(jwt())).andExpect(status().isForbidden());
    mockMvc.perform(post(BASE + "/reconcile").with(jwt())).andExpect(status().isForbidden());

    verifyNoInteractions(dividendBackfillService);
  }

  @Test
  @DisplayName("백필은 재계산을 요청한 자리 수를 돌려준다")
  void reconcileAll_ReturnsRequestedCount() throws Exception {
    given(dividendBackfillService.requestAll()).willReturn(3);

    mockMvc.perform(post(BASE + "/reconcile").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.requested").value(3));
  }

  @Test
  @DisplayName("미리 보기는 계좌별로 넣은 배당 · 받을 몫 · 차이를 돌려준다")
  void payoutDiff_ReturnsRows() throws Exception {
    given(dividendBackfillService.diff()).willReturn(List.of(new PayoutDiffResponse(
      "user-1", 20L, new BigDecimal("0"), new BigDecimal("2.03"), new BigDecimal("2.03"), 1)));

    mockMvc.perform(get(BASE + "/payout-diff").with(admin()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].accountId").value(20))
      .andExpect(jsonPath("$[0].target").value(2.03))
      .andExpect(jsonPath("$[0].delta").value(2.03))
      .andExpect(jsonPath("$[0].differingRows").value(1));
  }
}
