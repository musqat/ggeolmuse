package com.muscat.marketdata.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.marketdata.common.enums.StockSort;
import com.muscat.marketdata.common.exception.GlobalExceptionHandler;
import com.muscat.marketdata.config.security.SecurityConfig;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.domain.service.DividendService;
import com.muscat.marketdata.domain.service.FxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// MarketDataApplication 의 @ComponentScan 이 슬라이스 필터를 덮어써서 띄울 클래스를 직접 고른다. 보안은 실제 설정을 쓴다
@WebMvcTest(MarketController.class)
@ContextConfiguration(classes = {MarketController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("MarketController 공개 시세 API")
class MarketControllerTest {

  private static final String BASE = "/api/market";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CandleService candleService;

  @MockitoBean
  private DividendService dividendService;

  @MockitoBean
  private AssetService assetService;

  @MockitoBean
  private FxRateService fxRateService;

  // 공개 경로라 디코더를 부르지 않는다. 실제 Keycloak 에 붙지 않게 막아 둔다
  @MockitoBean
  private JwtDecoder jwtDecoder;

  private static OHLCPriceDto candle(String symbol, String close) {
    return OHLCPriceDto.builder().symbol(symbol).date(LocalDate.of(2024, 3, 4)).closePrice(new BigDecimal(close))
      .available(true).build();
  }

  private static StockPriceDto price(String symbol, String current) {
    return StockPriceDto.builder().symbol(symbol).currentPrice(new BigDecimal(current)).available(true).build();
  }

  private static FxRate fx(LocalDate date, String rate) {
    return FxRate.builder().date(date).rate(new BigDecimal(rate)).build();
  }

  @Test
  @DisplayName("날짜 시세는 토큰 없이 종목과 날짜를 넘겨 받는다")
  void ohlcByDate() throws Exception {
    given(candleService.getOHLCPrice("AAPL", LocalDate.of(2024, 3, 4))).willReturn(candle("AAPL", "175.1"));

    mockMvc.perform(get(BASE + "/ohlc/AAPL").param("date", "2024-03-04"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.closePrice").value(175.1));
  }

  @Test
  @DisplayName("날짜를 빼면 400 이고 서비스까지 가지 않는다")
  void ohlcMissingDate() throws Exception {
    mockMvc.perform(get(BASE + "/ohlc/AAPL"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.parameterName").value("date"));

    verify(candleService, never()).getOHLCPrice(anyString(), any());
  }

  @Test
  @DisplayName("종목 코드가 대문자 형식이 아니면 400 이고 서비스까지 가지 않는다")
  void invalidSymbol() throws Exception {
    mockMvc.perform(get(BASE + "/price/aapl"))
      .andExpect(status().isBadRequest());

    verify(candleService, never()).getCurrentPrice(anyString());
  }

  @Test
  @DisplayName("점 · 하이픈 · ^ 이 든 종목도 받는다(BRK-B · BRK.B · ^GSPC)")
  void symbolsWithDotHyphenCaret() throws Exception {
    for (String symbol : List.of("BRK-B", "BRK.B", "^GSPC")) {
      given(candleService.getCurrentPrice(symbol)).willReturn(price(symbol, "500"));

      mockMvc.perform(get(BASE + "/price/{symbol}", symbol))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentPrice").value(500));
    }
    given(candleService.getOHLCPrice("BRK-B", LocalDate.of(2024, 3, 4))).willReturn(candle("BRK-B", "410"));
    mockMvc.perform(get(BASE + "/ohlc/{symbol}", "BRK-B").param("date", "2024-03-04"))
      .andExpect(status().isOk());
  }

  @Test
  @DisplayName("여러 종목 현재가는 한 번에 50개까지만 받는다")
  void currentPricesLimit() throws Exception {
    String[] symbols = java.util.stream.IntStream.range(0, 51).mapToObj(i -> "S" + i).toArray(String[]::new);

    mockMvc.perform(get(BASE + "/prices").param("symbols", symbols))
      .andExpect(status().isBadRequest());

    verify(candleService, never()).getCurrentPrice(anyString());
  }

  @Test
  @DisplayName("현재가는 종목을 넘겨 받는다")
  void currentPrice() throws Exception {
    given(candleService.getCurrentPrice("AAPL")).willReturn(price("AAPL", "230"));

    mockMvc.perform(get(BASE + "/price/AAPL"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.currentPrice").value(230));
  }

  @Test
  @DisplayName("여러 종목 현재가는 대문자로 바꿔 조회하고, 실패한 종목은 빼고 돌려준다")
  void currentPrices() throws Exception {
    given(candleService.getCurrentPrice("AAPL")).willReturn(price("AAPL", "230"));
    given(candleService.getCurrentPrice("NOPE")).willThrow(new IllegalStateException("no candle"));

    mockMvc.perform(get(BASE + "/prices").param("symbols", "aapl", "NOPE"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.AAPL.currentPrice").value(230))
      .andExpect(jsonPath("$.NOPE").doesNotExist());
  }

  @Test
  @DisplayName("날짜 환율은 있으면 200, 없으면 404")
  void fxByDate() throws Exception {
    given(fxRateService.findByDate(LocalDate.of(2024, 3, 4))).willReturn(fx(LocalDate.of(2024, 3, 4), "1330.5"));

    mockMvc.perform(get(BASE + "/fx/2024-03-04"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.rate").value(1330.5));
    mockMvc.perform(get(BASE + "/fx/2024-03-05")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("최신 환율은 있으면 200, 없으면 404")
  void latestFx() throws Exception {
    given(fxRateService.getLatestRate()).willReturn(Optional.of(fx(LocalDate.of(2024, 3, 4), "1330.5")), Optional.empty());

    mockMvc.perform(get(BASE + "/fx/latest"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.date").value("2024-03-04"));
    mockMvc.perform(get(BASE + "/fx/latest")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("배당 이력은 기간을 주지 않으면 비운 채 넘긴다")
  void dividendHistory() throws Exception {
    given(dividendService.getDividendHistory("AAPL", null, null)).willReturn(List.of(
      DividendDto.builder().symbol("AAPL").exDate(LocalDate.of(2024, 2, 9)).amount(new BigDecimal("0.24")).build()));

    mockMvc.perform(get(BASE + "/dividend/AAPL"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].exDate").value("2024-02-09"));
  }

  @Test
  @DisplayName("활성 종목은 종목 코드만 담아 돌려준다")
  void activeSymbols() throws Exception {
    given(assetService.getActiveSymbols()).willReturn(List.of("AAPL", "MSFT"));

    mockMvc.perform(get(BASE + "/symbols"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[1].symbol").value("MSFT"));
  }

  @Test
  @DisplayName("종목 목록은 기본이 첫 페이지 50건 · 시총 내림차순 · 유형 전체")
  void stocksDefaults() throws Exception {
    given(candleService.getAllStocksWithPrices(any(), eq("desc"), eq(null), eq(StockSort.MARKET_CAP))).willReturn(new PageImpl<>(List.of(price("AAPL", "230"))));

    mockMvc.perform(get(BASE + "/stocks"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[0].symbol").value("AAPL"));

    verify(candleService).getAllStocksWithPrices(PageRequest.of(0, 50), "desc", null, StockSort.MARKET_CAP);
  }

  @Test
  @DisplayName("종목 목록은 페이지 · 정렬 방향 · 유형 · 정렬 기준을 그대로 넘긴다")
  void stocksWithParams() throws Exception {
    given(candleService.getAllStocksWithPrices(any(), anyString(), anyString(), any())).willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get(BASE + "/stocks").param("page", "2").param("size", "10").param("direction", "asc").param("assetType", "ETF")
        .param("sort", "dividendYield"))
      .andExpect(status().isOk());

    verify(candleService).getAllStocksWithPrices(PageRequest.of(2, 10), "asc", "ETF", StockSort.DIVIDEND_YIELD);
  }

  @Test
  @DisplayName("종목 목록의 정렬 기준이 시가총액 · 배당수익률이 아니면 400 이고 서비스를 부르지 않는다")
  void stocksUnknownSort() throws Exception {
    mockMvc.perform(get(BASE + "/stocks").param("sort", "price"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.detail").value("유효하지 않은 정렬 기준입니다"));

    verify(candleService, never()).getAllStocksWithPrices(any(), any(), any(), any());
  }

  @Test
  @DisplayName("종목 목록의 페이지 번호가 음수이거나 크기가 1~200 밖이면 400 이고 서비스를 부르지 않는다")
  void stocksPageBounds() throws Exception {
    mockMvc.perform(get(BASE + "/stocks").param("page", "-1")).andExpect(status().isBadRequest());
    mockMvc.perform(get(BASE + "/stocks").param("size", "0")).andExpect(status().isBadRequest());
    mockMvc.perform(get(BASE + "/stocks").param("size", "201")).andExpect(status().isBadRequest());

    verify(candleService, never()).getAllStocksWithPrices(any(), any(), any(), any());
  }

  @Test
  @DisplayName("여러 종목 기간 시세 · 배당 포함 캔들은 종목과 기간을 넘긴다")
  void multipleAndWithDividends() throws Exception {
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 3, 31);
    given(candleService.getMultipleOHLCPrices(List.of("AAPL", "MSFT"), start, end)).willReturn(List.of(candle("MSFT", "400")));
    given(candleService.getCandlesWithDividends("AAPL", start, end)).willReturn(List.of(candle("AAPL", "175")));

    mockMvc.perform(get(BASE + "/ohlc/multiple").param("symbols", "AAPL", "MSFT").param("startDate", "2024-01-01").param("endDate", "2024-03-31"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].symbol").value("MSFT"));
    mockMvc.perform(get(BASE + "/ohlc/AAPL/with-dividends").param("startDate", "2024-01-01").param("endDate", "2024-03-31"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].closePrice").value(175));
  }

  @Test
  @DisplayName("배당 경로의 high-yield 는 종목 코드 형식이 아니라 400 이고 배당을 조회하지 않는다")
  void highYieldRemoved() throws Exception {
    mockMvc.perform(get(BASE + "/dividend/high-yield").param("minAmount", "1.5")).andExpect(status().isBadRequest());

    verifyNoInteractions(dividendService);
  }
  @Test
  @DisplayName("일괄 환율은 토큰 없이 POST 로 받고, 날짜 문자열을 키로 돌려준다")
  void bulkFx() throws Exception {
    given(fxRateService.findByDates(List.of(LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 5))))
      .willReturn(Map.of(LocalDate.of(2024, 3, 4), new BigDecimal("1330.5")));

    mockMvc.perform(post(BASE + "/fx/bulk").contentType(MediaType.APPLICATION_JSON).content("[\"2024-03-04\",\"2024-03-05\"]"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$['2024-03-04']").value(1330.5))
      .andExpect(jsonPath("$['2024-03-05']").doesNotExist());
  }
}
