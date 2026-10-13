package com.muscat.trade.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.infra.client.dto.DividendDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 서킷 브레이커가 부르는 대체 메서드는 private 이라 직접 불러 동작을 고정한다.
// 단건 시세는 대체값 없이 예외(주문을 틀린 값으로 체결하면 안 된다), 목록은 빈 값으로 넘어간다
@DisplayName("시세 서비스 호출과 대체 동작")
class MarketServiceClientWrapperTest {

  private final MarketServiceClient client = mock(MarketServiceClient.class);
  private final MarketServiceClientWrapper wrapper = new MarketServiceClientWrapper(client);
  private final RuntimeException down = new RuntimeException("down");

  @Test
  @DisplayName("정상일 때는 시세 서비스가 준 값을 그대로 돌려준다")
  void delegates() {
    StockPriceDto price = mock(StockPriceDto.class);
    List<StockPriceDto> history = List.of(price);
    Map<String, StockPriceDto> prices = Map.of("AAPL", price);
    List<DividendDto> dividends = List.of(new DividendDto("2024-09-16", new BigDecimal("0.25")));
    given(client.getCurrentPrice("AAPL")).willReturn(price);
    given(client.getCurrentPrices(List.of("AAPL"))).willReturn(prices);
    given(client.getOHLCPrice("AAPL", "2024-09-16")).willReturn(price);
    given(client.getHistoricalPrices("AAPL", "2024-01-01", "2024-12-31")).willReturn(history);
    given(client.getDividends("AAPL", "2024-01-01", "2024-12-31")).willReturn(dividends);

    assertThat(wrapper.getCurrentPrice("AAPL")).isSameAs(price);
    assertThat(wrapper.getCurrentPrices(List.of("AAPL"))).isSameAs(prices);
    assertThat(wrapper.getOHLCPrice("AAPL", "2024-09-16")).isSameAs(price);
    assertThat(wrapper.getHistoricalPrices("AAPL", "2024-01-01", "2024-12-31")).isSameAs(history);
    assertThat(wrapper.getDividends("AAPL", "2024-01-01", "2024-12-31")).isSameAs(dividends);
  }

  @Test
  @DisplayName("단건 현재가 · 날짜 시세를 못 받으면 종목(과 날짜)을 담은 예외를 올린다")
  void singlePriceFallbacks_Throw() {
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getCurrentPriceFallback", "AAPL", down))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("AAPL")
      .hasCause(down);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getOHLCPriceFallback", "AAPL", "2024-09-16", down))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("AAPL")
      .hasMessageContaining("2024-09-16");
  }

  @Test
  @DisplayName("여러 종목 현재가 · 과거 시세 · 배당 목록을 못 받으면 빈 값으로 넘어간다")
  void listFallbacks_Empty() {
    Map<String, StockPriceDto> prices = ReflectionTestUtils.invokeMethod(wrapper, "getCurrentPricesFallback",
      List.of("AAPL", "MSFT"), down);
    List<StockPriceDto> history = ReflectionTestUtils.invokeMethod(wrapper, "getHistoricalPricesFallback",
      "AAPL", "2024-01-01", "2024-12-31", down);
    List<DividendDto> dividends = ReflectionTestUtils.invokeMethod(wrapper, "getDividendsFallback",
      "AAPL", "2024-01-01", "2024-12-31", down);

    assertThat(prices).isEmpty();
    assertThat(history).isEmpty();
    assertThat(dividends).isEmpty();
  }
}
