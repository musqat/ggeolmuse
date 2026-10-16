package com.muscat.backtest.domain.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.backtest.common.exception.GlobalExceptionHandler;
import com.muscat.backtest.config.SecurityConfig;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.service.InvestmentBacktestService;
import com.muscat.backtest.domain.service.TradingSimulationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TradingSimulationController.class)
@ContextConfiguration(classes = {TradingSimulationController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("TradingSimulationController 요청 · 응답")
class TradingSimulationControllerTest {

  private static final String BASE = "/api/trading-simulation";
  private static final String TOKEN = "good-token";
  private static final String USER = "user-1";
  private static final String EMAIL = "user@example.com";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TradingSimulationService tradingSimulationService;

  @MockitoBean
  private InvestmentBacktestService investmentBacktestService;

  // 실제 보안 설정의 디코더 자리를 바꿔 끼운다. 이 토큰만 사용자로 풀린다
  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void decodeToken() {
    given(jwtDecoder.decode(TOKEN)).willReturn(Jwt.withTokenValue(TOKEN).header("alg", "none").subject(USER)
      .claim("email", EMAIL).build());
  }

  private static InvestmentResponse investment(String status) {
    return InvestmentResponse.builder().status(status).symbol("AAPL").build();
  }

  @Test
  @DisplayName("시뮬레이션은 토큰 없이 실행되고 종목 · 매수일 · 투자금을 그대로 넘긴다")
  void simulation() throws Exception {
    given(tradingSimulationService.runSimulation(any())).willReturn(SimulationResponse.builder()
      .symbol("AAPL").totalReturnPercent(new BigDecimal("52.38")).build());

    mockMvc.perform(post(BASE + "/simulation").contentType(MediaType.APPLICATION_JSON)
        .content("{\"symbol\":\"AAPL\",\"purchaseDate\":\"2023-01-02\",\"investmentAmount\":1000000}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalReturnPercent").value(52.38));

    verify(tradingSimulationService).runSimulation(argThat((SimulationRequest r) -> r.getSymbol().equals("AAPL")
      && r.getPurchaseDate().equals(LocalDate.of(2023, 1, 2)) && r.getInvestmentAmount().intValue() == 1000000));
  }

  @Test
  @DisplayName("시뮬레이션에 매수일이 없으면 400 이고 서비스까지 가지 않는다")
  void simulation_MissingPurchaseDate() throws Exception {
    mockMvc.perform(post(BASE + "/simulation").contentType(MediaType.APPLICATION_JSON)
        .content("{\"symbol\":\"AAPL\",\"investmentAmount\":1000000}"))
      .andExpect(status().isBadRequest());

    verify(tradingSimulationService, never()).runSimulation(any());
  }

  @Test
  @DisplayName("로그인하고 돌린 시뮬레이션의 이력 주인은 본문이 아니라 토큰의 이메일이다")
  void simulation_OwnerFromToken() throws Exception {
    given(tradingSimulationService.runSimulation(any())).willReturn(SimulationResponse.builder().symbol("AAPL").build());

    mockMvc.perform(post(BASE + "/simulation").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"symbol\":\"AAPL\",\"purchaseDate\":\"2023-01-02\",\"investmentAmount\":1000000,"
          + "\"userId\":\"victim@example.com\"}"))
      .andExpect(status().isOk());

    verify(tradingSimulationService).runSimulation(argThat((SimulationRequest r) -> EMAIL.equals(r.getUserId())));
  }

  @Test
  @DisplayName("토큰 없이 돌린 시뮬레이션은 본문의 userId 를 버려 이력 주인이 없다")
  void simulation_AnonymousDropsBodyUser() throws Exception {
    given(tradingSimulationService.runSimulation(any())).willReturn(SimulationResponse.builder().symbol("AAPL").build());

    mockMvc.perform(post(BASE + "/simulation").contentType(MediaType.APPLICATION_JSON)
        .content("{\"symbol\":\"AAPL\",\"purchaseDate\":\"2023-01-02\",\"investmentAmount\":1000000,"
          + "\"userId\":\"victim@example.com\"}"))
      .andExpect(status().isOk());

    verify(tradingSimulationService).runSimulation(argThat((SimulationRequest r) -> r.getUserId() == null));
  }

  @Test
  @DisplayName("보유 주식 백테스트는 토큰이 없으면 401 이고 서비스까지 가지 않는다")
  void investment_RequiresToken() throws Exception {
    mockMvc.perform(get(BASE + "/investment")).andExpect(status().isUnauthorized());

    verify(investmentBacktestService, never()).executeInvestment(any(), anyString());
  }

  @Test
  @DisplayName("보유 주식 백테스트는 토큰의 사용자로 돌리고 trade 에 넘길 Bearer 토큰을 만든다")
  void investment_UsesTokenSubject() throws Exception {
    given(investmentBacktestService.executeInvestment(any(), anyString())).willReturn(investment("SUCCESS"));

    mockMvc.perform(get(BASE + "/investment").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(investmentBacktestService).executeInvestment(argThat(r -> r.getUserId().equals(USER)), eq("Bearer " + TOKEN));
  }

  @Test
  @DisplayName("투자 시뮬레이션은 본문이 아니라 토큰의 사용자와 Bearer 토큰을 넘긴다")
  void simulate() throws Exception {
    given(investmentBacktestService.executeInvestment(any(), anyString())).willReturn(investment("SUCCESS"));

    mockMvc.perform(post(BASE + "/simulate").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"someone-else\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.symbol").value("AAPL"));

    verify(investmentBacktestService).executeInvestment(argThat(r -> r.getUserId().equals(USER)), eq("Bearer " + TOKEN));
  }

  @Test
  @DisplayName("투자 시뮬레이션은 본문에 사용자가 없어도 토큰의 사용자로 실행된다")
  void simulate_NoBodyUser() throws Exception {
    given(investmentBacktestService.executeInvestment(any(), anyString())).willReturn(investment("SUCCESS"));

    mockMvc.perform(post(BASE + "/simulate").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isOk());

    verify(investmentBacktestService).executeInvestment(argThat(r -> r.getUserId().equals(USER)), eq("Bearer " + TOKEN));
  }

  @Test
  @DisplayName("투자 시뮬레이션은 토큰이 없으면 401 이고 서비스까지 가지 않는다")
  void simulate_RequiresToken() throws Exception {
    mockMvc.perform(post(BASE + "/simulate").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"user-1\"}"))
      .andExpect(status().isUnauthorized());

    verify(investmentBacktestService, never()).executeInvestment(any(), anyString());
  }
}
