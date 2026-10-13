package com.muscat.backtest.domain.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.GlobalExceptionHandler;
import com.muscat.backtest.config.SecurityConfig;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.OptimalTimingResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// BacktestApplication 의 @ComponentScan 이 슬라이스 필터를 덮어써서 띄울 클래스를 직접 고른다. 보안은 실제 설정을 쓴다
@WebMvcTest(BacktestAnalysisController.class)
@ContextConfiguration(classes = {BacktestAnalysisController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("BacktestAnalysisController 요청 · 응답")
class BacktestAnalysisControllerTest {

  private static final String BASE = "/api/analysis";
  private static final String PERIOD = "\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private BacktestAnalysisService backtestAnalysisService;

  // 분석은 토큰 없이 열려 있어 디코더를 부르지 않는다. 실제 Keycloak 에 붙지 않게 막아 둔다
  @MockitoBean
  private JwtDecoder jwtDecoder;

  private ResultActions postJson(String path, String body) throws Exception {
    return mockMvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static StrategyResponse strategy(String symbol, StrategyType type, String returnPercent) {
    return StrategyResponse.builder().symbol(symbol).strategyType(type)
      .totalReturnPercent(new BigDecimal(returnPercent)).build();
  }

  private static ComparisonResponse comparison(String best) {
    ComparisonItem item = ComparisonItem.builder().name(best).totalReturnPercent(new BigDecimal("12.5")).rank(1).build();
    return ComparisonResponse.builder().items(List.of(item)).bestPerformer(item).build();
  }

  @Test
  @DisplayName("적립식 전략은 토큰 없이 실행되고 요청 값을 그대로 넘긴다")
  void dca() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "30.42"));

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.strategyType").value("DCA"))
      .andExpect(jsonPath("$.totalReturnPercent").value(30.42));

    verify(backtestAnalysisService).runDcaStrategy(argThat((DcaStrategyRequest r) ->
      r.getSymbol().equals("AAPL") && r.getMonthlyAmount().intValue() == 100000 && r.getPurchaseDay() == 15));
  }

  @Test
  @DisplayName("적립식 전략에 종목이 없으면 400 이고 서비스까지 가지 않는다")
  void dca_MissingSymbol() throws Exception {
    postJson("/strategy/dca", "{" + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runDcaStrategy(any());
  }

  @Test
  @DisplayName("조건부 매수 전략은 하락률을 넘긴다")
  void conditional() throws Exception {
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("TSLA", StrategyType.CONDITIONAL_PURCHASE, "-4.1"));

    postJson("/strategy/conditional", "{\"symbol\":\"TSLA\"," + PERIOD + ",\"totalInvestment\":1000000,\"dropPercentage\":5}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalReturnPercent").value(-4.1));

    verify(backtestAnalysisService).runConditionalStrategy(argThat(r -> r.getDropPercentage().intValue() == 5));
  }

  @Test
  @DisplayName("조건부 매수 전략에 하락률이 없으면 400")
  void conditional_MissingDrop() throws Exception {
    postJson("/strategy/conditional", "{\"symbol\":\"TSLA\"," + PERIOD + ",\"totalInvestment\":1000000}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runConditionalStrategy(any());
  }

  @Test
  @DisplayName("종목 비교는 종목 목록을 넘기고 최고 수익 종목을 돌려준다")
  void compareSymbols() throws Exception {
    given(backtestAnalysisService.compareSymbols(any())).willReturn(comparison("MSFT"));

    postJson("/compare/symbols", "{\"symbols\":[\"AAPL\",\"MSFT\"]," + PERIOD + ",\"investmentAmount\":1000000}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bestPerformer.name").value("MSFT"));

    verify(backtestAnalysisService).compareSymbols(argThat(r -> r.getSymbols().equals(List.of("AAPL", "MSFT"))));
  }

  @Test
  @DisplayName("종목 비교에 투자금이 없으면 400")
  void compareSymbols_MissingAmount() throws Exception {
    postJson("/compare/symbols", "{\"symbols\":[\"AAPL\",\"MSFT\"]," + PERIOD + "}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).compareSymbols(any());
  }

  @Test
  @DisplayName("전략 비교는 전략 목록을 넘긴다")
  void compareStrategies() throws Exception {
    given(backtestAnalysisService.compareStrategies(any())).willReturn(comparison("DCA"));

    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"DCA\",\"monthlyAmount\":100000},{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bestPerformer.name").value("DCA"));

    verify(backtestAnalysisService).compareStrategies(argThat(r -> r.getStrategies().size() == 2
      && r.getStrategies().get(0).getStrategyType() == StrategyType.DCA));
  }

  @Test
  @DisplayName("시점 비교는 매수일 목록을 넘긴다")
  void compareTiming() throws Exception {
    given(backtestAnalysisService.compareTiming(any())).willReturn(comparison("2024-03-01"));

    postJson("/compare/timing", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"purchaseDates\":[\"2024-01-02\",\"2024-03-01\"]}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].rank").value(1));

    verify(backtestAnalysisService).compareTiming(argThat(r -> r.getPurchaseDates().size() == 2));
  }

  @Test
  @DisplayName("최적 시점 분석은 목표 수익률을 넘긴다")
  void optimalTiming() throws Exception {
    given(backtestAnalysisService.analyzeOptimalTiming(any())).willReturn(OptimalTimingResponse.builder()
      .symbol("AAPL").totalAnalyzedDays(120).totalQualifyingDays(7).build());

    postJson("/timing/optimal", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,\"targetReturnPercent\":10}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalQualifyingDays").value(7));

    verify(backtestAnalysisService).analyzeOptimalTiming(argThat(r -> r.getTargetReturnPercent().intValue() == 10));
  }

  @Test
  @DisplayName("열려 있는 건 POST 뿐이다. 같은 경로라도 GET 은 토큰이 없으면 401")
  void onlyPostIsOpen() throws Exception {
    mockMvc.perform(get(BASE + "/strategy/dca")).andExpect(status().isUnauthorized());
  }
}
