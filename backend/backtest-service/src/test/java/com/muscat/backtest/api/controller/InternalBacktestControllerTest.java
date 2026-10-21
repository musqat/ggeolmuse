package com.muscat.backtest.api.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.backtest.common.exception.GlobalExceptionHandler;
import com.muscat.backtest.config.SecurityConfig;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.domain.service.InvestmentBacktestService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalBacktestController.class)
@ContextConfiguration(classes = {InternalBacktestController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("InternalBacktestController 요청 · 응답")
class InternalBacktestControllerTest {

  private static final String PATH = "/api/internal/backtest/investment-result/user-1";
  private static final String TOKEN = "good-token";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private InvestmentBacktestService investmentBacktestService;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void decodeToken() {
    given(jwtDecoder.decode(TOKEN)).willReturn(Jwt.withTokenValue(TOKEN).header("alg", "none").subject("user-1").build());
  }

  @Test
  @DisplayName("열린 내부 경로는 시뮬레이션 · 분석뿐이라 이 경로는 토큰이 필요하다. trade 가 사용자 토큰을 실어 부른다")
  void requiresToken() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

    verify(investmentBacktestService, never()).getCachedInvestmentResultEntity(anyString());
  }

  @Test
  @DisplayName("캐시가 있으면 사용자 · 결과 · 계산 시각을 돌려준다")
  void cached() throws Exception {
    given(investmentBacktestService.getCachedInvestmentResultEntity("user-1")).willReturn(Optional.of(
      InvestmentBacktestResult.builder().userId("user-1").resultData("{\"status\":\"SUCCESS\"}")
        .calculatedAt(LocalDateTime.of(2024, 5, 1, 9, 30)).build()));

    mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.userId").value("user-1"))
      .andExpect(jsonPath("$.backtestResult").value("{\"status\":\"SUCCESS\"}"))
      .andExpect(jsonPath("$.calculatedAt").value("2024-05-01T09:30:00"));
  }

  @Test
  @DisplayName("캐시가 없으면 본문 없이 204")
  void notCached() throws Exception {
    given(investmentBacktestService.getCachedInvestmentResultEntity("user-1")).willReturn(Optional.empty());

    mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
      .andExpect(status().isNoContent())
      .andExpect(content().string(""));
  }
}
