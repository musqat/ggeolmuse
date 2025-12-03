package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.common.util.TradeUtils;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.infra.client.UserServiceClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingService 핵심 로직 테스트")
class TradingServiceSimpleTest {

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private HoldingsRepository holdingsRepository;

  @Mock
  private UserServiceClient userServiceClient;

  @Mock
  private MarketDataService marketDataService;

  @Mock
  private TradeLogger tradeLogger;

  @Mock
  private TradeProperties tradeProperties;

  @Mock
  private TradeUtils tradeUtils;

  @InjectMocks
  private TradingServiceImpl tradingService;

  private String userId;
  private Long accountId;
  private String symbol;

  @BeforeEach
  void setUp() {
    userId = "test-user-123";
    accountId = 1L;
    symbol = "AAPL";
  }

  @Test
  @DisplayName("보유 종목이 없을 때 매도가 실패한다")
  void sellStock_NoHoldings_ThrowsException() {
    // given
    BigDecimal quantity = new BigDecimal("10");
    LocalDate tradeDate = LocalDate.of(2024, 1, 15);

    given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
      userId, accountId, symbol))
      .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() ->
      tradingService.sellStock(userId, accountId, symbol, quantity, tradeDate, PriceType.CLOSE,
        null)
    ).isInstanceOf(NotEnoughHoldingsException.class);

    verify(tradeRepository, never()).save(any(Trade.class));
  }

  @Test
  @DisplayName("보유 수량보다 많이 매도하려 하면 실패한다")
  void sellStock_InsufficientQuantity_ThrowsException() {
    // given
    BigDecimal holdingQuantity = new BigDecimal("5");
    BigDecimal sellQuantity = new BigDecimal("10");
    LocalDate tradeDate = LocalDate.of(2024, 1, 15);

    Holdings holdings = Holdings.builder()
      .id(1L)
      .userId(userId)
      .accountId(accountId)
      .symbol(symbol)
      .totalQuantity(holdingQuantity)
      .avgPurchasePrice(new BigDecimal("140.00"))
      .totalInvestedAmount(new BigDecimal("700.00"))
      .build();

    given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
      userId, accountId, symbol))
      .willReturn(Optional.of(holdings));

    // when & then
    assertThatThrownBy(() ->
      tradingService.sellStock(userId, accountId, symbol, sellQuantity, tradeDate, PriceType.CLOSE,
        null)
    ).isInstanceOf(NotEnoughHoldingsException.class);

    verify(tradeRepository, never()).save(any(Trade.class));
  }

  @Test
  @DisplayName("매도 가능 여부 확인 - 보유량이 정확히 일치")
  void canSellStock_ExactQuantity_ReturnsTrue() {
    // given
    BigDecimal sellQuantity = new BigDecimal("10");
    BigDecimal holdingQuantity = new BigDecimal("10");

    Holdings holdings = Holdings.builder()
      .id(1L)
      .userId(userId)
      .accountId(accountId)
      .symbol(symbol)
      .totalQuantity(holdingQuantity)
      .avgPurchasePrice(new BigDecimal("150.00"))
      .totalInvestedAmount(new BigDecimal("1500.00"))
      .build();

    given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
      userId, accountId, symbol))
      .willReturn(Optional.of(holdings));

    // when
    boolean canSell = tradingService.canSellStock(userId, accountId, symbol, sellQuantity);

    // then
    assertThat(canSell).isTrue();
  }

  @Test
  @DisplayName("매도 가능 여부 확인 - 충분한 보유량")
  void canSellStock_SufficientHoldings_ReturnsTrue() {
    // given
    BigDecimal sellQuantity = new BigDecimal("5");
    BigDecimal holdingQuantity = new BigDecimal("10");

    Holdings holdings = Holdings.builder()
      .id(1L)
      .userId(userId)
      .accountId(accountId)
      .symbol(symbol)
      .totalQuantity(holdingQuantity)
      .avgPurchasePrice(new BigDecimal("150.00"))
      .totalInvestedAmount(new BigDecimal("1500.00"))
      .build();

    given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
      userId, accountId, symbol))
      .willReturn(Optional.of(holdings));

    // when
    boolean canSell = tradingService.canSellStock(userId, accountId, symbol, sellQuantity);

    // then
    assertThat(canSell).isTrue();
  }

  @Test
  @DisplayName("매도 가능 여부 확인 - 보유 종목 없음")
  void canSellStock_NoHoldings_ReturnsFalse() {
    // given
    BigDecimal quantity = new BigDecimal("10");

    given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
      userId, accountId, symbol))
      .willReturn(Optional.empty());

    // when
    boolean canSell = tradingService.canSellStock(userId, accountId, symbol, quantity);

    // then
    assertThat(canSell).isFalse();
  }

  @Test
  @DisplayName("매도 가능 여부 확인 - 수량 부족")
  void canSellStock_InsufficientQuantity_ReturnsFalse() {
    // given
    BigDecimal sellQuantity = new BigDecimal("15");
    BigDecimal holdingQuantity = new BigDecimal("10");

    Holdings holdings = Holdings.builder()
      .id(1L)
      .userId(userId)
      .accountId(accountId)
      .symbol(symbol)
      .totalQuantity(holdingQuantity)
      .avgPurchasePrice(new BigDecimal("150.00"))
      .totalInvestedAmount(new BigDecimal("1500.00"))
      .build();

    given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
      userId, accountId, symbol))
      .willReturn(Optional.of(holdings));

    // when
    boolean canSell = tradingService.canSellStock(userId, accountId, symbol, sellQuantity);

    // then
    assertThat(canSell).isFalse();
  }
}
