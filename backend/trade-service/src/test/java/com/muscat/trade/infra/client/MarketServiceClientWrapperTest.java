package com.muscat.trade.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
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

// 여러 종목 현재가는 못 받으면 빈 값(날짜 시세 · 배당 목록은 ClientWrapperRetryTest)
@DisplayName("시세 서비스 호출과 대체 동작")
class MarketServiceClientWrapperTest {

  private final MarketServiceClient client = mock(MarketServiceClient.class);
  private final MarketServiceClientWrapper wrapper = new MarketServiceClientWrapper(client);
  private final RuntimeException down = new RuntimeException("down");

  @Test
  @DisplayName("정상일 때는 시세 서비스가 준 값을 그대로 돌려준다")
  void delegates() {
    StockPriceDto price = mock(StockPriceDto.class);
    Map<String, StockPriceDto> prices = Map.of("AAPL", price);
    List<DividendDto> dividends = List.of(new DividendDto("2024-09-16", new BigDecimal("0.25")));
    given(client.getCurrentPrices(List.of("AAPL"))).willReturn(prices);
    given(client.getOHLCPrice("AAPL", "2024-09-16")).willReturn(price);
    given(client.getDividends("AAPL", "2024-01-01", "2024-12-31")).willReturn(dividends);

    assertThat(wrapper.getCurrentPrices(List.of("AAPL"))).isSameAs(prices);
    assertThat(wrapper.getOHLCPrice("AAPL", "2024-09-16")).isSameAs(price);
    assertThat(wrapper.getDividends("AAPL", "2024-01-01", "2024-12-31")).isSameAs(dividends);
  }

  @Test
  @DisplayName("여러 종목 현재가를 못 받으면 빈 값으로 넘어간다")
  void currentPricesFallback_Empty() {
    Map<String, StockPriceDto> prices = ReflectionTestUtils.invokeMethod(wrapper, "getCurrentPricesFallback",
      List.of("AAPL", "MSFT"), down);

    assertThat(prices).isEmpty();
  }
}
