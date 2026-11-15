package com.muscat.trade.api.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.trade.common.exception.GlobalExceptionHandler;
import com.muscat.trade.config.security.SecurityConfig;
import com.muscat.trade.domain.service.HoldingsService;
import com.muscat.trade.domain.service.TradingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 거래 내역 크기 · 계좌 ID 처럼 경로 · 쿼리 · 본문 값이 잘못된 요청의 응답
@WebMvcTest({TradeApiController.class, PortfolioController.class, TradingController.class})
@ContextConfiguration(classes = {TradeApiController.class, PortfolioController.class, TradingController.class,
  SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
@DisplayName("요청 값 범위")
class RequestParamsWebTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TradingService tradingService;

  @MockitoBean
  private HoldingsService holdingsService;

  @ParameterizedTest(name = "page={0} size={1}")
  @CsvSource({"0, 10000", "0, 101", "0, 0", "-1, 20"})
  @DisplayName("거래 내역 크기가 1 ~ 100 밖이거나 페이지가 음수면 400 이고 서비스까지 가지 않는다")
  void tradeHistory_OutOfRange(String page, String size) throws Exception {
    mockMvc.perform(get("/api/trade-history/history").param("page", page).param("size", size).with(jwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));

    verifyNoInteractions(tradingService);
  }

  @Test
  @DisplayName("거래 내역 크기 100 은 그대로 넘긴다")
  void tradeHistory_MaxSize() throws Exception {
    given(tradingService.getUserTrades(anyString(), anyInt(), anyInt())).willReturn(List.of());

    mockMvc.perform(get("/api/trade-history/history").param("page", "3").param("size", "100")
        .with(jwt().jwt(token -> token.subject("user-1"))))
      .andExpect(status().isOk());

    verify(tradingService).getUserTrades("user-1", 3, 100);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"abc", "99999999999999999999", "1.5"})
  @DisplayName("포트폴리오 경로의 계좌 ID 가 정수가 아니거나 Long 범위를 넘으면 400 이고 서비스까지 가지 않는다")
  void portfolio_InvalidAccountId(String accountId) throws Exception {
    mockMvc.perform(get("/api/portfolio/account/" + accountId).with(jwt()))
      .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/portfolio/account/" + accountId + "/symbol/AAPL").with(jwt()))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(holdingsService);
  }

  @Test
  @DisplayName("포트폴리오 경로의 계좌 ID 는 숫자로 넘긴다")
  void portfolio_AccountId() throws Exception {
    given(holdingsService.getPortfolio(anyString(), anyLong())).willReturn(List.of());

    mockMvc.perform(get("/api/portfolio/account/7").with(jwt().jwt(token -> token.subject("user-1"))))
      .andExpect(status().isOk());

    verify(holdingsService).getPortfolio("user-1", 7L);
  }

  @Test
  @DisplayName("매수 · 매도 본문의 계좌 ID 가 18자리를 넘는 숫자면 400 이고 서비스까지 가지 않는다")
  void trade_AccountIdTooLong() throws Exception {
    String body = "{\"accountId\":\"99999999999999999999\",\"symbol\":\"AAPL\",\"quantity\":1,\"tradeDate\":\"2024-01-15\"}";

    mockMvc.perform(post("/api/trade/buy").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/trade/sell").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(tradingService);
  }
}
