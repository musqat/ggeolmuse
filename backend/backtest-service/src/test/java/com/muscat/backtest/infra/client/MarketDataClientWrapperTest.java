package com.muscat.backtest.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.market.DividendDto;
import com.muscat.backtest.domain.dto.market.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 서킷 브레이커 대체 메서드(private)의 동작 — 시세 · 환율은 예외(그날 환율 없음 404 는 null), 배당 목록은 null
@DisplayName("시세 서비스 호출과 대체 동작")
class MarketDataClientWrapperTest {

  private final MarketDataClient client = mock(MarketDataClient.class);
  private final MarketDataClientWrapper wrapper = new MarketDataClientWrapper(client, new BigDecimal("1400"));
  private final RuntimeException down = new RuntimeException("down");

  private <T> T fallback(String method, Object... args) {
    return Objects.requireNonNull(ReflectionTestUtils.invokeMethod(wrapper, method, args));
  }

  @Test
  @DisplayName("정상일 때는 시세 서비스가 준 값을 그대로 돌려준다")
  void delegates() {
    OHLCPriceDto ohlc = mock(OHLCPriceDto.class);
    StockPriceDto price = mock(StockPriceDto.class);
    List<OHLCPriceDto> range = List.of(ohlc);
    List<DividendDto> dividends = List.of();
    FxRateDto fx = new FxRateDto(LocalDate.of(2024, 3, 4), java.math.BigDecimal.TEN);
    given(client.getOHLCPrice("AAPL", "2024-03-04")).willReturn(ohlc);
    given(client.getOHLCPriceRange("AAPL", "2024-01-01", "2024-12-31")).willReturn(range);
    given(client.getCurrentPrice("AAPL")).willReturn(price);
    given(client.getDividendHistory("AAPL", "2024-01-01", "2024-12-31")).willReturn(dividends);
    given(client.getFxRate("2024-03-04")).willReturn(fx);
    given(client.getLatestFxRate()).willReturn(fx);
    Map<String, java.math.BigDecimal> fxRates = Map.of("2024-03-04", java.math.BigDecimal.TEN);
    given(client.getBulkFxRates(List.of("2024-03-04"))).willReturn(fxRates);

    assertThat(wrapper.getOHLCPrice("AAPL", "2024-03-04")).isSameAs(ohlc);
    assertThat(wrapper.getOHLCPriceRange("AAPL", "2024-01-01", "2024-12-31")).isSameAs(range);
    assertThat(wrapper.getCurrentPrice("AAPL")).isSameAs(price);
    assertThat(wrapper.getDividendHistory("AAPL", "2024-01-01", "2024-12-31")).isSameAs(dividends);
    assertThat(wrapper.getFxRate("2024-03-04")).isSameAs(fx);
    assertThat(wrapper.getLatestFxRate()).isSameAs(fx);
    assertThat(wrapper.getBulkFxRates(List.of("2024-03-04"))).isSameAs(fxRates);
  }

  @Test
  @DisplayName("날짜 시세 · 기간 시세 · 현재가를 못 받으면 원인을 담은 시세 서비스 사용 불가 예외가 난다")
  void singlePriceFallbacks_Throw() {
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getOHLCPriceRangeFallback", "AAPL", "2024-01-01",
      "2024-12-31", down))
      .isInstanceOf(BacktestException.class)
      .hasCause(down)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getOHLCPriceFallback", "AAPL", "2024-03-04", down))
      .isInstanceOf(BacktestException.class)
      .hasCause(down)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getCurrentPriceFallback", "AAPL", down))
      .isInstanceOf(BacktestException.class)
      .hasCause(down)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
  }

  @Test
  @DisplayName("4xx 는 장애로 보지 않아 날짜 시세 · 현재가 모두 받은 예외를 바꾸지 않는다")
  void singlePriceFallbacks_ClientErrorRethrown() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal/market/price/AAPL", Map.of(), null,
      StandardCharsets.UTF_8, null);
    FeignException.BadRequest badRequest = new FeignException.BadRequest("Bad Request", request, null, Map.of());

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getOHLCPriceFallback", "AAPL", "2024-03-04", badRequest))
      .isSameAs(badRequest);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getCurrentPriceFallback", "AAPL", badRequest))
      .isSameAs(badRequest);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getOHLCPriceRangeFallback", "AAPL", "2024-01-01",
      "2024-12-31", badRequest))
      .isSameAs(badRequest);
  }

  @Test
  @DisplayName("배당 목록을 못 받으면 null")
  void dividendFallback_Null() {
    List<DividendDto> dividends = ReflectionTestUtils.invokeMethod(wrapper, "getDividendHistoryFallback", "AAPL",
      "2024-01-01", "2024-12-31", down);

    assertThat(dividends).isNull();
  }

  @Test
  @DisplayName("날짜 환율 · 최신 환율 · 여러 날짜 환율을 못 받으면 고정 환율 대신 시세 서비스 사용 불가 예외가 난다")
  void fxFallbacks_Throw() {
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getFxRateFallback", "2024-03-04", down))
      .isInstanceOf(BacktestException.class)
      .hasCause(down)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getLatestFxRateFallback", down))
      .isInstanceOf(BacktestException.class)
      .hasCause(down)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getBulkFxRatesFallback", List.of("2024-03-04"), down))
      .isInstanceOf(BacktestException.class)
      .hasCause(down)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
  }

  @Test
  @DisplayName("그날 · 최신 환율이 없다는 404 는 null 이라 기본 환율로 채우고 표시한다")
  void fxFallbacks_NotFoundNull() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal/market/fx/2024-03-04", Map.of(), null,
      StandardCharsets.UTF_8, null);
    FeignException.NotFound notFound = new FeignException.NotFound("Not Found", request, null, Map.of());

    FxRateDto dated = ReflectionTestUtils.invokeMethod(wrapper, "getFxRateFallback", "2024-03-04", notFound);
    FxRateDto latest = ReflectionTestUtils.invokeMethod(wrapper, "getLatestFxRateFallback", notFound);

    assertThat(dated).isNull();
    assertThat(latest).isNull();
  }
}
