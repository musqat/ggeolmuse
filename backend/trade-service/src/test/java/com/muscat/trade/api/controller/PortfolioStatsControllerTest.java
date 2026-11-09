package com.muscat.trade.api.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.FlowPeriod;
import com.muscat.trade.common.exception.GlobalExceptionHandler;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.config.security.SecurityConfig;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.service.ProfitFlowService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PortfolioStatsController.class)
@ContextConfiguration(classes = {PortfolioStatsController.class, SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
@DisplayName("포트폴리오 통계 경로")
class PortfolioStatsControllerTest {

  private static final String FLOW = "/api/portfolio/stats/flow";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ProfitFlowService profitFlowService;

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({"1M, ONE_MONTH", "3M, THREE_MONTHS", "1Y, ONE_YEAR", "ALL, ALL"})
  @DisplayName("기간 값을 받아 토큰 주인의 수익 흐름 · 최고 · 최저 · 현재 수익을 돌려준다")
  void flow_ReturnsSummary(String code, FlowPeriod period) throws Exception {
    LocalDate peak = LocalDate.of(2024, 1, 2);
    LocalDate trough = LocalDate.of(2024, 1, 3);
    given(profitFlowService.flow("user-1", "all", period)).willReturn(new ProfitFlowResponse(
      List.of(new ProfitPoint(peak, new BigDecimal("300"), new BigDecimal("1300000")),
        new ProfitPoint(trough, new BigDecimal("-100"), new BigDecimal("1310000"))),
      new ProfitPoint(peak, new BigDecimal("300"), new BigDecimal("1300000")),
      new ProfitPoint(trough, new BigDecimal("-100"), new BigDecimal("1310000")),
      new BigDecimal("-100")));

    mockMvc.perform(get(FLOW).param("accountId", "all").param("period", code).with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.points[0].date").value("2024-01-02"))
      .andExpect(jsonPath("$.points[1].profitKrw").value(-100))
      .andExpect(jsonPath("$.points[1].investedKrw").value(1310000))
      .andExpect(jsonPath("$.best.profitKrw").value(300))
      .andExpect(jsonPath("$.best.investedKrw").value(1300000))
      .andExpect(jsonPath("$.worst.date").value("2024-01-03"))
      .andExpect(jsonPath("$.currentProfitKrw").value(-100));
  }

  @Test
  @DisplayName("기간 값이 1M · 3M · 1Y · ALL 이 아니거나 빠지면 400 이고 서비스까지 가지 않는다")
  void flow_InvalidPeriod_BadRequest() throws Exception {
    mockMvc.perform(get(FLOW).param("accountId", "all").param("period", "2W").with(jwt()))
      .andExpect(status().isBadRequest());
    mockMvc.perform(get(FLOW).param("accountId", "all").param("period", "1m").with(jwt()))
      .andExpect(status().isBadRequest());
    mockMvc.perform(get(FLOW).param("accountId", "all").with(jwt()))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(profitFlowService);
  }

  @Test
  @DisplayName("토큰이 없으면 401 이다")
  void flow_NoToken_Unauthorized() throws Exception {
    mockMvc.perform(get(FLOW).param("accountId", "all").param("period", "1M"))
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(profitFlowService);
  }

  @Test
  @DisplayName("남의 계좌면 403 이다")
  void flow_OtherUsersAccount_Forbidden() throws Exception {
    given(profitFlowService.flow("user-1", "7", FlowPeriod.ALL))
      .willThrow(new TradeException(TradeResponse.ACCOUNT_ACCESS_DENIED));

    mockMvc.perform(get(FLOW).param("accountId", "7").param("period", "ALL").with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isForbidden());
  }
}
