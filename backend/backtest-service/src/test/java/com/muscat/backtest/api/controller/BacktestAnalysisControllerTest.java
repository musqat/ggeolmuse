package com.muscat.backtest.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioAllocation;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.PortfolioRebalance;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.enums.type.TaxSale;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.exception.GlobalExceptionHandler;
import com.muscat.backtest.common.metrics.BacktestMetrics;
import com.muscat.backtest.config.SecurityConfig;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.PartialTakeProfit;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.PortfolioResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import com.muscat.backtest.domain.strategy.ConditionalPurchaseStrategy;
import com.muscat.backtest.domain.strategy.MaBandStrategy;
import com.muscat.backtest.domain.strategy.MaCrossStrategy;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.ResultActions;

// 띄울 클래스를 직접 고름(BacktestApplication 의 @ComponentScan 이 슬라이스 필터를 덮어씀, 보안은 실제 설정)
@WebMvcTest(BacktestAnalysisController.class)
@ContextConfiguration(classes = {BacktestAnalysisController.class, GlobalExceptionHandler.class, SecurityConfig.class,
  BacktestMetrics.class, SimpleMeterRegistry.class})
@ActiveProfiles("test")
@DisplayName("BacktestAnalysisController 요청 · 응답")
class BacktestAnalysisControllerTest {

  private static final String BASE = "/api/analysis";
  private static final String PERIOD = "\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"";
  private static final String TOKEN = "good-token";
  private static final String EMAIL = "user@example.com";
  private static final String USER = "user-1";
  private static final String VICTIM = ",\"userId\":\"victim@example.com\"}";
  // 총액 1000만원 · SPY 60% · QQQ 40% 적립식 · 분기마다 리밸런싱(닫는 괄호 없음)
  private static final String PORTFOLIO = "{" + PERIOD + ",\"allocation\":\"WEIGHT\",\"totalAmount\":10000000,"
    + "\"assets\":[{\"symbol\":\"SPY\",\"weightPercent\":60},"
    + "{\"symbol\":\"QQQ\",\"weightPercent\":40,\"strategy\":{\"type\":\"LUMP_SUM\"}}],"
    + "\"method\":\"DCA\",\"frequency\":\"MONTHLY\",\"rebalance\":\"QUARTERLY\"";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MeterRegistry meterRegistry;

  @BeforeEach
  void clearMetrics() {
    meterRegistry.clear();
  }

  @MockitoBean
  private BacktestAnalysisService backtestAnalysisService;

  // 실제 Keycloak 에 붙지 않게 바꿔 끼운 디코더
  @MockitoBean
  private JwtDecoder jwtDecoder;

  private ResultActions postJson(String path, String body) throws Exception {
    return mockMvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions postJsonWithToken(String path, String body) throws Exception {
    return mockMvc.perform(post(BASE + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
      .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static StrategyResponse strategy(String symbol, StrategyType type, String returnPercent) {
    return StrategyResponse.builder().symbol(symbol).strategyType(type)
      .totalReturnPercent(new BigDecimal(returnPercent)).build();
  }

  private static ComparisonResponse comparison(String best) {
    ComparisonItem item = ComparisonItem.builder().name(best).totalReturnPercent(new BigDecimal("12.5")).rank(1).build();
    return ComparisonResponse.builder().items(List.of(item)).bestPerformer(item).build();
  }

  private static String schedule(int interval, int day) {
    return "\"investmentInterval\":" + interval + ",\"purchaseDay\":" + day;
  }

  @Test
  @DisplayName("적립식 전략은 토큰 없이 실행되고 종목 · monthlyAmount 로 보낸 회당 금액 · 매수일을 그대로 넘긴다")
  void dca() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "30.42"));

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.strategyType").value("DCA"))
      .andExpect(jsonPath("$.totalReturnPercent").value(30.42));

    verify(backtestAnalysisService).runDcaStrategy(argThat((DcaStrategyRequest r) ->
      r.getSymbol().equals("AAPL") && r.getAmountPerPurchase().intValue() == 100000 && r.getPurchaseDay() == 15
        && r.getFrequency() == DcaFrequency.MONTHLY));
  }

  @Test
  @DisplayName("적립식 전략은 회당 금액 · 주기 · 첫 매수일을 넘기고 투자일을 비워도 된다")
  void dcaFrequency() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "1"));

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD
      + ",\"amountPerPurchase\":100000,\"frequency\":\"WEEKLY\",\"firstPurchaseDate\":\"2024-01-03\"}")
      .andExpect(status().isOk());

    verify(backtestAnalysisService).runDcaStrategy(argThat((DcaStrategyRequest r) ->
      r.getAmountPerPurchase().intValue() == 100000 && r.getFrequency() == DcaFrequency.WEEKLY
        && r.getFirstPurchaseDate().equals(LocalDate.of(2024, 1, 3)) && r.getPurchaseDay() == null));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"\"frequency\":\"HOURLY\"", "\"totalInvestmentLimit\":0", "\"amountPerPurchase\":0"})
  @DisplayName("적립식 전략의 주기가 매일 · 매주 · 매달 밖이거나 예산 · 회당 금액이 0 이면 400 이고 서비스까지 가지 않는다")
  void dca_InvalidFrequencyOrAmounts(String field) throws Exception {
    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"amountPerPurchase\":100000," + field + "}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runDcaStrategy(any());
  }

  @Test
  @DisplayName("적립식 · 조건부 전략은 회당 금액 대신 주식 수를 1,000,000 까지 받아 그대로 넘긴다")
  void sharesPerPurchase() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "1"));
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("TSLA", StrategyType.CONDITIONAL_PURCHASE, "1"));

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"sharesPerPurchase\":2,\"frequency\":\"WEEKLY\"}")
      .andExpect(status().isOk());
    postJson("/strategy/conditional", "{\"symbol\":\"TSLA\"," + PERIOD + ",\"sharesPerPurchase\":1000000,\"dropPercentage\":0.05}")
      .andExpect(status().isOk());

    verify(backtestAnalysisService).runDcaStrategy(argThat((DcaStrategyRequest r) -> r.getSharesPerPurchase() == 2
      && r.getAmountPerPurchase() == null));
    verify(backtestAnalysisService).runConditionalStrategy(argThat(r -> r.getSharesPerPurchase() == 1_000_000
      && r.getAmountPerPurchase() == null));
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource(delimiter = '|', value = {
    "dca|둘 다|,\"amountPerPurchase\":100000,\"sharesPerPurchase\":1",
    "dca|둘 다 없음|",
    "dca|주식 수 0|,\"sharesPerPurchase\":0",
    "dca|주식 수 1000001|,\"sharesPerPurchase\":1000001",
    "conditional|둘 다|,\"amountPerPurchase\":100000,\"sharesPerPurchase\":1",
    "conditional|주식 수 0|,\"sharesPerPurchase\":0",
    "conditional|주식 수 1000001|,\"sharesPerPurchase\":1000001"})
  @DisplayName("적립식 · 조건부 전략의 회당 금액 · 주식 수가 둘 다 있거나 둘 다 없거나 주식 수가 1 ~ 1,000,000 밖이면 400 이고 서비스까지 가지 않는다")
  void amountOrSharesRejected(String path, String label, String fields) throws Exception {
    postJson("/strategy/" + path, "{\"symbol\":\"AAPL\"," + PERIOD + ",\"dropPercentage\":0.05"
      + (fields == null ? "" : fields) + "}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runDcaStrategy(any());
    verify(backtestAnalysisService, never()).runConditionalStrategy(any());
  }

  @Test
  @DisplayName("적립식 전략 한 번이 끝나면 종류 dca · 성공으로 소요 시간 지표에 한 건 남는다")
  void dca_RecordsSuccessMetric() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "30.42"));

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15}")
      .andExpect(status().isOk());

    assertThat(meterRegistry.get("backtest.run").tags("kind", "dca", "outcome", "success").timer().count())
      .isEqualTo(1);
  }

  @Test
  @DisplayName("전략이 실패하면 종류 conditional · 실패로 한 건 남는다")
  void conditional_RecordsFailureMetric() throws Exception {
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willThrow(new BacktestException(BacktestResponse.INVALID_REQUEST));

    postJson("/strategy/conditional", "{\"symbol\":\"TSLA\"," + PERIOD + ",\"amountPerPurchase\":100000,\"totalInvestment\":1000000,\"dropPercentage\":5}")
      .andExpect(status().isBadRequest());

    assertThat(meterRegistry.get("backtest.run").tags("kind", "conditional", "outcome", "failure").timer().count())
      .isEqualTo(1);
  }

  @Test
  @DisplayName("적립식 전략에 종목이 없으면 400 이고 서비스까지 가지 않는다")
  void dca_MissingSymbol() throws Exception {
    postJson("/strategy/dca", "{" + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runDcaStrategy(any());
  }

  @Test
  @DisplayName("적립식 전략의 수동 환율이 0 이면 400 이고 서비스까지 가지 않는다")
  void dca_ZeroFxRate() throws Exception {
    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15,\"purchaseFxRate\":0}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runDcaStrategy(any());
  }

  @ParameterizedTest(name = "투자 주기 {0} · 투자일 {1}")
  @CsvSource({"0, 15", "-1, 15", "13, 15", "1, 0", "1, 32"})
  @DisplayName("적립식 전략의 투자 주기가 1 ~ 12 밖이거나 투자일이 1 ~ 31 밖이면 400 이고 서비스까지 가지 않는다")
  void dca_ScheduleOutOfRange(int interval, int day) throws Exception {
    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000," + schedule(interval, day) + "}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runDcaStrategy(any());
  }

  @Test
  @DisplayName("조건부 매수 전략은 하락률을 넘긴다")
  void conditional() throws Exception {
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("TSLA", StrategyType.CONDITIONAL_PURCHASE, "-4.1"));

    postJson("/strategy/conditional", "{\"symbol\":\"TSLA\"," + PERIOD + ",\"amountPerPurchase\":100000,\"totalInvestment\":1000000,\"dropPercentage\":5}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalReturnPercent").value(-4.1));

    verify(backtestAnalysisService).runConditionalStrategy(argThat(r -> r.getDropPercentage().intValue() == 5));
  }

  @Test
  @DisplayName("적립식 · 조건부 전략은 양도세 · 기간 끝 선택을 받아 그대로 넘긴다")
  void capitalGainsTaxAndPeriodEnd() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "1"));
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("AAPL", StrategyType.CONDITIONAL_PURCHASE, "1"));
    String options = ",\"capitalGainsTax\":true,\"periodEnd\":\"SELL_AT_END\"}";

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"amountPerPurchase\":100000" + options)
      .andExpect(status().isOk());
    postJson("/strategy/conditional", "{\"symbol\":\"AAPL\"," + PERIOD
      + ",\"amountPerPurchase\":100000,\"dropPercentage\":0.05" + options)
      .andExpect(status().isOk());

    verify(backtestAnalysisService).runDcaStrategy(argThat((DcaStrategyRequest r) ->
      Boolean.TRUE.equals(r.getCapitalGainsTax()) && r.getPeriodEnd() == PeriodEnd.SELL_AT_END));
    verify(backtestAnalysisService).runConditionalStrategy(argThat(r ->
      Boolean.TRUE.equals(r.getCapitalGainsTax()) && r.getPeriodEnd() == PeriodEnd.SELL_AT_END));
  }

  @Test
  @DisplayName("기간 끝이 끝까지 보유 · 기간 끝 매도 밖이면 400 이고 서비스까지 가지 않는다")
  void periodEndInvalid() throws Exception {
    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"amountPerPurchase\":100000,\"periodEnd\":\"SELL\"}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runDcaStrategy(any());
  }

  @Test
  @DisplayName("조건부 · 이동평균 교차 · 밴드 전략은 양도세 판매 설정을 그대로 넘긴다")
  void taxSale() throws Exception {
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("AAPL", StrategyType.CONDITIONAL_PURCHASE, "1"));
    given(backtestAnalysisService.runMaCrossStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_CROSS, "1"));
    given(backtestAnalysisService.runMaBandStrategy(any())).willReturn(strategy("SPY", StrategyType.MA_BAND, "1"));
    String options = ",\"capitalGainsTax\":true,\"taxSale\":\"SELL\"}";

    postJson("/strategy/conditional", "{\"symbol\":\"AAPL\"," + PERIOD
      + ",\"amountPerPurchase\":100000,\"dropPercentage\":0.05" + options)
      .andExpect(status().isOk());
    postJson("/strategy/ma-cross", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":10000000" + options)
      .andExpect(status().isOk());
    postJson("/strategy/ma-band", "{\"symbol\":\"SPY\"," + PERIOD + ",\"investmentAmount\":10000000" + options)
      .andExpect(status().isOk());

    verify(backtestAnalysisService).runConditionalStrategy(argThat(r -> r.getTaxSale() == TaxSale.SELL));
    verify(backtestAnalysisService).runMaCrossStrategy(argThat((MaCrossStrategyRequest r) -> r.getTaxSale() == TaxSale.SELL));
    verify(backtestAnalysisService).runMaBandStrategy(argThat((MaBandStrategyRequest r) -> r.getTaxSale() == TaxSale.SELL));
  }

  @Test
  @DisplayName("양도세 판매 설정이 NONE · SELL 밖이면 400 이고 서비스까지 가지 않는다")
  void taxSaleInvalid() throws Exception {
    postJson("/strategy/ma-cross", "{\"symbol\":\"AAPL\"," + PERIOD
      + ",\"investmentAmount\":10000000,\"taxSale\":\"LOSS_FIRST\"}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runMaCrossStrategy(any());
  }

  @Test
  @DisplayName("조건부 매수 전략에 하락률이 없으면 400")
  void conditional_MissingDrop() throws Exception {
    postJson("/strategy/conditional", "{\"symbol\":\"TSLA\"," + PERIOD + ",\"amountPerPurchase\":100000}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runConditionalStrategy(any());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
    "회당 금액 · 주식 수 없음|\"totalInvestment\":1000000",
    "예산 0|\"amountPerPurchase\":100000,\"totalInvestment\":0",
    "최대 횟수 0|\"amountPerPurchase\":100000,\"maxPurchases\":0"})
  @DisplayName("조건부 매수 전략의 회당 금액 · 주식 수가 없거나 예산 · 최대 횟수가 0 이면 400 이고 서비스까지 가지 않는다")
  void conditional_LimitsRejected(String label, String limits) throws Exception {
    postJson("/strategy/conditional", "{\"symbol\":\"TSLA\"," + PERIOD + "," + limits + ",\"dropPercentage\":0.05}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runConditionalStrategy(any());
  }

  @Test
  @DisplayName("이동평균 교차 전략은 원금 · 짧은 · 긴 기간 · 신호 종목 · 양도세 · 기간 끝 선택을 그대로 넘기고 종류 ma_cross · 성공으로 지표에 한 건 남긴다")
  void maCross() throws Exception {
    given(backtestAnalysisService.runMaCrossStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_CROSS, "12.5"));

    postJson("/strategy/ma-cross", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":10000000,"
      + "\"shortWindow\":5,\"longWindow\":20,\"signalSymbol\":\"^GSPC\",\"capitalGainsTax\":true,\"periodEnd\":\"SELL_AT_END\"}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.strategyType").value("MA_CROSS"))
      .andExpect(jsonPath("$.totalReturnPercent").value(12.5));

    verify(backtestAnalysisService).runMaCrossStrategy(argThat((MaCrossStrategyRequest r) ->
      r.getSymbol().equals("AAPL") && r.getInvestmentAmount().intValue() == 10000000 && r.getShortWindow() == 5
        && r.getLongWindow() == 20 && "^GSPC".equals(r.getSignalSymbol()) && Boolean.TRUE.equals(r.getCapitalGainsTax())
        && r.getPeriodEnd() == PeriodEnd.SELL_AT_END));
    assertThat(meterRegistry.get("backtest.run").tags("kind", "ma_cross", "outcome", "success").timer().count())
      .isEqualTo(1);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"", ",\"shortWindow\":2,\"longWindow\":250", ",\"shortWindow\":59"})
  @DisplayName("이동평균 교차 전략의 짧은 · 긴 기간은 비우거나 2 ~ 250 안에서 짧은 기간이 작으면 통과한다")
  void maCrossWindowsAccepted(String windows) throws Exception {
    given(backtestAnalysisService.runMaCrossStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_CROSS, "1"));

    postJson("/strategy/ma-cross", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":10000000" + windows + "}")
      .andExpect(status().isOk());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
    "원금 없음|\"shortWindow\":5",
    "원금 0|\"investmentAmount\":0"})
  @DisplayName("이동평균 교차 전략의 원금이 없거나 0 이면 400 이고 서비스까지 가지 않는다")
  void maCrossRejected(String label, String fields) throws Exception {
    postJson("/strategy/ma-cross", "{\"symbol\":\"AAPL\"," + PERIOD + "," + fields + "}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runMaCrossStrategy(any());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
    "짧은 기간 1|\"shortWindow\":1",
    "긴 기간 251|\"longWindow\":251",
    "두 기간이 같음|\"shortWindow\":60,\"longWindow\":60",
    "짧은 기간 70 · 긴 기간 비움|\"shortWindow\":70"})
  @DisplayName("이동평균 교차 전략의 기간이 2 ~ 250 밖이거나 짧은 기간이 긴 기간보다 작지 않으면 400 과 INVALID_MA_WINDOWS")
  void maCrossWindowsRejected(String label, String fields) throws Exception {
    runStrategies();

    postJson("/strategy/ma-cross", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":10000000," + fields + "}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_MA_WINDOWS"));
  }

  @Test
  @DisplayName("이동평균 밴드 전략은 원금 · 평균 기간 · 위 · 아래 비율 · 신호 종목 · 양도세 · 기간 끝 선택을 그대로 넘기고 종류 ma_band · 성공으로 지표에 한 건 남긴다")
  void maBand() throws Exception {
    given(backtestAnalysisService.runMaBandStrategy(any())).willReturn(strategy("SPY", StrategyType.MA_BAND, "8.5"));

    postJson("/strategy/ma-band", "{\"symbol\":\"SPY\"," + PERIOD + ",\"investmentAmount\":10000000,"
      + "\"bandWindow\":200,\"upperRate\":0.025,\"lowerRate\":0.03,\"signalSymbol\":\"^NDX\",\"capitalGainsTax\":true,\"periodEnd\":\"SELL_AT_END\"}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.strategyType").value("MA_BAND"))
      .andExpect(jsonPath("$.totalReturnPercent").value(8.5));

    verify(backtestAnalysisService).runMaBandStrategy(argThat((MaBandStrategyRequest r) ->
      r.getSymbol().equals("SPY") && r.getInvestmentAmount().intValue() == 10000000 && r.getBandWindow() == 200
        && r.getUpperRate().compareTo(new BigDecimal("0.025")) == 0 && r.getLowerRate().compareTo(new BigDecimal("0.03")) == 0
        && "^NDX".equals(r.getSignalSymbol()) && Boolean.TRUE.equals(r.getCapitalGainsTax())
        && r.getPeriodEnd() == PeriodEnd.SELL_AT_END));
    assertThat(meterRegistry.get("backtest.run").tags("kind", "ma_band", "outcome", "success").timer().count())
      .isEqualTo(1);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"", ",\"bandWindow\":2,\"upperRate\":0,\"lowerRate\":0",
    ",\"bandWindow\":250,\"upperRate\":0.99,\"lowerRate\":0.99"})
  @DisplayName("이동평균 밴드 전략의 평균 기간 · 비율은 비우거나 2 ~ 250 · 0 이상 1 미만이면 통과한다")
  void maBandAccepted(String band) throws Exception {
    given(backtestAnalysisService.runMaBandStrategy(any())).willReturn(strategy("SPY", StrategyType.MA_BAND, "1"));

    postJson("/strategy/ma-band", "{\"symbol\":\"SPY\"," + PERIOD + ",\"investmentAmount\":10000000" + band + "}")
      .andExpect(status().isOk());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
    "원금 없음|\"bandWindow\":200",
    "원금 0|\"investmentAmount\":0"})
  @DisplayName("이동평균 밴드 전략의 원금이 없거나 0 이면 400 이고 서비스까지 가지 않는다")
  void maBandRejected(String label, String fields) throws Exception {
    postJson("/strategy/ma-band", "{\"symbol\":\"SPY\"," + PERIOD + "," + fields + "}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runMaBandStrategy(any());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
    "평균 기간 1|\"bandWindow\":1",
    "평균 기간 251|\"bandWindow\":251",
    "위 비율 음수|\"upperRate\":-0.01",
    "위 비율 1|\"upperRate\":1",
    "아래 비율 음수|\"lowerRate\":-0.01",
    "아래 비율 1|\"lowerRate\":1"})
  @DisplayName("이동평균 밴드 전략의 평균 기간이 2 ~ 250 밖이거나 비율이 0 이상 1 미만 밖이면 400 과 INVALID_BAND")
  void maBandRangeRejected(String label, String fields) throws Exception {
    runStrategies();

    postJson("/strategy/ma-band", "{\"symbol\":\"SPY\"," + PERIOD + ",\"investmentAmount\":10000000," + fields + "}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_BAND"));
  }

  // 서비스 자리에서 실제 전략을 돌림(칸 검사는 시세를 받기 전에 끝남)
  private void runStrategies() {
    MarketDataClientWrapper marketData = mock(MarketDataClientWrapper.class);
    ResponseMapper responseMapper = mock(ResponseMapper.class);
    given(backtestAnalysisService.runConditionalStrategy(any())).willAnswer(call ->
      new ConditionalPurchaseStrategy(marketData, responseMapper).execute(call.getArgument(0)));
    given(backtestAnalysisService.runMaCrossStrategy(any())).willAnswer(call ->
      new MaCrossStrategy(marketData, responseMapper).execute(call.getArgument(0)));
    given(backtestAnalysisService.runMaBandStrategy(any())).willAnswer(call ->
      new MaBandStrategy(marketData, responseMapper).execute(call.getArgument(0)));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
    "익절 비율 0|\"takeProfitRate\":0",
    "손절 비율 0|\"stopLossRate\":0",
    "손절 비율 1|\"stopLossRate\":1"})
  @DisplayName("조건부 매수 전략의 익절 비율이 0 이하이거나 손절 비율이 0 초과 1 미만 밖이면 400 과 INVALID_SELL_RATE")
  void sellRatesRejected(String label, String fields) throws Exception {
    runStrategies();

    postJson("/strategy/conditional", sellingBody("conditional") + "," + fields + "}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_SELL_RATE"));
  }

  // 경로마다 매도 규칙 칸 앞까지의 요청 본문
  private static String sellingBody(String path) {
    return switch (path) {
      case "conditional" -> "{\"symbol\":\"AAPL\"," + PERIOD + ",\"amountPerPurchase\":100000,\"dropPercentage\":0.05";
      default -> "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":10000000";
    };
  }

  @Test
  @DisplayName("조건부 · 이동평균 교차 · 밴드 전략은 트레일링 스탑 하락 비율(1 미만) · 팔 비율(1 이하)을 그대로 넘긴다")
  void trailingStopPassedThrough() throws Exception {
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("AAPL", StrategyType.CONDITIONAL_PURCHASE, "1"));
    given(backtestAnalysisService.runMaCrossStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_CROSS, "1"));
    given(backtestAnalysisService.runMaBandStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_BAND, "1"));
    String trailing = ",\"trailingStopRate\":0.99,\"trailingSellRate\":1}";

    for (String path : List.of("conditional", "ma-cross", "ma-band")) {
      postJson("/strategy/" + path, sellingBody(path) + trailing).andExpect(status().isOk());
    }

    BigDecimal stop = new BigDecimal("0.99");
    verify(backtestAnalysisService).runConditionalStrategy(argThat(r ->
      r.getTrailingStopRate().compareTo(stop) == 0 && r.getTrailingSellRate().compareTo(BigDecimal.ONE) == 0));
    verify(backtestAnalysisService).runMaCrossStrategy(argThat((MaCrossStrategyRequest r) ->
      r.getTrailingStopRate().compareTo(stop) == 0 && r.getTrailingSellRate().compareTo(BigDecimal.ONE) == 0));
    verify(backtestAnalysisService).runMaBandStrategy(argThat((MaBandStrategyRequest r) ->
      r.getTrailingStopRate().compareTo(stop) == 0 && r.getTrailingSellRate().compareTo(BigDecimal.ONE) == 0));
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource(delimiter = '|', value = {
    "conditional|하락 비율 0|\"trailingStopRate\":0",
    "conditional|팔 비율만|\"trailingSellRate\":0.5",
    "ma-cross|하락 비율 1|\"trailingStopRate\":1",
    "ma-cross|팔 비율 0|\"trailingStopRate\":0.1,\"trailingSellRate\":0",
    "ma-cross|팔 비율만|\"trailingSellRate\":0.5",
    "ma-band|팔 비율 1 초과|\"trailingStopRate\":0.1,\"trailingSellRate\":1.01",
    "ma-band|팔 비율만|\"trailingSellRate\":0.5"})
  @DisplayName("트레일링 스탑 하락 비율이 0 초과 1 미만 밖이거나 팔 비율이 0 초과 1 이하 밖이거나 팔 비율만 있으면 400 과 INVALID_TRAILING_STOP")
  void trailingStopRejected(String path, String label, String fields) throws Exception {
    runStrategies();

    postJson("/strategy/" + path, sellingBody(path) + "," + fields + "}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_TRAILING_STOP"));
  }

  @Test
  @DisplayName("조건부 · 이동평균 교차 · 밴드 전략은 부분 익절 단계를 순서대로 그대로 넘긴다")
  void partialTakeProfitsPassedThrough() throws Exception {
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("AAPL", StrategyType.CONDITIONAL_PURCHASE, "1"));
    given(backtestAnalysisService.runMaCrossStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_CROSS, "1"));
    given(backtestAnalysisService.runMaBandStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_BAND, "1"));
    String ladder = ",\"partialTakeProfits\":[{\"gainRate\":0.1,\"sellRate\":0.5},{\"gainRate\":0.3,\"sellRate\":1}]}";

    for (String path : List.of("conditional", "ma-cross", "ma-band")) {
      postJson("/strategy/" + path, sellingBody(path) + ladder).andExpect(status().isOk());
    }

    List<PartialTakeProfit> steps = List.of(new PartialTakeProfit(new BigDecimal("0.1"), new BigDecimal("0.5")),
      new PartialTakeProfit(new BigDecimal("0.3"), BigDecimal.ONE));
    verify(backtestAnalysisService).runConditionalStrategy(argThat(r -> steps.equals(r.getPartialTakeProfits())));
    verify(backtestAnalysisService).runMaCrossStrategy(argThat((MaCrossStrategyRequest r) ->
      steps.equals(r.getPartialTakeProfits())));
    verify(backtestAnalysisService).runMaBandStrategy(argThat((MaBandStrategyRequest r) ->
      steps.equals(r.getPartialTakeProfits())));
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource(delimiter = '|', value = {
    "conditional|6단계|[{\"gainRate\":0.1,\"sellRate\":0.1},{\"gainRate\":0.2,\"sellRate\":0.1},{\"gainRate\":0.3,\"sellRate\":0.1},{\"gainRate\":0.4,\"sellRate\":0.1},{\"gainRate\":0.5,\"sellRate\":0.1},{\"gainRate\":0.6,\"sellRate\":0.1}]",
    "conditional|수익률 0|[{\"gainRate\":0,\"sellRate\":0.5}]",
    "conditional|수익률 내림|[{\"gainRate\":0.3,\"sellRate\":0.5},{\"gainRate\":0.2,\"sellRate\":0.5}]",
    "ma-cross|수익률이 앞 단계와 같음|[{\"gainRate\":0.2,\"sellRate\":0.5},{\"gainRate\":0.2,\"sellRate\":0.5}]",
    "ma-cross|팔 비율 0|[{\"gainRate\":0.2,\"sellRate\":0}]",
    "ma-cross|수익률 비움|[{\"gainRate\":0.1,\"sellRate\":0.5},{\"sellRate\":0.5}]",
    "ma-band|팔 비율 1 초과|[{\"gainRate\":0.2,\"sellRate\":1.01}]",
    "ma-band|팔 비율 비움|[{\"gainRate\":0.2}]",
    "ma-band|비운 단계|[null]"})
  @DisplayName("부분 익절이 6단계 이상이거나 수익률이 0 초과 오름차순이 아니거나 팔 비율이 0 초과 1 이하 밖이거나 비운 칸이 있으면 400 과 INVALID_PARTIAL_TAKE_PROFIT")
  void partialTakeProfitsRejected(String path, String label, String steps) throws Exception {
    runStrategies();

    postJson("/strategy/" + path, sellingBody(path) + ",\"partialTakeProfits\":" + steps + "}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_PARTIAL_TAKE_PROFIT"));
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
  @DisplayName("전략 비교는 전략 목록과 칩의 회당 금액(monthlyAmount 로 보내도 받음) · 주기 · 첫 매수일 · 최대 횟수를 넘긴다")
  void compareStrategies() throws Exception {
    given(backtestAnalysisService.compareStrategies(any())).willReturn(comparison("DCA"));

    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"DCA\",\"monthlyAmount\":100000,\"totalInvestmentLimit\":1000000},"
      + "{\"strategyType\":\"DCA\",\"amountPerPurchase\":50000,\"frequency\":\"WEEKLY\",\"firstPurchaseDate\":\"2024-01-03\"},"
      + "{\"strategyType\":\"CONDITIONAL_PURCHASE\",\"amountPerPurchase\":200000,\"dropPercentage\":0.05,\"maxPurchases\":3},"
      + "{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bestPerformer.name").value("DCA"));

    verify(backtestAnalysisService).compareStrategies(argThat(r -> r.getStrategies().size() == 4
      && r.getStrategies().get(0).getStrategyType() == StrategyType.DCA
      && r.getStrategies().get(0).getAmountPerPurchase().intValue() == 100000
      && r.getStrategies().get(1).getFrequency() == DcaFrequency.WEEKLY
      && r.getStrategies().get(1).getFirstPurchaseDate().equals(LocalDate.of(2024, 1, 3))
      && r.getStrategies().get(2).getAmountPerPurchase().intValue() == 200000
      && r.getStrategies().get(2).getMaxPurchases() == 3));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {
    "{\"strategyType\":\"DCA\",\"purchaseDay\":15}",
    "{\"strategyType\":\"CONDITIONAL_PURCHASE\",\"dropPercentage\":0.05}",
    "{\"strategyType\":\"DCA\",\"amountPerPurchase\":0}",
    "{\"strategyType\":\"CONDITIONAL_PURCHASE\",\"amountPerPurchase\":100000,\"dropPercentage\":0.05,\"maxPurchases\":0}",
    "{\"strategyType\":\"DCA\",\"amountPerPurchase\":100000,\"frequency\":\"HOURLY\"}"})
  @DisplayName("전략 비교의 적립식 · 조건부 칩에 회당 금액이 없거나 회당 금액 · 최대 횟수가 0 이거나 주기가 매일 · 매주 · 매달 밖이면 400 이고 서비스까지 가지 않는다")
  void compareStrategies_InvalidChip(String chip) throws Exception {
    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[" + chip + ",{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).compareStrategies(any());
  }

  @Test
  @DisplayName("전략 비교의 이동평균 칩은 회당 금액 없이 짧은 · 긴 기간을 받아 그대로 넘기고 기간을 비워도 된다")
  void compareStrategies_MaCrossChip() throws Exception {
    given(backtestAnalysisService.compareStrategies(any())).willReturn(comparison("이동평균"));

    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"MA_CROSS\",\"name\":\"이동평균\",\"shortWindow\":5,\"longWindow\":20},"
      + "{\"strategyType\":\"MA_CROSS\"},{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bestPerformer.name").value("이동평균"));

    verify(backtestAnalysisService).compareStrategies(argThat(r -> r.getStrategies().size() == 3
      && r.getStrategies().get(0).getStrategyType() == StrategyType.MA_CROSS
      && r.getStrategies().get(0).getShortWindow() == 5 && r.getStrategies().get(0).getLongWindow() == 20
      && r.getStrategies().get(1).getShortWindow() == null && r.getStrategies().get(1).getLongWindow() == null));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"\"shortWindow\":1", "\"longWindow\":251", "\"shortWindow\":60,\"longWindow\":60",
    "\"shortWindow\":70"})
  @DisplayName("전략 비교의 이동평균 칩 기간이 2 ~ 250 밖이거나 짧은 기간이 긴 기간보다 작지 않으면 400 이고 서비스까지 가지 않는다")
  void compareStrategies_InvalidMaCrossChip(String windows) throws Exception {
    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"MA_CROSS\"," + windows + "},{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).compareStrategies(any());
  }

  @Test
  @DisplayName("전략 비교의 밴드 칩은 회당 금액 없이 평균 기간 · 위 · 아래 비율을 받아 그대로 넘기고 비워도 된다")
  void compareStrategies_MaBandChip() throws Exception {
    given(backtestAnalysisService.compareStrategies(any())).willReturn(comparison("밴드"));

    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"MA_BAND\",\"name\":\"밴드\",\"bandWindow\":100,\"upperRate\":0.03,"
      + "\"lowerRate\":0.05},{\"strategyType\":\"MA_BAND\"},{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bestPerformer.name").value("밴드"));

    verify(backtestAnalysisService).compareStrategies(argThat(r -> r.getStrategies().size() == 3
      && r.getStrategies().get(0).getStrategyType() == StrategyType.MA_BAND
      && r.getStrategies().get(0).getBandWindow() == 100
      && r.getStrategies().get(0).getUpperRate().compareTo(new BigDecimal("0.03")) == 0
      && r.getStrategies().get(0).getLowerRate().compareTo(new BigDecimal("0.05")) == 0
      && r.getStrategies().get(1).getBandWindow() == null && r.getStrategies().get(1).getUpperRate() == null
      && r.getStrategies().get(1).getLowerRate() == null));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"\"bandWindow\":1", "\"bandWindow\":251", "\"upperRate\":-0.01", "\"upperRate\":1",
    "\"lowerRate\":-0.01", "\"lowerRate\":1"})
  @DisplayName("전략 비교의 밴드 칩 평균 기간이 2 ~ 250 밖이거나 비율이 0 이상 1 미만 밖이면 400 이고 서비스까지 가지 않는다")
  void compareStrategies_InvalidMaBandChip(String band) throws Exception {
    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"MA_BAND\"," + band + "},{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).compareStrategies(any());
  }

  @Test
  @DisplayName("전략 비교의 모든 전략이 매수 0회라 최고 수익 항목이 없어도 200 이다")
  void compareStrategies_NoBestPerformer() throws Exception {
    ComparisonItem none = ComparisonItem.builder().name("DCA").totalInvested(BigDecimal.ZERO)
      .totalReturnPercent(BigDecimal.ZERO).build();
    given(backtestAnalysisService.compareStrategies(any()))
      .willReturn(ComparisonResponse.builder().items(List.of(none)).build());

    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000,"
      + "\"strategies\":[{\"strategyType\":\"DCA\",\"amountPerPurchase\":100000},{\"strategyType\":\"SIMPLE\"}]}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bestPerformer").doesNotExist());
  }

  @Test
  @DisplayName("포트폴리오는 토큰 없이 실행되고 줄 금액 방식 · 종목 줄(줄 전략 포함) · 매수 방식 · 리밸런싱 주기를 그대로 넘긴다")
  void portfolio() throws Exception {
    given(backtestAnalysisService.runPortfolio(any()))
      .willReturn(PortfolioResponse.builder().totalReturnPercent(new BigDecimal("42.50")).assets(List.of()).build());

    postJson("/portfolio", PORTFOLIO + "}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalReturnPercent").value(42.50));

    verify(backtestAnalysisService).runPortfolio(argThat((PortfolioRequest r) ->
      r.getAllocation() == PortfolioAllocation.WEIGHT && r.getTotalAmount().intValue() == 10000000
        && r.getAssets().size() == 2 && "QQQ".equals(r.getAssets().get(1).symbol())
        && r.getAssets().get(0).weightPercent().intValue() == 60 && r.getAssets().get(0).strategy() == null
        && r.getAssets().get(1).strategy().type() == PortfolioStrategyType.LUMP_SUM
        && r.getMethod() == PortfolioMethod.DCA
        && r.getRebalance() == PortfolioRebalance.QUARTERLY && r.getUserId() == null));
    assertThat(meterRegistry.get("backtest.run").tags("kind", "portfolio", "outcome", "success").timer().count())
      .isEqualTo(1);
  }

  @Test
  @DisplayName("포트폴리오 요청 검사가 실패하면 400 과 그 코드를 돌려준다")
  void portfolio_InvalidRequest() throws Exception {
    given(backtestAnalysisService.runPortfolio(any())).willThrow(new BacktestException(BacktestResponse.INVALID_PORTFOLIO));

    postJson("/portfolio", "{" + PERIOD + "}")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errorCode").value("INVALID_PORTFOLIO"));
  }

  @Test
  @DisplayName("포트폴리오 리밸런싱 주기가 없음 · 매년 · 분기마다 · 매달 밖이면 400 이고 서비스까지 가지 않는다")
  void portfolio_RebalanceInvalid() throws Exception {
    postJson("/portfolio", "{" + PERIOD + ",\"rebalance\":\"WEEKLY\"}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runPortfolio(any());
  }

  @Test
  @DisplayName("포트폴리오 줄 전략이 고를 수 있는 전략 밖이면 400 이고 서비스까지 가지 않는다")
  void portfolio_LineStrategyInvalid() throws Exception {
    postJson("/portfolio", "{" + PERIOD + ",\"assets\":[{\"symbol\":\"SPY\",\"amount\":1000000,"
      + "\"strategy\":{\"type\":\"CONDITIONAL_PURCHASE\"}}]}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runPortfolio(any());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"\"tradingFeeRate\":1", "\"tradingFeeRate\":-0.1", "\"purchaseFxRate\":0", "\"currentFxRate\":-1"})
  @DisplayName("포트폴리오의 수수료율이 0 이상 1 미만 밖이거나 수동 환율이 0 이하면 400 이고 서비스까지 가지 않는다")
  void portfolio_RateOutOfRange(String field) throws Exception {
    postJson("/portfolio", PORTFOLIO + "," + field + "}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).runPortfolio(any());
  }

  @Test
  @DisplayName("분석 일곱 경로 모두 이력 주인을 본문이 아니라 토큰의 사용자로 정한다")
  void ownerFromToken() throws Exception {
    given(jwtDecoder.decode(TOKEN)).willReturn(Jwt.withTokenValue(TOKEN).header("alg", "none").subject(USER)
      .claim("email", EMAIL).build());
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "1"));
    given(backtestAnalysisService.runConditionalStrategy(any()))
      .willReturn(strategy("TSLA", StrategyType.CONDITIONAL_PURCHASE, "1"));
    given(backtestAnalysisService.runMaCrossStrategy(any())).willReturn(strategy("AAPL", StrategyType.MA_CROSS, "1"));
    given(backtestAnalysisService.runMaBandStrategy(any())).willReturn(strategy("SPY", StrategyType.MA_BAND, "1"));
    given(backtestAnalysisService.compareSymbols(any())).willReturn(comparison("MSFT"));
    given(backtestAnalysisService.compareStrategies(any())).willReturn(comparison("DCA"));
    given(backtestAnalysisService.runPortfolio(any())).willReturn(PortfolioResponse.builder().build());

    postJsonWithToken("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15" + VICTIM)
      .andExpect(status().isOk());
    postJsonWithToken("/strategy/conditional",
      "{\"symbol\":\"TSLA\"," + PERIOD + ",\"amountPerPurchase\":100000,\"totalInvestment\":1000000,\"dropPercentage\":5" + VICTIM)
      .andExpect(status().isOk());
    postJsonWithToken("/strategy/ma-cross", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000" + VICTIM)
      .andExpect(status().isOk());
    postJsonWithToken("/strategy/ma-band", "{\"symbol\":\"SPY\"," + PERIOD + ",\"investmentAmount\":1000000" + VICTIM)
      .andExpect(status().isOk());
    postJsonWithToken("/compare/symbols", "{\"symbols\":[\"AAPL\",\"MSFT\"]," + PERIOD + ",\"investmentAmount\":1000000" + VICTIM)
      .andExpect(status().isOk());
    postJsonWithToken("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"DCA\",\"monthlyAmount\":100000},{\"strategyType\":\"SIMPLE\"}]" + VICTIM)
      .andExpect(status().isOk());
    postJsonWithToken("/portfolio", PORTFOLIO + VICTIM)
      .andExpect(status().isOk());

    verify(backtestAnalysisService).runPortfolio(argThat(r -> USER.equals(r.getUserId())));
    verify(backtestAnalysisService).runDcaStrategy(argThat(r -> USER.equals(r.getUserId())));
    verify(backtestAnalysisService).runConditionalStrategy(argThat(r -> USER.equals(r.getUserId())));
    verify(backtestAnalysisService).runMaCrossStrategy(argThat(r -> USER.equals(r.getUserId())));
    verify(backtestAnalysisService).runMaBandStrategy(argThat(r -> USER.equals(r.getUserId())));
    verify(backtestAnalysisService).compareSymbols(argThat(r -> USER.equals(r.getUserId())));
    verify(backtestAnalysisService).compareStrategies(argThat(r -> USER.equals(r.getUserId())));
  }

  @Test
  @DisplayName("토큰 없이 돌린 분석은 본문의 userId 를 버려 이력 주인이 없다")
  void anonymousDropsBodyUser() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "1"));

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000,\"purchaseDay\":15" + VICTIM)
      .andExpect(status().isOk());

    verify(backtestAnalysisService).runDcaStrategy(argThat(r -> r.getUserId() == null));
  }

  @Test
  @DisplayName("열려 있는 건 POST 뿐이다. 같은 경로라도 GET 은 토큰이 없으면 401")
  void onlyPostIsOpen() throws Exception {
    mockMvc.perform(get(BASE + "/strategy/dca")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("컨트롤러가 없는 내부 경로는 열어 두지 않는다. 토큰이 없으면 401")
  void oldInternalPathsAreClosed() throws Exception {
    mockMvc.perform(post("/api/internal/analysis/strategy/dca")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/internal/trading-simulation/simulation")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("시점 비교 · 최적 시점 분석 경로는 없다. 토큰 없이 POST 해도 404")
  void timingPathsAreGone() throws Exception {
    postJson("/compare/timing", "{}").andExpect(status().isNotFound());
    postJson("/timing/optimal", "{}").andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("전략 비교에 전략 타입이 없는 항목이 있으면 400")
  void compareStrategies_MissingStrategyType() throws Exception {
    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"DCA\",\"monthlyAmount\":100000},{\"name\":\"타입 없음\"}]}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).compareStrategies(any());
  }

  @ParameterizedTest(name = "투자 주기 {0} · 투자일 {1}")
  @CsvSource({"0, 15", "-1, 15", "13, 15", "1, 0", "1, 32"})
  @DisplayName("전략 비교의 적립식 항목도 투자 주기가 1 ~ 12 밖이거나 투자일이 1 ~ 31 밖이면 400")
  void compareStrategies_DcaScheduleOutOfRange(int interval, int day) throws Exception {
    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"DCA\",\"monthlyAmount\":100000," + schedule(interval, day) + "}]}")
      .andExpect(status().isBadRequest());

    verify(backtestAnalysisService, never()).compareStrategies(any());
  }

  @Test
  @DisplayName("투자 주기 12 · 투자일 31 은 적립식 · 전략 비교 모두 통과한다")
  void dcaScheduleUpperBoundsPass() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any())).willReturn(strategy("AAPL", StrategyType.DCA, "1"));
    given(backtestAnalysisService.compareStrategies(any())).willReturn(comparison("DCA"));

    postJson("/strategy/dca", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"monthlyAmount\":100000," + schedule(12, 31) + "}")
      .andExpect(status().isOk());
    postJson("/compare/strategies", "{\"symbol\":\"AAPL\"," + PERIOD + ",\"investmentAmount\":1000000,"
      + "\"strategies\":[{\"strategyType\":\"DCA\",\"monthlyAmount\":100000," + schedule(12, 31) + "}]}")
      .andExpect(status().isOk());
  }
}
