package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDataService 단위 테스트")
class MarketDataServiceImplTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate TRADE_DATE = LocalDate.of(2024, 9, 18);

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Mock
  private TradeLogger tradeLogger;

  @InjectMocks
  private MarketDataServiceImpl marketDataService;

  /** open 216 / high 222 / low 213 / close 220 */
  private static StockPriceDto ohlc(boolean available) {
    return new StockPriceDto(
      SYMBOL, "Apple Inc.", new BigDecimal("220"), null, null, null, null,
      TRADE_DATE, null,
      new BigDecimal("216"), new BigDecimal("222"), new BigDecimal("213"), new BigDecimal("220"),
      new BigDecimal("220"), "USD", available, "EQUITY", null);
  }

  private static StockPriceDto ohlcWith(BigDecimal open, BigDecimal high, BigDecimal low,
                                        BigDecimal close) {
    return new StockPriceDto(
      SYMBOL, "Apple Inc.", close, null, null, null, null,
      TRADE_DATE, null, open, high, low, close, close, "USD", true, "EQUITY", null);
  }

  @Nested
  @DisplayName("determineTradePrice")
  class DetermineTradePrice {

    @Test
    @DisplayName("MANUAL 이면 범위 검증을 거친다")
    void manual_경로() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      BigDecimal result = marketDataService.determineTradePrice(
        SYMBOL, TRADE_DATE, PriceType.MANUAL, new BigDecimal("218"));

      // 시세 대신 입력값 반환
      assertThat(result).isEqualByComparingTo("218");
      verify(tradeLogger).logMarketDataRequest(SYMBOL, "MANUAL_VALIDATION", true, null);
    }

    @Test
    @DisplayName("MANUAL 이 아니면 OHLC 조회로 간다")
    void ohlc_경로() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      BigDecimal result = marketDataService.determineTradePrice(
        SYMBOL, TRADE_DATE, PriceType.CLOSE, null);

      assertThat(result).isEqualByComparingTo("220");
      verify(tradeLogger).logMarketDataRequest(SYMBOL, "CLOSE", true, null);
    }
  }

  @Nested
  @DisplayName("getOHLCPrice")
  class GetOHLCPrice {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"OPEN,216", "HIGH,222", "LOW,213", "CLOSE,220"})
    @DisplayName("가격 유형별로 해당 필드를 고른다")
    void 유형별_매핑(PriceType priceType, String expected) {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThat(marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, priceType))
        .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("응답이 null 이면 시세 없음으로 막는다")
    void 응답_null() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString())).willReturn(null);

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
    }

    @Test
    @DisplayName("available=false 면 시세 없음으로 막는다")
    void 데이터_없음() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(false));

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
    }

    @Test
    @DisplayName("고른 가격 필드가 null 이면 TradeException")
    void 가격_null() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlcWith(null, new BigDecimal("222"), new BigDecimal("213"),
          new BigDecimal("220")));

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.OPEN))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("MANUAL 을 직접 넘기면 지원하지 않는 유형으로 막는다")
    void manual_직접_호출() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.MANUAL))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("시세 조회가 실패해도 TradeException 하나로 나간다")
    void 시세조회_실패() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willThrow(new IllegalStateException("connection refused"));

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class)
        // 원인 예외는 밖으로 안 나감(호출부는 원인 구분 불가)
        .hasNoCause();
    }

    @Test
    @DisplayName("성공하면 성공 로그를 남긴다")
    void 성공_로그() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.HIGH);

      verify(tradeLogger).logMarketDataRequest(SYMBOL, "HIGH", true, null);
      verify(tradeLogger, never()).logMarketDataRequest(anyString(), anyString(), eq(false), any());
    }

    @Test
    @DisplayName("실패하면 실패 로그를 남긴다")
    void 실패_로그() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString())).willReturn(null);

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class);

      verify(tradeLogger).logMarketDataRequest(eq(SYMBOL), eq("CLOSE"), eq(false), any());
      verify(tradeLogger, never()).logMarketDataRequest(anyString(), anyString(), eq(true), any());
    }
  }

  @Nested
  @DisplayName("validateManualPrice")
  class ValidateManualPrice {

    @Test
    @DisplayName("범위 안이면 입력값을 그대로 돌려준다")
    void 범위_안() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThat(marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isEqualByComparingTo("218");
    }

    @Test
    @DisplayName("저가·고가 경계는 통과시킨다")
    void 경계_포함() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      // 경계 포함(막으면 종가와 고가가 같은 날 주문 불가)
      assertThat(marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("213")))
        .isEqualByComparingTo("213");
      assertThat(marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("222")))
        .isEqualByComparingTo("222");
    }

    @Test
    @DisplayName("저가보다 낮으면 입력 가격 범위 오류로 막는다")
    void 저가_미만() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("212.99")))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "MANUAL_PRICE_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("고가보다 높으면 입력 가격 범위 오류로 막는다")
    void 고가_초과() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("222.01")))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "MANUAL_PRICE_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("고가·저가가 null 이면 막는다")
    void 고저가_null() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlcWith(new BigDecimal("216"), null, null, new BigDecimal("220")));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("응답이 없거나 available=false 면 시세 없음으로 막는다")
    void 데이터_없음() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(false));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
    }

    @Test
    @DisplayName("시세 조회가 실패해도 TradeException")
    void 시세조회_실패() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willThrow(new IllegalStateException("timeout"));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("검증 로그는 MANUAL_VALIDATION 이름으로 남는다")
    void 로그_이름() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218"));

      verify(tradeLogger).logMarketDataRequest(SYMBOL, "MANUAL_VALIDATION", true, null);
    }

    @Test
    @DisplayName("범위를 벗어나면 실패 로그에 입력값과 범위를 담는다")
    void 범위_이탈_로그() {
      given(marketServiceClientWrapper.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("300")))
        .isInstanceOf(TradeException.class);

      verify(tradeLogger).logMarketDataRequest(SYMBOL, "MANUAL_VALIDATION", false,
        "입력 가격이 범위를 벗어남: 입력=300, 범위=213~222");
    }
  }

  @Nested
  @DisplayName("resolveTradeDate")
  class ResolveTradeDate {

    // 미국 동부 2026-11-18 21:00(한국은 2026-11-19 11:00)
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T02:00:00Z"), ZoneOffset.UTC);
    private MarketDataServiceImpl service;

    @BeforeEach
    void setUp() {
      service = new MarketDataServiceImpl(marketServiceClientWrapper, tradeLogger, clock);
    }

    private void price(String symbol, String date, boolean available) {
      given(marketServiceClientWrapper.getOHLCPrice(symbol, date)).willReturn(ohlc(available));
    }

    // 기준 종목이 그 날 뒤(미국 동부 오늘까지)에 시세가 있는지
    private void referenceAfter(String date, boolean collected) {
      LocalDate next = LocalDate.parse(date).plusDays(1);
      given(marketServiceClientWrapper.getOHLCPriceRange(TradeConstants.MARKET_REFERENCE_SYMBOL, next.toString(), "2026-11-18"))
        .willReturn(collected
          ? List.of(new OHLCPriceDto(TradeConstants.MARKET_REFERENCE_SYMBOL, next, BigDecimal.ONE, BigDecimal.ONE,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L, "USD", true))
          : List.of());
    }

    @Test
    @DisplayName("미국 동부 날짜로 오늘보다 뒤면 시세를 묻지 않고 미래 날짜로 거절한다")
    void futureDate() {
      assertThatThrownBy(() -> service.resolveTradeDate(SYMBOL, LocalDate.of(2026, 11, 19)))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "FUTURE_DATE_NOT_ALLOWED");
      verify(marketServiceClientWrapper, never()).getOHLCPrice(anyString(), anyString());
    }

    @Test
    @DisplayName("미국 동부의 오늘이면 한국 날짜가 하루 뒤여도 그날 시세로 체결한다")
    void todayInNewYork() {
      price(SYMBOL, "2026-11-18", true);

      assertThat(service.resolveTradeDate(SYMBOL, LocalDate.of(2026, 11, 18))).isEqualTo("2026-11-18");
    }

    @Test
    @DisplayName("평일에 시세가 있으면 그날을 그대로 쓴다")
    void weekdayWithPrice() {
      price(SYMBOL, "2024-09-18", true);

      assertThat(service.resolveTradeDate(SYMBOL, LocalDate.of(2024, 9, 18))).isEqualTo("2024-09-18");
      verify(marketServiceClientWrapper, never()).getOHLCPrice(eq(TradeConstants.MARKET_REFERENCE_SYMBOL), anyString());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"2024-09-21, 2024-09-20", "2024-09-22, 2024-09-20"})
    @DisplayName("토 · 일요일은 직전 금요일 시세로 체결한다")
    void weekendRollsBackToFriday(String requested, String expected) {
      price(SYMBOL, expected, true);

      assertThat(service.resolveTradeDate(SYMBOL, LocalDate.parse(requested))).isEqualTo(expected);
    }

    @Test
    @DisplayName("주말 앞 금요일이 휴장이면 그 앞 거래일까지 간다(성금요일 다음 토요일 → 목요일)")
    void weekendSkipsHolidayFriday() {
      price(SYMBOL, "2024-03-29", false);
      price(TradeConstants.MARKET_REFERENCE_SYMBOL, "2024-03-29", false);
      referenceAfter("2024-03-29", true);
      price(SYMBOL, "2024-03-28", true);

      assertThat(service.resolveTradeDate(SYMBOL, LocalDate.of(2024, 3, 30))).isEqualTo("2024-03-28");
    }

    @Test
    @DisplayName("평일인데 기준 종목도 시세가 없으면 휴장일로 거절한다")
    void weekdayHoliday() {
      price(SYMBOL, "2024-07-04", false);
      price(TradeConstants.MARKET_REFERENCE_SYMBOL, "2024-07-04", false);
      referenceAfter("2024-07-04", true);

      assertThatThrownBy(() -> service.resolveTradeDate(SYMBOL, LocalDate.of(2024, 7, 4)))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "HOLIDAY_TRADING_NOT_ALLOWED");
    }

    @Test
    @DisplayName("기준 종목도 그 뒤 날 시세가 없으면 휴장이 아니라 아직 수집 전이라 시세 없음으로 거절한다")
    void weekdayNotCollectedYet() {
      price(SYMBOL, "2026-11-13", false);
      price(TradeConstants.MARKET_REFERENCE_SYMBOL, "2026-11-13", false);
      referenceAfter("2026-11-13", false);

      assertThatThrownBy(() -> service.resolveTradeDate(SYMBOL, LocalDate.of(2026, 11, 13)))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
    }

    @Test
    @DisplayName("주말 앞 금요일이 아직 수집 전이면 목요일 시세로 가지 않고 시세 없음으로 거절한다")
    void weekendFridayNotCollectedYet() {
      price(SYMBOL, "2026-11-13", false);
      price(TradeConstants.MARKET_REFERENCE_SYMBOL, "2026-11-13", false);
      referenceAfter("2026-11-13", false);

      assertThatThrownBy(() -> service.resolveTradeDate(SYMBOL, LocalDate.of(2026, 11, 14)))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
      verify(marketServiceClientWrapper, never()).getOHLCPrice(SYMBOL, "2026-11-12");
    }

    @Test
    @DisplayName("미국 동부의 오늘이 기준 종목까지 비어 있으면 그 뒤 날을 묻지 않고 시세 없음으로 거절한다")
    void todayNotCollectedYet() {
      price(SYMBOL, "2026-11-18", false);
      price(TradeConstants.MARKET_REFERENCE_SYMBOL, "2026-11-18", false);

      assertThatThrownBy(() -> service.resolveTradeDate(SYMBOL, LocalDate.of(2026, 11, 18)))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
      verify(marketServiceClientWrapper, never()).getOHLCPriceRange(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("기준 종목은 시세가 있는데 그 종목만 없으면 시세 없음으로 거절한다")
    void symbolOnlyMissing() {
      price(SYMBOL, "2024-09-18", false);
      price(TradeConstants.MARKET_REFERENCE_SYMBOL, "2024-09-18", true);

      assertThatThrownBy(() -> service.resolveTradeDate(SYMBOL, LocalDate.of(2024, 9, 18)))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
    }

    @Test
    @DisplayName("주말에서 거슬러 가도 그 종목만 시세가 없으면 시세 없음으로 거절한다")
    void weekendSymbolOnlyMissing() {
      price(SYMBOL, "2024-09-20", false);
      price(TradeConstants.MARKET_REFERENCE_SYMBOL, "2024-09-20", true);

      assertThatThrownBy(() -> service.resolveTradeDate(SYMBOL, LocalDate.of(2024, 9, 21)))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_UNAVAILABLE");
    }
  }
}
