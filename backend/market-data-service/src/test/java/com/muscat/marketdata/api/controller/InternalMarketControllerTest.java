package com.muscat.marketdata.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.commonlib.security.ServiceSecurity;
import com.muscat.marketdata.common.exception.GlobalExceptionHandler;
import com.muscat.marketdata.config.security.SecurityConfig;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.SplitDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.domain.service.DividendService;
import com.muscat.marketdata.domain.service.FxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 서비스 토큰(realm 역할 service)으로 부르는 내부 경로
@WebMvcTest(InternalMarketController.class)
@ContextConfiguration(classes = {InternalMarketController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("InternalMarketController 내부 시세 API")
class InternalMarketControllerTest {

  private static final String BASE = "/api/internal/market";
  private static final LocalDate START = LocalDate.of(2024, 1, 1);
  private static final LocalDate END = LocalDate.of(2024, 3, 31);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private WebApplicationContext context;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private Filter securityFilter;

  // 요청마다 서비스 토큰을 싣는 MockMvc
  private MockMvc service;

  @MockitoBean
  private CandleService candleService;

  @MockitoBean
  private DividendService dividendService;

  @MockitoBean
  private FxRateService fxRateService;

  @MockitoBean
  private FxRateRepository fxRateRepository;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private static OHLCPriceDto candle(LocalDate date, String close) {
    return OHLCPriceDto.builder().symbol("AAPL").date(date).closePrice(new BigDecimal(close)).available(true).build();
  }

  private static FxRate fx(LocalDate date, String rate) {
    return FxRate.builder().date(date).rate(new BigDecimal(rate)).build();
  }

  private static Jwt token(String value, List<String> roles) {
    return Jwt.withTokenValue(value).header("alg", "none").subject("sub-" + value)
      .claim("realm_access", Map.of("roles", roles)).build();
  }

  @BeforeEach
  void serviceToken() {
    given(jwtDecoder.decode("service-token")).willReturn(token("service-token", List.of(ServiceSecurity.SERVICE_ROLE)));
    given(jwtDecoder.decode("user-token")).willReturn(token("user-token", List.of()));
    given(jwtDecoder.decode("admin-token")).willReturn(token("admin-token", List.of("admin")));
    service = MockMvcBuilders.webAppContextSetup(context)
      .addFilters(securityFilter)
      .defaultRequest(get("/").header("Authorization", "Bearer service-token"))
      .build();
  }

  @Test
  @DisplayName("토큰이 없으면 401, 서비스 역할이 없는 사용자 · 관리자 토큰은 403 이고 시세 · 환율을 조회하지 않는다")
  void withoutServiceRole_Rejected() throws Exception {
    mockMvc.perform(get(BASE + "/price/AAPL")).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/price/AAPL").header("Authorization", "Bearer user-token"))
      .andExpect(status().isForbidden());
    mockMvc.perform(get(BASE + "/price/AAPL").header("Authorization", "Bearer admin-token"))
      .andExpect(status().isForbidden());
    mockMvc.perform(post(BASE + "/fx/bulk").contentType(MediaType.APPLICATION_JSON).content("[\"2024-01-01\"]"))
      .andExpect(status().isUnauthorized());

    verify(candleService, never()).getCurrentPrice(anyString());
    verify(fxRateService, never()).findByDates(any());
  }

  @Test
  @DisplayName("내부 시세 조회도 하이픈 · 점이 든 종목을 받는다")
  void symbolsWithDotHyphen() throws Exception {
    service.perform(get(BASE + "/ohlc/{symbol}/range", "BRK-B").param("startDate", "2024-01-01").param("endDate", "2024-03-31"))
      .andExpect(status().isOk());
    service.perform(get(BASE + "/ohlc/{symbol}/range", "BRK.B").param("startDate", "2024-01-01").param("endDate", "2024-03-31"))
      .andExpect(status().isOk());
  }

  @Test
  @DisplayName("날짜 시세 · 기간 시세는 종목과 날짜를 넘겨 받는다")
  void candles() throws Exception {
    given(candleService.getOHLCPrice("AAPL", START)).willReturn(candle(START, "185.6"));
    given(candleService.getOHLCPriceRange("AAPL", START, END)).willReturn(List.of(candle(START, "185.6"), candle(END, "171.5")));

    service.perform(get(BASE + "/ohlc/AAPL").param("date", "2024-01-01"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.closePrice").value(185.6));
    service.perform(get(BASE + "/ohlc/AAPL/range").param("startDate", "2024-01-01").param("endDate", "2024-03-31"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[1].date").value("2024-03-31"));
  }

  @Test
  @DisplayName("기간 시세에 종료일이 없으면 400 이고 서비스까지 가지 않는다")
  void rangeMissingEnd() throws Exception {
    service.perform(get(BASE + "/ohlc/AAPL/range").param("startDate", "2024-01-01"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.parameterName").value("endDate"));

    verify(candleService, never()).getOHLCPriceRange(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("현재가는 하나 · 여럿 모두 받고, 여럿일 때 실패한 종목은 뺀다")
  void prices() throws Exception {
    given(candleService.getCurrentPrice("AAPL")).willReturn(StockPriceDto.builder().symbol("AAPL").currentPrice(new BigDecimal("230")).build());
    given(candleService.getCurrentPrice("NOPE")).willThrow(new IllegalStateException("no candle"));

    service.perform(get(BASE + "/price/AAPL"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.currentPrice").value(230));
    service.perform(get(BASE + "/prices").param("symbols", "aapl", "nope"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.AAPL.currentPrice").value(230))
      .andExpect(jsonPath("$.NOPE").doesNotExist());
  }

  @Test
  @DisplayName("현재가 응답에 상장 여부와 날짜 문자열이 실린다")
  void priceCarriesListing() throws Exception {
    given(candleService.getCurrentPrice("AAPL")).willReturn(StockPriceDto.builder()
      .symbol("AAPL").currentPrice(new BigDecimal("230")).date(LocalDate.of(2026, 10, 15))
      .available(true).active(true).build());

    service.perform(get(BASE + "/price/AAPL"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.date").value("2026-10-15"));
  }

  @Test
  @DisplayName("날짜 환율 · 최신 환율은 있으면 200, 없으면 404")
  void fxRates() throws Exception {
    given(fxRateService.findByDate(START)).willReturn(fx(START, "1300.5"));
    given(fxRateService.getLatestRate()).willReturn(Optional.of(fx(END, "1330")), Optional.empty());

    service.perform(get(BASE + "/fx/2024-01-01"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.rate").value(1300.5));
    service.perform(get(BASE + "/fx/2024-01-02")).andExpect(status().isNotFound());
    service.perform(get(BASE + "/fx/latest"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.date").value("2024-03-31"));
    service.perform(get(BASE + "/fx/latest")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("일괄 환율은 날짜 목록을 받아 찾은 날만 날짜 문자열을 키로 돌려준다")
  void bulkFx() throws Exception {
    given(fxRateService.findByDates(List.of(START, END))).willReturn(Map.of(END, new BigDecimal("1330")));

    service.perform(post(BASE + "/fx/bulk").contentType(MediaType.APPLICATION_JSON).content("[\"2024-01-01\",\"2024-03-31\"]"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$['2024-03-31']").value(1330))
      .andExpect(jsonPath("$['2024-01-01']").doesNotExist());
  }

  @Test
  @DisplayName("배당 이력은 기간을 그대로 넘긴다")
  void dividends() throws Exception {
    given(dividendService.getDividendHistory("AAPL", START, END)).willReturn(List.of(
      DividendDto.builder().symbol("AAPL").exDate(LocalDate.of(2024, 2, 9)).amount(new BigDecimal("0.24")).build()));

    service.perform(get(BASE + "/dividend/AAPL").param("startDate", "2024-01-01").param("endDate", "2024-03-31"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].amount").value(0.24));
  }

  @Test
  @DisplayName("분할 목록은 시작일부터 날짜 · 비율로 돌려주고 시작일이 없으면 400")
  void splits() throws Exception {
    given(candleService.getSplits("NVDA", START)).willReturn(List.of(
      new SplitDto(LocalDate.of(2024, 6, 10), new BigDecimal("10"))));

    service.perform(get(BASE + "/splits/NVDA").param("startDate", "2024-01-01"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].date").value("2024-06-10"))
      .andExpect(jsonPath("$[0].ratio").value(10));
    service.perform(get(BASE + "/splits/NVDA"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("일괄 환율의 날짜가 상한보다 많으면 400 이고 환율을 조회하지 않는다")
  void bulkFx_TooManyDates_BadRequest() throws Exception {
    String body = "[" + String.join(",", java.util.Collections.nCopies(FxRateService.MAX_BULK_DATES + 1, "\"2024-03-04\"")) + "]";

    service.perform(post(BASE + "/fx/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isBadRequest());

    verify(fxRateService, never()).findByDates(any());
  }
}
