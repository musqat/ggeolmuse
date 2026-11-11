package com.muscat.trade.api.controller;

import static org.hamcrest.Matchers.nullValue;
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
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Holding;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.MonthlyProfit;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Sale;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse.SymbolContribution;
import com.muscat.trade.domain.service.PortfolioStatsService;
import com.muscat.trade.domain.service.ProfitFlowService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
  private static final String PROFIT = "/api/portfolio/stats/profit";
  private static final String SYMBOLS = "/api/portfolio/stats/symbols";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ProfitFlowService profitFlowService;

  @MockitoBean
  private PortfolioStatsService portfolioStatsService;

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

  @Test
  @DisplayName("수익 자세히는 토큰 주인의 달마다 수익 · 판 거래 · 들고 있는 종목을 돌려준다")
  void profit_ReturnsDetail() throws Exception {
    given(portfolioStatsService.profit("user-1", "3", FlowPeriod.ONE_YEAR)).willReturn(new ProfitDetailResponse(
      List.of(new MonthlyProfit(YearMonth.of(2024, 9), new BigDecimal("120000"))),
      new BigDecimal("46834"), new BigDecimal("-756000"),
      List.of(new Sale(41L, 3L, LocalDate.of(2024, 9, 18), "AAPL", new BigDecimal("2.000000"),
        new BigDecimal("220.15"), new BigDecimal("35.20"), new BigDecimal("1330.50"), new BigDecimal("46834"),
        new BigDecimal("8.69"))),
      List.of(new Holding("XYZ", new BigDecimal("3.000000"), new BigDecimal("180.00"), null, null,
        new BigDecimal("-540.00"), new BigDecimal("-756000"), new BigDecimal("-100.00")))));

    mockMvc.perform(get(PROFIT).param("accountId", "3").param("period", "1Y").with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.monthly[0].month").value("2024-09"))
      .andExpect(jsonPath("$.monthly[0].profitKrw").value(120000))
      .andExpect(jsonPath("$.realizedKrw").value(46834))
      .andExpect(jsonPath("$.unrealizedKrw").value(-756000))
      .andExpect(jsonPath("$.sales[0].tradeId").value(41))
      .andExpect(jsonPath("$.sales[0].accountId").value(3))
      .andExpect(jsonPath("$.sales[0].date").value("2024-09-18"))
      .andExpect(jsonPath("$.sales[0].symbol").value("AAPL"))
      .andExpect(jsonPath("$.sales[0].quantity").value(2.0))
      .andExpect(jsonPath("$.sales[0].priceUsd").value(220.15))
      .andExpect(jsonPath("$.sales[0].realizedUsd").value(35.20))
      .andExpect(jsonPath("$.sales[0].fxRate").value(1330.50))
      .andExpect(jsonPath("$.sales[0].realizedKrw").value(46834))
      .andExpect(jsonPath("$.sales[0].returnPercent").value(8.69))
      .andExpect(jsonPath("$.holdings[0].symbol").value("XYZ"))
      .andExpect(jsonPath("$.holdings[0].quantity").value(3.0))
      .andExpect(jsonPath("$.holdings[0].averagePriceUsd").value(180.00))
      .andExpect(jsonPath("$.holdings[0].closeUsd").value(nullValue()))
      .andExpect(jsonPath("$.holdings[0].closeDate").value(nullValue()))
      .andExpect(jsonPath("$.holdings[0].unrealizedUsd").value(-540.00))
      .andExpect(jsonPath("$.holdings[0].unrealizedKrw").value(-756000))
      .andExpect(jsonPath("$.holdings[0].returnPercent").value(-100.00));
  }

  @Test
  @DisplayName("종목별 기여는 토큰 주인의 종목마다 실현 · 평가 · 배당 · 수수료 · 합 · 비율 · 보유 여부를 돌려준다")
  void symbols_ReturnsContributions() throws Exception {
    given(portfolioStatsService.symbols("user-1", "all", FlowPeriod.THREE_MONTHS)).willReturn(
      new SymbolContributionResponse(new BigDecimal("1250000"), List.of(new SymbolContribution("AAPL",
        new BigDecimal("46834"), new BigDecimal("190680"), new BigDecimal("0"), new BigDecimal("1200"),
        new BigDecimal("236314"), new BigDecimal("62.50"), true))));

    mockMvc.perform(get(SYMBOLS).param("accountId", "all").param("period", "3M")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalKrw").value(1250000))
      .andExpect(jsonPath("$.symbols[0].symbol").value("AAPL"))
      .andExpect(jsonPath("$.symbols[0].realizedKrw").value(46834))
      .andExpect(jsonPath("$.symbols[0].unrealizedKrw").value(190680))
      .andExpect(jsonPath("$.symbols[0].dividendKrw").value(0))
      .andExpect(jsonPath("$.symbols[0].feeKrw").value(1200))
      .andExpect(jsonPath("$.symbols[0].totalKrw").value(236314))
      .andExpect(jsonPath("$.symbols[0].sharePercent").value(62.50))
      .andExpect(jsonPath("$.symbols[0].held").value(true));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {PROFIT, SYMBOLS})
  @DisplayName("통계 탭도 기간 값이 1M · 3M · 1Y · ALL 이 아니면 400, 토큰이 없으면 401 이고 서비스까지 가지 않는다")
  void tabs_InvalidPeriodOrNoToken(String path) throws Exception {
    mockMvc.perform(get(path).param("accountId", "all").param("period", "2W").with(jwt()))
      .andExpect(status().isBadRequest());
    mockMvc.perform(get(path).param("accountId", "all").param("period", "1M"))
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(portfolioStatsService);
  }

  @Test
  @DisplayName("통계 탭도 남의 계좌면 403, 계좌 값이 숫자 · all 이 아니면 400 이다")
  void tabs_AccountErrors() throws Exception {
    given(portfolioStatsService.profit("user-1", "7", FlowPeriod.ALL))
      .willThrow(new TradeException(TradeResponse.ACCOUNT_ACCESS_DENIED));
    given(portfolioStatsService.symbols("user-1", "abc", FlowPeriod.ALL))
      .willThrow(new TradeException(TradeResponse.INVALID_ACCOUNT_ID));

    mockMvc.perform(get(PROFIT).param("accountId", "7").param("period", "ALL")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isForbidden());
    mockMvc.perform(get(SYMBOLS).param("accountId", "abc").param("period", "ALL")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isBadRequest());
  }
}
