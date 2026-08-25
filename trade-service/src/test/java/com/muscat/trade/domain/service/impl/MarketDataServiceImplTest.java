package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.infra.client.MarketServiceClient;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  private MarketServiceClient marketServiceClient;

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
    @DisplayName("MANUAL 이면 범위 검증을 태운다")
    void manual_경로() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      BigDecimal result = marketDataService.determineTradePrice(
        SYMBOL, TRADE_DATE, PriceType.MANUAL, new BigDecimal("218"));

      // 시세를 그대로 쓰지 않고 입력값을 돌려준다
      assertThat(result).isEqualByComparingTo("218");
      verify(tradeLogger).logMarketDataRequest(SYMBOL, "MANUAL_VALIDATION", true, null);
    }

    @Test
    @DisplayName("MANUAL 이 아니면 OHLC 조회로 간다")
    void ohlc_경로() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
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
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThat(marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, priceType))
        .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("응답이 null 이면 TradeException")
    void 응답_null() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString())).willReturn(null);

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("available=false 면 TradeException")
    void 데이터_없음() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(false));

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("고른 가격 필드가 null 이면 TradeException")
    void 가격_null() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlcWith(null, new BigDecimal("222"), new BigDecimal("213"),
          new BigDecimal("220")));

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.OPEN))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("MANUAL 을 직접 넘기면 지원하지 않는 유형으로 막는다")
    void manual_직접_호출() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      // switch default 의 IllegalArgumentException 도 catch 에서 TradeException 으로 바뀐다
      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.MANUAL))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("Feign 이 터져도 TradeException 하나로 나간다")
    void feign_실패() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willThrow(new IllegalStateException("connection refused"));

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class)
        // 원인 예외는 밖으로 안 나간다. 호출부는 원인을 구분할 수 없다
        .hasNoCause();
    }

    @Test
    @DisplayName("성공하면 성공 로그를 남긴다")
    void 성공_로그() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.HIGH);

      verify(tradeLogger).logMarketDataRequest(SYMBOL, "HIGH", true, null);
      verify(tradeLogger, never()).logMarketDataRequest(anyString(), anyString(), eq(false), any());
    }

    @Test
    @DisplayName("실패하면 실패 로그를 남긴다")
    void 실패_로그() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString())).willReturn(null);

      assertThatThrownBy(
        () -> marketDataService.getOHLCPrice(SYMBOL, TRADE_DATE, PriceType.CLOSE))
        .isInstanceOf(TradeException.class);

      // 내부 분기와 catch 에서 각각 남겨 같은 실패가 두 번 기록된다
      verify(tradeLogger, atLeastOnce())
        .logMarketDataRequest(eq(SYMBOL), eq("CLOSE"), eq(false), any());
      verify(tradeLogger, never()).logMarketDataRequest(anyString(), anyString(), eq(true), any());
    }
  }

  @Nested
  @DisplayName("validateManualPrice")
  class ValidateManualPrice {

    @Test
    @DisplayName("범위 안이면 입력값을 그대로 돌려준다")
    void 범위_안() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThat(marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isEqualByComparingTo("218");
    }

    @Test
    @DisplayName("저가·고가 경계는 통과시킨다")
    void 경계_포함() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      // 경계를 막으면 종가가 그날 고가인 날 주문을 넣을 수 없다
      assertThat(marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("213")))
        .isEqualByComparingTo("213");
      assertThat(marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("222")))
        .isEqualByComparingTo("222");
    }

    @Test
    @DisplayName("저가보다 낮으면 막는다")
    void 저가_미만() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("212.99")))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("고가보다 높으면 막는다")
    void 고가_초과() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("222.01")))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("고가·저가가 null 이면 막는다")
    void 고저가_null() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlcWith(new BigDecimal("216"), null, null, new BigDecimal("220")));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("응답이 없거나 available=false 면 막는다")
    void 데이터_없음() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(false));

      // 내부에서 MarketDataException 을 던지지만 catch 가 TradeException 으로 바꿔 내보낸다
      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("Feign 이 터져도 TradeException")
    void feign_실패() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willThrow(new IllegalStateException("timeout"));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218")))
        .isInstanceOf(TradeException.class);
    }

    @Test
    @DisplayName("검증 로그는 MANUAL_VALIDATION 이름으로 남는다")
    void 로그_이름() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("218"));

      verify(tradeLogger).logMarketDataRequest(SYMBOL, "MANUAL_VALIDATION", true, null);
    }

    @Test
    @DisplayName("범위를 벗어나면 실패 로그에 입력값과 범위를 담는다")
    void 범위_이탈_로그() {
      given(marketServiceClient.getOHLCPrice(SYMBOL, TRADE_DATE.toString()))
        .willReturn(ohlc(true));

      assertThatThrownBy(() ->
        marketDataService.validateManualPrice(SYMBOL, TRADE_DATE, new BigDecimal("300")))
        .isInstanceOf(TradeException.class);

      verify(tradeLogger).logMarketDataRequest(SYMBOL, "MANUAL_VALIDATION", false,
        "입력 가격이 범위를 벗어남: 입력=300, 범위=213~222");
    }
  }
}
