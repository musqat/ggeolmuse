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
import com.muscat.trade.domain.dto.response.DividendStatsResponse;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.MonthlyDividend;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.Payout;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.SymbolDividend;
import com.muscat.trade.domain.dto.response.FxStatsResponse;
import com.muscat.trade.domain.dto.response.FxStatsResponse.Direction;
import com.muscat.trade.domain.dto.response.FxStatsResponse.Exchange;
import com.muscat.trade.domain.dto.response.MarketComparisonResponse;
import com.muscat.trade.domain.dto.response.MarketComparisonResponse.MarketPoint;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Holding;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.MonthlyProfit;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Sale;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse.SymbolContribution;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse.SymbolTrades;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse.WeekdayTrades;
import com.muscat.trade.domain.service.PortfolioStatsService;
import com.muscat.trade.domain.service.ProfitFlowService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
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
  private static final String DIVIDENDS = "/api/portfolio/stats/dividends";
  private static final String FX = "/api/portfolio/stats/fx";
  private static final String HABITS = "/api/portfolio/stats/habits";
  private static final String MARKET = "/api/portfolio/stats/market";

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

  @Test
  @DisplayName("배당은 토큰 주인의 세후 · 세전 · 세금 합과 달마다 · 종목마다 합 · 배당 내역을 돌려준다")
  void dividends_ReturnsStats() throws Exception {
    given(portfolioStatsService.dividends("user-1", "3", FlowPeriod.ONE_YEAR)).willReturn(new DividendStatsResponse(
      new BigDecimal("85000"), new BigDecimal("63.20"), new BigDecimal("74.35"), new BigDecimal("11.15"), 7,
      List.of(new MonthlyDividend(YearMonth.of(2024, 9), new BigDecimal("12.40"), new BigDecimal("16500"))),
      List.of(new SymbolDividend("KO", new BigDecimal("30.10"), new BigDecimal("40100"), 3, new BigDecimal("47.18"))),
      List.of(new Payout(LocalDate.of(2024, 9, 13), 3L, "KO", new BigDecimal("10.000000"), new BigDecimal("0.485000"),
        new BigDecimal("4.85"), new BigDecimal("0.73"), new BigDecimal("4.12"), null, new BigDecimal("0")))));

    mockMvc.perform(get(DIVIDENDS).param("accountId", "3").param("period", "1Y")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.netKrw").value(85000))
      .andExpect(jsonPath("$.netUsd").value(63.20))
      .andExpect(jsonPath("$.grossUsd").value(74.35))
      .andExpect(jsonPath("$.taxUsd").value(11.15))
      .andExpect(jsonPath("$.payoutCount").value(7))
      .andExpect(jsonPath("$.monthly[0].month").value("2024-09"))
      .andExpect(jsonPath("$.monthly[0].netUsd").value(12.40))
      .andExpect(jsonPath("$.monthly[0].netKrw").value(16500))
      .andExpect(jsonPath("$.symbols[0].symbol").value("KO"))
      .andExpect(jsonPath("$.symbols[0].netUsd").value(30.10))
      .andExpect(jsonPath("$.symbols[0].netKrw").value(40100))
      .andExpect(jsonPath("$.symbols[0].payoutCount").value(3))
      .andExpect(jsonPath("$.symbols[0].sharePercent").value(47.18))
      .andExpect(jsonPath("$.payouts[0].exDate").value("2024-09-13"))
      .andExpect(jsonPath("$.payouts[0].accountId").value(3))
      .andExpect(jsonPath("$.payouts[0].symbol").value("KO"))
      .andExpect(jsonPath("$.payouts[0].shares").value(10.0))
      .andExpect(jsonPath("$.payouts[0].dividendPerShareUsd").value(0.485))
      .andExpect(jsonPath("$.payouts[0].grossUsd").value(4.85))
      .andExpect(jsonPath("$.payouts[0].taxUsd").value(0.73))
      .andExpect(jsonPath("$.payouts[0].netUsd").value(4.12))
      .andExpect(jsonPath("$.payouts[0].fxRate").value(nullValue()))
      .andExpect(jsonPath("$.payouts[0].netKrw").value(0));
  }

  @Test
  @DisplayName("환율 효과는 토큰 주인의 효과 · 지금 환율 · 평균 환율 · 들고 있는 달러와 기간 안 환전을 돌려준다")
  void fx_ReturnsStats() throws Exception {
    given(portfolioStatsService.fx("user-1", "all", FlowPeriod.ALL)).willReturn(new FxStatsResponse(
      new BigDecimal("152000"), new BigDecimal("1385.20"), null, new BigDecimal("0.00"), new BigDecimal("3000000"),
      new BigDecimal("300.00"), 4, List.of(new Exchange(LocalDate.of(2024, 9, 2), 3L, Direction.KRW_TO_USD,
        new BigDecimal("1300000"), new BigDecimal("1000.00"), new BigDecimal("1300.00"), new BigDecimal("85200")))));

    mockMvc.perform(get(FX).param("accountId", "all").param("period", "ALL")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.fxEffectKrw").value(152000))
      .andExpect(jsonPath("$.currentRate").value(1385.20))
      .andExpect(jsonPath("$.averageRate").value(nullValue()))
      .andExpect(jsonPath("$.heldUsd").value(0.00))
      .andExpect(jsonPath("$.krwToUsdKrw").value(3000000))
      .andExpect(jsonPath("$.usdToKrwUsd").value(300.00))
      .andExpect(jsonPath("$.exchangeCount").value(4))
      .andExpect(jsonPath("$.exchanges[0].date").value("2024-09-02"))
      .andExpect(jsonPath("$.exchanges[0].accountId").value(3))
      .andExpect(jsonPath("$.exchanges[0].direction").value("KRW_TO_USD"))
      .andExpect(jsonPath("$.exchanges[0].krwAmount").value(1300000))
      .andExpect(jsonPath("$.exchanges[0].usdAmount").value(1000.00))
      .andExpect(jsonPath("$.exchanges[0].rate").value(1300.00))
      .andExpect(jsonPath("$.exchanges[0].effectKrw").value(85200));
  }

  @Test
  @DisplayName("환전 기록을 못 받으면 환율 효과는 502 이다")
  void fx_UserServiceError_BadGateway() throws Exception {
    given(portfolioStatsService.fx("user-1", "all", FlowPeriod.ONE_MONTH))
      .willThrow(new TradeException(TradeResponse.USER_SERVICE_ERROR));

    mockMvc.perform(get(FX).param("accountId", "all").param("period", "1M")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isBadGateway())
      .andExpect(jsonPath("$.errorCode").value("USER_SERVICE_ERROR"));
  }

  @Test
  @DisplayName("거래 패턴은 토큰 주인의 매수 · 매도 수 · 거래한 날 · 평균 보유 기간 · 승률 · 요일마다 체결 수 · 많이 거래한 종목을 돌려준다")
  void habits_ReturnsStats() throws Exception {
    given(portfolioStatsService.habits("user-1", "3", FlowPeriod.THREE_MONTHS)).willReturn(new TradingHabitsResponse(
      12, 4, 10, new BigDecimal("42.5"), 3, 1, new BigDecimal("75.00"),
      List.of(new WeekdayTrades(DayOfWeek.MONDAY, 3), new WeekdayTrades(DayOfWeek.TUESDAY, 0)),
      List.of(new SymbolTrades("AAPL", 6, 4, 2, new BigDecimal("2450.30")))));

    mockMvc.perform(get(HABITS).param("accountId", "3").param("period", "3M")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.buyCount").value(12))
      .andExpect(jsonPath("$.sellCount").value(4))
      .andExpect(jsonPath("$.tradingDays").value(10))
      .andExpect(jsonPath("$.averageHoldingDays").value(42.5))
      .andExpect(jsonPath("$.winCount").value(3))
      .andExpect(jsonPath("$.lossCount").value(1))
      .andExpect(jsonPath("$.winRatePercent").value(75.00))
      .andExpect(jsonPath("$.weekdays[0].weekday").value("MONDAY"))
      .andExpect(jsonPath("$.weekdays[0].count").value(3))
      .andExpect(jsonPath("$.weekdays[1].weekday").value("TUESDAY"))
      .andExpect(jsonPath("$.weekdays[1].count").value(0))
      .andExpect(jsonPath("$.topSymbols[0].symbol").value("AAPL"))
      .andExpect(jsonPath("$.topSymbols[0].tradeCount").value(6))
      .andExpect(jsonPath("$.topSymbols[0].buyCount").value(4))
      .andExpect(jsonPath("$.topSymbols[0].sellCount").value(2))
      .andExpect(jsonPath("$.topSymbols[0].amountUsd").value(2450.30));
  }

  @Test
  @DisplayName("시장 비교는 토큰 주인의 수익 흐름 날짜마다 SPY 수익과 마지막 점 수익을 돌려준다")
  void market_ReturnsComparison() throws Exception {
    given(portfolioStatsService.market("user-1", "all", FlowPeriod.ONE_YEAR)).willReturn(new MarketComparisonResponse(
      "SPY", List.of(new MarketPoint(LocalDate.of(2024, 9, 18), new BigDecimal("-12000")),
        new MarketPoint(LocalDate.of(2024, 9, 19), new BigDecimal("98000"))), new BigDecimal("98000")));

    mockMvc.perform(get(MARKET).param("accountId", "all").param("period", "1Y")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.symbol").value("SPY"))
      .andExpect(jsonPath("$.points[0].date").value("2024-09-18"))
      .andExpect(jsonPath("$.points[0].profitKrw").value(-12000))
      .andExpect(jsonPath("$.points[1].date").value("2024-09-19"))
      .andExpect(jsonPath("$.points[1].profitKrw").value(98000))
      .andExpect(jsonPath("$.currentProfitKrw").value(98000));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {PROFIT, SYMBOLS, DIVIDENDS, FX, HABITS, MARKET})
  @DisplayName("통계 탭 · 시장 비교도 기간 값이 1M · 3M · 1Y · ALL 이 아니면 400, 토큰이 없으면 401 이고 서비스까지 가지 않는다")
  void tabs_InvalidPeriodOrNoToken(String path) throws Exception {
    mockMvc.perform(get(path).param("accountId", "all").param("period", "2W").with(jwt()))
      .andExpect(status().isBadRequest());
    mockMvc.perform(get(path).param("accountId", "all").param("period", "1M"))
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(portfolioStatsService);
  }

  @Test
  @DisplayName("통계 탭 · 시장 비교도 남의 계좌면 403, 계좌 값이 숫자 · all 이 아니면 400 이다")
  void tabs_AccountErrors() throws Exception {
    given(portfolioStatsService.profit("user-1", "7", FlowPeriod.ALL))
      .willThrow(new TradeException(TradeResponse.ACCOUNT_ACCESS_DENIED));
    given(portfolioStatsService.symbols("user-1", "abc", FlowPeriod.ALL))
      .willThrow(new TradeException(TradeResponse.INVALID_ACCOUNT_ID));
    given(portfolioStatsService.habits("user-1", "7", FlowPeriod.ONE_YEAR))
      .willThrow(new TradeException(TradeResponse.ACCOUNT_ACCESS_DENIED));
    given(portfolioStatsService.market("user-1", "abc", FlowPeriod.ONE_MONTH))
      .willThrow(new TradeException(TradeResponse.INVALID_ACCOUNT_ID));

    mockMvc.perform(get(PROFIT).param("accountId", "7").param("period", "ALL")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isForbidden());
    mockMvc.perform(get(SYMBOLS).param("accountId", "abc").param("period", "ALL")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isBadRequest());
    mockMvc.perform(get(HABITS).param("accountId", "7").param("period", "1Y")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isForbidden());
    mockMvc.perform(get(MARKET).param("accountId", "abc").param("period", "1M")
        .with(jwt().jwt(t -> t.subject("user-1"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_ACCOUNT_ID"));
  }
}
