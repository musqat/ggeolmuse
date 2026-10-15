package com.muscat.backtest.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 서킷 브레이커가 부르는 대체 메서드는 private 이라 직접 불러 동작을 고정한다.
// 단건 시세는 예외(틀린 값으로 백테스트하지 않는다), 목록은 빈 값, 환율은 기본 환율 1300
@DisplayName("시세 서비스 호출과 대체 동작")
class MarketDataClientWrapperTest {

  private final MarketDataClient client = mock(MarketDataClient.class);
  private final MarketDataClientWrapper wrapper = new MarketDataClientWrapper(client);
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

    assertThat(wrapper.getOHLCPrice("AAPL", "2024-03-04")).isSameAs(ohlc);
    assertThat(wrapper.getOHLCPriceRange("AAPL", "2024-01-01", "2024-12-31")).isSameAs(range);
    assertThat(wrapper.getCurrentPrice("AAPL")).isSameAs(price);
    assertThat(wrapper.getDividendHistory("AAPL", "2024-01-01", "2024-12-31")).isSameAs(dividends);
    assertThat(wrapper.getFxRate("2024-03-04")).isSameAs(fx);
    assertThat(wrapper.getLatestFxRate()).isSameAs(fx);
  }

  @Test
  @DisplayName("날짜 시세 · 현재가를 못 받으면 시세 서비스 사용 불가 코드에 원인을 담아 올린다")
  void singlePriceFallbacks_Throw() {
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
  @DisplayName("4xx 는 장애가 아니라서 날짜 시세 · 현재가 모두 받은 예외를 그대로 올린다")
  void singlePriceFallbacks_ClientErrorRethrown() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal/market/price/AAPL", Map.of(), null,
      StandardCharsets.UTF_8, null);
    FeignException.BadRequest badRequest = new FeignException.BadRequest("Bad Request", request, null, Map.of());

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getOHLCPriceFallback", "AAPL", "2024-03-04", badRequest))
      .isSameAs(badRequest);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getCurrentPriceFallback", "AAPL", badRequest))
      .isSameAs(badRequest);
  }

  @Test
  @DisplayName("기간 시세 · 배당 목록을 못 받으면 빈 값으로 넘어간다")
  void listFallbacks_Empty() {
    List<OHLCPriceDto> range = fallback("getOHLCPriceRangeFallback", "AAPL", "2024-01-01", "2024-12-31", down);
    List<DividendDto> dividends = fallback("getDividendHistoryFallback", "AAPL", "2024-01-01", "2024-12-31", down);

    assertThat(range).isEmpty();
    assertThat(dividends).isEmpty();
  }

  @Test
  @DisplayName("환율을 못 받으면 요청한 날짜(최신이면 오늘)의 기본 환율 1300")
  void fxFallbacks_DefaultRate() {
    FxRateDto dated = fallback("getFxRateFallback", "2024-03-04", down);
    FxRateDto latest = fallback("getLatestFxRateFallback", down);

    assertThat(dated.date()).isEqualTo(LocalDate.of(2024, 3, 4));
    assertThat(dated.rate()).isEqualByComparingTo("1300");
    assertThat(latest.date()).isEqualTo(LocalDate.now());
    assertThat(latest.rate()).isEqualByComparingTo("1300");
  }
}
