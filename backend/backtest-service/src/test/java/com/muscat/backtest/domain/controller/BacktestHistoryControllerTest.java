package com.muscat.backtest.domain.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.exception.GlobalExceptionHandler;
import com.muscat.backtest.config.SecurityConfig;
import com.muscat.backtest.domain.dto.response.BacktestHistoryDto;
import com.muscat.backtest.domain.service.BacktestHistoryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BacktestHistoryController.class)
@ContextConfiguration(classes = {BacktestHistoryController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("BacktestHistoryController 요청 · 응답")
class BacktestHistoryControllerTest {

  private static final String BASE = "/api/backtest/history";
  private static final String TOKEN = "good-token";
  private static final String EMAIL = "user@example.com";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private BacktestHistoryService backtestHistoryService;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void decodeToken() {
    given(jwtDecoder.decode(TOKEN)).willReturn(Jwt.withTokenValue(TOKEN).header("alg", "none").subject("user-1")
      .claim("email", EMAIL).build());
  }

  private static BacktestHistoryDto history() {
    return new BacktestHistoryDto(1L, EMAIL, BacktestType.COMPARISON, "{}", "auto", LocalDateTime.of(2024, 5, 1, 9, 30));
  }

  @Test
  @DisplayName("토큰이 없으면 401 이고 서비스까지 가지 않는다")
  void requiresToken() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());

    verify(backtestHistoryService, never()).getUserBacktestHistory(anyString(), any());
  }

  @Test
  @DisplayName("페이지 번호와 크기를 넘기고 이력을 돌려준다")
  void paged() throws Exception {
    given(backtestHistoryService.getUserBacktestHistory(eq(EMAIL), any()))
      .willReturn(new PageImpl<>(List.of(history()), PageRequest.of(2, 5), 11));

    mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .param("page", "2").param("size", "5"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].backtestType").value("COMPARISON"));

    verify(backtestHistoryService).getUserBacktestHistory(EMAIL, PageRequest.of(2, 5));
  }

  @Test
  @DisplayName("페이지를 주지 않으면 첫 페이지 20건이다")
  void defaultPage() throws Exception {
    given(backtestHistoryService.getUserBacktestHistory(eq(EMAIL), any())).willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
      .andExpect(status().isOk());

    verify(backtestHistoryService).getUserBacktestHistory(EMAIL, PageRequest.of(0, 20));
  }

  @Test
  @DisplayName("쿼리에 다른 사람의 userId 를 넣어도 토큰의 이메일로 조회한다")
  void ignoresQueryUser() throws Exception {
    given(backtestHistoryService.getUserBacktestHistory(eq(EMAIL), any())).willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN).param("userId", "other@example.com"))
      .andExpect(status().isOk());

    verify(backtestHistoryService).getUserBacktestHistory(EMAIL, PageRequest.of(0, 20));
    verify(backtestHistoryService, never()).getUserBacktestHistory(eq("other@example.com"), any());
  }

  @Test
  @DisplayName("토큰에 이메일이 없으면 빈 목록이고 서비스까지 가지 않는다")
  void noEmailClaim() throws Exception {
    given(jwtDecoder.decode("no-email")).willReturn(Jwt.withTokenValue("no-email").header("alg", "none").subject("svc")
      .build());

    mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer no-email"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content").isEmpty());

    verify(backtestHistoryService, never()).getUserBacktestHistory(any(), any());
  }
}
