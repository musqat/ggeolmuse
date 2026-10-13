package com.muscat.backtest.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 보유 종목이 없으면 백테스트가 성립하지 않아 예외로 올리고, 거래 이력은 없어도 보유 생성일로 대신한다
@DisplayName("trade 서비스 호출과 대체 동작")
class TradeServiceClientWrapperTest {

  private static final String TOKEN = "Bearer token";

  private final TradeServiceClient client = mock(TradeServiceClient.class);
  private final TradeServiceClientWrapper wrapper = new TradeServiceClientWrapper(client);
  private final RuntimeException down = new RuntimeException("down");

  @Test
  @DisplayName("정상일 때는 사용자 토큰을 그대로 넘기고 받은 값을 돌려준다")
  void delegates() {
    List<HoldingDto> holdings = List.of();
    List<TradeDto> trades = List.of(new TradeDto());
    given(client.getPortfolio(TOKEN)).willReturn(holdings);
    given(client.getTradeHistoryBySymbol(TOKEN, "AAPL")).willReturn(trades);

    assertThat(wrapper.getPortfolio(TOKEN)).isSameAs(holdings);
    assertThat(wrapper.getTradeHistoryBySymbol(TOKEN, "AAPL")).isSameAs(trades);
  }

  @Test
  @DisplayName("보유 종목을 못 받으면 원인을 담은 예외를 올린다")
  void portfolioFallback_Throws() {
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getPortfolioFallback", TOKEN, down))
      .isInstanceOf(RuntimeException.class)
      .hasCause(down);
  }

  @Test
  @DisplayName("거래 이력을 못 받으면 빈 목록으로 넘어간다")
  void tradeHistoryFallback_Empty() {
    List<TradeDto> trades = Objects.requireNonNull(
      ReflectionTestUtils.invokeMethod(wrapper, "getTradeHistoryBySymbolFallback", TOKEN, "AAPL", down));

    assertThat(trades).isEmpty();
  }
}
