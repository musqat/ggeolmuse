package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.common.util.TradeUtils;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.dto.request.TradingCapacityRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.dto.response.TradingCapacityResponseDto;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeSavedEvent;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradingService 단위 테스트")
class TradingServiceImplTest {

  @Mock
  private TradeRepository tradeRepository;
  @Mock
  private HoldingsRepository holdingsRepository;
  @Mock
  private UserServiceClientWrapper userServiceClientWrapper;
  @Mock
  private MarketDataService marketDataService;
  @Mock
  private TradeLogger tradeLogger;
  @Mock
  private TradeProperties tradeProperties;
  @Mock
  private TradeUtils tradeUtils;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private TradingServiceImpl tradingService;

  private static final String TEST_USER_ID = "test-user-uuid";
  private static final Long TEST_ACCOUNT_ID = 1L;
  private static final String TEST_SYMBOL = "AAPL";
  private static final BigDecimal TEST_QUANTITY = new BigDecimal("10");
  private static final BigDecimal TEST_PRICE = new BigDecimal("150.00");
  private static final LocalDate TEST_TRADE_DATE = LocalDate.of(2024, 1, 15);

  private AccountBalanceDto testAccountBalance;
  private TradeProperties.Calculation calculationProps;

  @BeforeEach
  void setUp() {
    testAccountBalance = AccountBalanceDto.builder()
      .accountId(String.valueOf(TEST_ACCOUNT_ID))
      .balanceUsd(new BigDecimal("10000.00"))
      .balanceKrw(BigDecimal.ZERO)
      .commissionRate(new BigDecimal("0.001"))
      .build();

    calculationProps = new TradeProperties.Calculation();
    calculationProps.setPricePrecision(2);
  }

  private void stubTradePropertiesForHoldingsUpdate() {
    given(tradeProperties.getCalculation()).willReturn(calculationProps);
  }

  @Nested
  @DisplayName("주식 매수 테스트")
  class BuyStockTests {

    @Test
    @DisplayName("정상적으로 주식이 매수된다 (신규 Holdings 생성)")
    void buyStock_NewHoldings_Success() {
      // given
      BigDecimal tradeAmount = TEST_QUANTITY.multiply(TEST_PRICE); // 1500.00
      BigDecimal fee = new BigDecimal("1.50");
      BigDecimal totalAmount = tradeAmount.add(fee); // 1501.50

      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.CLOSE, null))
        .willReturn(TEST_PRICE);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(fee);
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.empty());

      Trade savedTrade = Trade.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.BUY)
        .quantity(TEST_QUANTITY)
        .price(TEST_PRICE)
        .totalAmount(totalAmount)
        .fee(fee)
        .tradeDate(TEST_TRADE_DATE)
        .executedAt(LocalDateTime.now())
        .build();
      given(tradeRepository.save(any(Trade.class))).willReturn(savedTrade);

      // when
      TradeResponseDto result = tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY, TEST_TRADE_DATE, PriceType.CLOSE, null);

      // then
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(1L);
      assertThat(result.symbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.quantity()).isEqualByComparingTo(TEST_QUANTITY);
      assertThat(result.price()).isEqualByComparingTo(TEST_PRICE);

      // Holdings 생성 확인
      ArgumentCaptor<Holdings> holdingsCaptor = ArgumentCaptor.forClass(Holdings.class);
      verify(holdingsRepository).save(holdingsCaptor.capture());
      Holdings capturedHoldings = holdingsCaptor.getValue();
      assertThat(capturedHoldings.getUserId()).isEqualTo(TEST_USER_ID);
      assertThat(capturedHoldings.getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(capturedHoldings.getTotalQuantity()).isEqualByComparingTo(TEST_QUANTITY);
      assertThat(capturedHoldings.getAvgPurchasePrice()).isEqualByComparingTo(TEST_PRICE);

      // 이벤트 발행 확인
      verify(eventPublisher).publishEvent(any(TradeSavedEvent.class));
    }

    @Test
    @DisplayName("매수는 계좌 슬리피지율만큼 비싸게 체결된다")
    void buyStock_WithSlippage_RaisesTradePrice() {
      // given
      stubTradePropertiesForHoldingsUpdate();
      testAccountBalance = AccountBalanceDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .balanceUsd(new BigDecimal("10000.00"))
        .balanceKrw(BigDecimal.ZERO)
        .commissionRate(new BigDecimal("0.001"))
        .slippageRate(new BigDecimal("0.001"))
        .build();
      BigDecimal expectedPrice = new BigDecimal("150.15"); // 150.00 × 1.001
      BigDecimal fee = new BigDecimal("1.50");

      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.CLOSE, null))
        .willReturn(TEST_PRICE);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(fee);
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.empty());
      given(tradeRepository.save(any(Trade.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

      // when
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, TEST_QUANTITY,
        TEST_TRADE_DATE, PriceType.CLOSE, null);

      // then
      ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
      verify(tradeRepository).save(tradeCaptor.capture());
      assertThat(tradeCaptor.getValue().getPrice()).isEqualByComparingTo(expectedPrice);

      // 수수료도 조정된 체결가 기준이다
      ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
      verify(tradeUtils).calculateFee(eq(testAccountBalance), amountCaptor.capture());
      assertThat(amountCaptor.getValue()).isEqualByComparingTo("1501.50"); // 10 × 150.15
    }

    @Test
    @DisplayName("직접 입력 가격에는 슬리피지를 붙이지 않는다")
    void buyStock_ManualPrice_KeepsInputPrice() {
      // given
      BigDecimal manualPrice = new BigDecimal("100.00");
      testAccountBalance = AccountBalanceDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .balanceUsd(new BigDecimal("10000.00"))
        .balanceKrw(BigDecimal.ZERO)
        .commissionRate(new BigDecimal("0.001"))
        .slippageRate(new BigDecimal("0.001"))
        .build();

      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.MANUAL, manualPrice))
        .willReturn(manualPrice);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(new BigDecimal("1.00"));
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.empty());
      given(tradeRepository.save(any(Trade.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

      // when
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, TEST_QUANTITY,
        TEST_TRADE_DATE, PriceType.MANUAL, manualPrice);

      // then
      ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
      verify(tradeRepository).save(tradeCaptor.capture());
      assertThat(tradeCaptor.getValue().getPrice()).isEqualByComparingTo(manualPrice);
    }

    @Test
    @DisplayName("기존 Holdings에 추가 매수된다 (평균 단가 재계산)")
    void buyStock_ExistingHoldings_Success() {
      // given
      stubTradePropertiesForHoldingsUpdate();
      BigDecimal existingQuantity = new BigDecimal("5");
      BigDecimal existingAvgPrice = new BigDecimal("140.00");
      BigDecimal existingInvestedAmount = existingQuantity.multiply(existingAvgPrice);

      Holdings existingHoldings = Holdings.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .totalQuantity(existingQuantity)
        .avgPurchasePrice(existingAvgPrice)
        .totalInvestedAmount(existingInvestedAmount)
        .build();

      BigDecimal tradeAmount = TEST_QUANTITY.multiply(TEST_PRICE);
      BigDecimal fee = new BigDecimal("1.50");
      BigDecimal totalAmount = tradeAmount.add(fee);

      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.CLOSE, null))
        .willReturn(TEST_PRICE);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(fee);
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));

      Trade savedTrade = Trade.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.BUY)
        .quantity(TEST_QUANTITY)
        .price(TEST_PRICE)
        .totalAmount(totalAmount)
        .fee(fee)
        .tradeDate(TEST_TRADE_DATE)
        .executedAt(LocalDateTime.now())
        .build();
      given(tradeRepository.save(any(Trade.class))).willReturn(savedTrade);

      // when
      TradeResponseDto result = tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY, TEST_TRADE_DATE, PriceType.CLOSE, null);

      // then
      assertThat(result).isNotNull();

      // 평균 단가 재계산 확인
      // (5 * 140 + 10 * 150) / 15 = (700 + 1500) / 15 = 146.67
      BigDecimal newTotalQuantity = existingQuantity.add(TEST_QUANTITY);
      assertThat(existingHoldings.getTotalQuantity()).isEqualByComparingTo(newTotalQuantity);

      BigDecimal expectedAvgPrice = existingInvestedAmount.add(tradeAmount)
        .divide(newTotalQuantity, 2, RoundingMode.HALF_UP);
      assertThat(existingHoldings.getAvgPurchasePrice()).isEqualByComparingTo(expectedAvgPrice);

      verify(holdingsRepository, never()).save(any(Holdings.class));
      verify(eventPublisher).publishEvent(any(TradeSavedEvent.class));
    }

    @Test
    @DisplayName("잔액 부족 시 매수 실패")
    void buyStock_InsufficientBalance_ThrowsException() {
      // given
      BigDecimal tradeAmount = TEST_QUANTITY.multiply(TEST_PRICE);
      BigDecimal fee = new BigDecimal("1.50");
      BigDecimal totalAmount = tradeAmount.add(fee);

      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.CLOSE, null))
        .willReturn(TEST_PRICE);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(fee);
      willThrow(new TradeException(TradeResponse.INSUFFICIENT_BALANCE))
        .given(tradeUtils).validateBuyBalance(eq(TEST_USER_ID),
          eq(String.valueOf(TEST_ACCOUNT_ID)), any(BigDecimal.class), any());

      // when & then
      assertThatThrownBy(() -> tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY, TEST_TRADE_DATE, PriceType.CLOSE, null))
        .isInstanceOf(TradeException.class)
        .hasMessageContaining("잔액");

      verify(tradeRepository, never()).save(any(Trade.class));
      verify(holdingsRepository, never()).save(any(Holdings.class));
      verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
  }

  @Nested
  @DisplayName("주식 매도 테스트")
  class SellStockTests {

    @Test
    @DisplayName("정상적으로 주식이 매도된다 (부분 매도)")
    void sellStock_PartialSell_Success() {
      // given
      BigDecimal existingQuantity = new BigDecimal("20");
      BigDecimal avgPrice = new BigDecimal("140.00");
      BigDecimal totalInvestedAmount = existingQuantity.multiply(avgPrice);

      Holdings existingHoldings = Holdings.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .totalQuantity(existingQuantity)
        .avgPurchasePrice(avgPrice)
        .totalInvestedAmount(totalInvestedAmount)
        .build();

      BigDecimal sellQuantity = new BigDecimal("10");
      BigDecimal tradeAmount = sellQuantity.multiply(TEST_PRICE);
      BigDecimal fee = new BigDecimal("1.50");
      BigDecimal totalAmount = tradeAmount.subtract(fee);

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));
      given(tradeRepository.calculateSellableQuantity(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, TEST_TRADE_DATE))
        .willReturn(existingQuantity);
      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.CLOSE, null))
        .willReturn(TEST_PRICE);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(fee);
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));

      Trade savedTrade = Trade.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.SELL)
        .quantity(sellQuantity)
        .price(TEST_PRICE)
        .totalAmount(totalAmount)
        .fee(fee)
        .tradeDate(TEST_TRADE_DATE)
        .executedAt(LocalDateTime.now())
        .build();
      given(tradeRepository.save(any(Trade.class))).willReturn(savedTrade);

      // when
      TradeResponseDto result = tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, sellQuantity, TEST_TRADE_DATE, PriceType.CLOSE, null);

      // then
      assertThat(result).isNotNull();
      assertThat(result.tradeType()).isEqualTo(TradeType.SELL);
      assertThat(result.quantity()).isEqualByComparingTo(sellQuantity);

      // Holdings 수량 감소 확인 (평균단가는 유지)
      BigDecimal remainingQuantity = existingQuantity.subtract(sellQuantity);
      assertThat(existingHoldings.getTotalQuantity()).isEqualByComparingTo(remainingQuantity);
      assertThat(existingHoldings.getAvgPurchasePrice()).isEqualByComparingTo(avgPrice);

      verify(holdingsRepository, never()).delete(any());
      verify(eventPublisher).publishEvent(any(TradeSavedEvent.class));
    }

    @Test
    @DisplayName("매도는 계좌 슬리피지율만큼 싸게 체결된다")
    void sellStock_WithSlippage_LowersTradePrice() {
      // given
      stubTradePropertiesForHoldingsUpdate();
      BigDecimal existingQuantity = new BigDecimal("20");
      BigDecimal avgPrice = new BigDecimal("140.00");
      Holdings existingHoldings = Holdings.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .totalQuantity(existingQuantity)
        .avgPurchasePrice(avgPrice)
        .totalInvestedAmount(existingQuantity.multiply(avgPrice))
        .build();
      testAccountBalance = AccountBalanceDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .balanceUsd(new BigDecimal("10000.00"))
        .balanceKrw(BigDecimal.ZERO)
        .commissionRate(new BigDecimal("0.001"))
        .slippageRate(new BigDecimal("0.001"))
        .build();
      BigDecimal sellQuantity = new BigDecimal("10");
      BigDecimal expectedPrice = new BigDecimal("149.85"); // 150.00 × 0.999

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));
      given(tradeRepository.calculateSellableQuantity(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, TEST_TRADE_DATE))
        .willReturn(existingQuantity);
      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.CLOSE, null))
        .willReturn(TEST_PRICE);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(new BigDecimal("1.50"));
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));
      given(tradeRepository.save(any(Trade.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

      // when
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, sellQuantity,
        TEST_TRADE_DATE, PriceType.CLOSE, null);

      // then
      ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
      verify(tradeRepository).save(tradeCaptor.capture());
      assertThat(tradeCaptor.getValue().getPrice()).isEqualByComparingTo(expectedPrice);
    }

    @Test
    @DisplayName("전량 매도 시 Holdings가 삭제된다")
    void sellStock_FullSell_DeletesHoldings() {
      // given
      BigDecimal existingQuantity = TEST_QUANTITY;
      BigDecimal avgPrice = new BigDecimal("140.00");

      Holdings existingHoldings = Holdings.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .totalQuantity(existingQuantity)
        .avgPurchasePrice(avgPrice)
        .totalInvestedAmount(existingQuantity.multiply(avgPrice))
        .build();

      BigDecimal tradeAmount = TEST_QUANTITY.multiply(TEST_PRICE);
      BigDecimal fee = new BigDecimal("1.50");
      BigDecimal totalAmount = tradeAmount.subtract(fee);

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));
      given(tradeRepository.calculateSellableQuantity(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, TEST_TRADE_DATE))
        .willReturn(existingQuantity);
      given(marketDataService.determineTradePrice(TEST_SYMBOL, TEST_TRADE_DATE,
        PriceType.CLOSE, null))
        .willReturn(TEST_PRICE);
      given(tradeUtils.getAccountBalance(String.valueOf(TEST_ACCOUNT_ID)))
        .willReturn(testAccountBalance);
      given(tradeUtils.calculateFee(any(), any())).willReturn(fee);
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));

      Trade savedTrade = Trade.builder()
        .id(1L)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.SELL)
        .quantity(TEST_QUANTITY)
        .price(TEST_PRICE)
        .totalAmount(totalAmount)
        .fee(fee)
        .tradeDate(TEST_TRADE_DATE)
        .executedAt(LocalDateTime.now())
        .build();
      given(tradeRepository.save(any(Trade.class))).willReturn(savedTrade);

      // when
      TradeResponseDto result = tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY, TEST_TRADE_DATE, PriceType.CLOSE, null);

      // then
      assertThat(result).isNotNull();
      verify(holdingsRepository).delete(existingHoldings);
      verify(eventPublisher).publishEvent(any(TradeSavedEvent.class));
    }

    @Test
    @DisplayName("보유하지 않은 종목 매도 시 실패")
    void sellStock_NoHoldings_ThrowsException() {
      // given
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY, TEST_TRADE_DATE, PriceType.CLOSE, null))
        .isInstanceOf(NotEnoughHoldingsException.class);

      verify(tradeRepository, never()).save(any(Trade.class));
      verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("보유량보다 많은 수량 매도 시 실패")
    void sellStock_InsufficientQuantity_ThrowsException() {
      // given
      BigDecimal existingQuantity = new BigDecimal("5");
      Holdings existingHoldings = Holdings.builder()
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .totalQuantity(existingQuantity)
        .avgPurchasePrice(new BigDecimal("140.00"))
        .build();

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));

      // when & then
      assertThatThrownBy(() -> tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY, TEST_TRADE_DATE, PriceType.CLOSE, null))
        .isInstanceOf(NotEnoughHoldingsException.class);

      verify(tradeRepository, never()).save(any(Trade.class));
    }

    @Test
    @DisplayName("FIFO 방식 위반 시 매도 실패 (매도일 이전 매수 물량 부족)")
    void sellStock_FifoViolation_ThrowsException() {
      // given
      BigDecimal existingQuantity = new BigDecimal("20");
      Holdings existingHoldings = Holdings.builder()
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .totalQuantity(existingQuantity)
        .avgPurchasePrice(new BigDecimal("140.00"))
        .build();

      BigDecimal sellableQuantity = new BigDecimal("5"); // FIFO로 계산한 매도 가능 수량

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(existingHoldings));
      given(tradeRepository.calculateSellableQuantity(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, TEST_TRADE_DATE))
        .willReturn(sellableQuantity);

      // when & then
      assertThatThrownBy(() -> tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY, TEST_TRADE_DATE, PriceType.CLOSE, null))
        .isInstanceOf(TradeException.class)
        .hasMessageContaining("매도");

      verify(tradeRepository, never()).save(any(Trade.class));
    }
  }

  @Nested
  @DisplayName("거래 내역 조회 테스트")
  class GetTradesTests {

    @Test
    @DisplayName("사용자 거래 내역이 페이지네이션으로 조회된다")
    void getUserTrades_Success() {
      // given
      List<Trade> trades = new ArrayList<>();
      trades.add(createTestTrade(1L, TradeType.BUY));
      trades.add(createTestTrade(2L, TradeType.SELL));

      given(
        tradeRepository.findByUserIdOrderByExecutedAtDesc(eq(TEST_USER_ID), any(Pageable.class)))
        .willReturn(new PageImpl<>(trades));

      // when
      List<TradeResponseDto> result = tradingService.getUserTrades(TEST_USER_ID, 0, 10);

      // then
      assertThat(result).hasSize(2);
      assertThat(result.getFirst().id()).isEqualTo(1L);
      assertThat(result.get(1).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("종목별 거래 내역이 조회된다")
    void getTradesBySymbol_Success() {
      // given
      List<Trade> trades = new ArrayList<>();
      trades.add(createTestTrade(1L, TradeType.BUY));

      given(tradeRepository.findByUserIdAndSymbolOrderByExecutedAtDesc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(trades);

      // when
      List<TradeResponseDto> result = tradingService.getTradesBySymbol(TEST_USER_ID, TEST_SYMBOL);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().symbol()).isEqualTo(TEST_SYMBOL);
    }

    @Test
    @DisplayName("날짜 범위로 거래 내역이 조회된다")
    void getTradesByDateRange_Success() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 1, 31);

      List<Trade> trades = new ArrayList<>();
      trades.add(createTestTrade(1L, TradeType.BUY));

      given(tradeRepository.findTradesWithComplexFilters(
        eq(TEST_USER_ID), isNull(), isNull(), isNull(), eq(startDate), eq(endDate),
        isNull(), isNull(), any(Pageable.class)))
        .willReturn(new PageImpl<>(trades));

      // when
      List<TradeResponseDto> result = tradingService.getTradesByDateRange(
        TEST_USER_ID, startDate, endDate);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().tradeDate()).isAfterOrEqualTo(startDate);
      assertThat(result.getFirst().tradeDate()).isBeforeOrEqualTo(endDate);
    }
  }

  @Nested
  @DisplayName("매수/매도 가능 여부 확인 테스트")
  class CanTradeTests {

    @Test
    @DisplayName("매수 가능 여부가 정확히 확인된다 (잔액 충분)")
    void canBuyStock_SufficientBalance_ReturnsTrue() {
      // given
      BigDecimal totalAmount = new BigDecimal("5000.00");
      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(testAccountBalance);

      // when
      boolean result = tradingService.canBuyStock(TEST_USER_ID, TEST_ACCOUNT_ID, totalAmount);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("매수 불가능 여부가 정확히 확인된다 (잔액 부족)")
    void canBuyStock_InsufficientBalance_ReturnsFalse() {
      // given
      BigDecimal totalAmount = new BigDecimal("15000.00");
      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(testAccountBalance);

      // when
      boolean result = tradingService.canBuyStock(TEST_USER_ID, TEST_ACCOUNT_ID, totalAmount);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("매도 가능 여부가 정확히 확인된다 (보유량 충분)")
    void canSellStock_SufficientHoldings_ReturnsTrue() {
      // given
      BigDecimal existingQuantity = new BigDecimal("20");
      Holdings holdings = Holdings.builder()
        .totalQuantity(existingQuantity)
        .build();

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(holdings));

      // when
      boolean result = tradingService.canSellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("매도 불가능 여부가 정확히 확인된다 (보유량 부족)")
    void canSellStock_InsufficientHoldings_ReturnsFalse() {
      // given
      BigDecimal existingQuantity = new BigDecimal("5");
      Holdings holdings = Holdings.builder()
        .totalQuantity(existingQuantity)
        .build();

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(holdings));

      // when
      boolean result = tradingService.canSellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("보유하지 않은 종목은 매도 불가능")
    void canSellStock_NoHoldings_ReturnsFalse() {
      // given
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.empty());

      // when
      boolean result = tradingService.canSellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, TEST_QUANTITY);

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("매수/매도 가능 수량 계산 테스트")
  class CalculateCapacityTests {

    @Test
    @DisplayName("매수 가능 수량이 정확히 계산된다")
    void calculateBuyingCapacity_Success() {
      // given
      TradingCapacityRequestDto request = TradingCapacityRequestDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .symbol(TEST_SYMBOL)
        .tradeDate(TEST_TRADE_DATE)
        .build();

      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(testAccountBalance);
      given(marketDataService.determineTradePrice(
        TEST_SYMBOL, TEST_TRADE_DATE, PriceType.CLOSE, null)).willReturn(TEST_PRICE);
      stubFeeRate("0.001");

      // when
      TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(
        TEST_USER_ID, request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.symbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.currentPrice()).isEqualByComparingTo(TEST_PRICE);
      assertThat(result.availableBalance()).isEqualByComparingTo(
        testAccountBalance.getBalanceUsd());

      // 66주는 9,900 + 수수료 9.90 = 9,909.90. 67주는 가격만 10,050 이다
      assertThat(result.maxShares()).isEqualByComparingTo("66");
      assertThat(result.totalValue()).isEqualByComparingTo("9909.90");
    }

    @Test
    @DisplayName("슬리피지를 붙인 체결가와 수수료까지 넣어 센다")
    void calculateBuyingCapacity_SlippageAndFee_CountsTradeTotal() {
      // given
      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(balance("90.00", "0.001"));
      given(marketDataService.determineTradePrice(
        TEST_SYMBOL, TEST_TRADE_DATE, PriceType.CLOSE, null)).willReturn(new BigDecimal("8.97"));
      given(tradeProperties.getCalculation()).willReturn(calculationProps);
      stubFeeRate("0.0025");

      // when
      TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(
        TEST_USER_ID, capacityRequest(null, null));

      // then
      // 체결가 8.97 × 1.001 = 8.98. 10주는 89.80 + 수수료 0.22 = 90.02 라 잔액을 넘는다
      assertThat(result.currentPrice()).isEqualByComparingTo("8.97");
      assertThat(result.maxShares()).isEqualByComparingTo("9");
      assertThat(result.totalValue()).isEqualByComparingTo("81.02");
    }

    @Test
    @DisplayName("가격 유형을 고르면 그 가격으로 센다")
    void calculateBuyingCapacity_PriceType_UsesThatPrice() {
      // given
      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(balance("100.00", null));
      given(marketDataService.determineTradePrice(
        TEST_SYMBOL, TEST_TRADE_DATE, PriceType.HIGH, null)).willReturn(new BigDecimal("10.00"));
      stubFeeRate("0");

      // when
      TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(
        TEST_USER_ID, capacityRequest(PriceType.HIGH, null));

      // then
      assertThat(result.currentPrice()).isEqualByComparingTo("10.00");
      assertThat(result.maxShares()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("지정가는 슬리피지 없이 입력한 가격으로 센다")
    void calculateBuyingCapacity_Manual_SkipsSlippage() {
      // given
      BigDecimal manualPrice = new BigDecimal("9.00");
      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(balance("90.00", "0.001"));
      given(marketDataService.determineTradePrice(
        TEST_SYMBOL, TEST_TRADE_DATE, PriceType.MANUAL, manualPrice)).willReturn(manualPrice);
      stubFeeRate("0");

      // when
      TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(
        TEST_USER_ID, capacityRequest(PriceType.MANUAL, manualPrice));

      // then
      // 슬리피지가 붙었으면 9.01 이라 9주다
      assertThat(result.maxShares()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("수수료 반올림까지 체결 때와 같게 센다")
    void calculateBuyingCapacity_FeeRounding_MatchesTradeTotal() {
      // given
      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(balance("100.35", null));
      given(marketDataService.determineTradePrice(
        TEST_SYMBOL, TEST_TRADE_DATE, PriceType.CLOSE, null)).willReturn(new BigDecimal("10.01"));
      stubFeeRate("0.0025");

      // when
      TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(
        TEST_USER_ID, capacityRequest(null, null));

      // then
      // 10주는 100.10 + 수수료 0.25025 → 0.25 = 100.35 로 잔액과 같다
      assertThat(result.maxShares()).isEqualByComparingTo("10");
      assertThat(result.totalValue()).isEqualByComparingTo("100.35");
    }

    @Test
    @DisplayName("잔액이 한 주 값보다 적으면 0주")
    void calculateBuyingCapacity_NotEnoughForOneShare_ReturnsZero() {
      // given
      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(balance("5.00", null));
      given(marketDataService.determineTradePrice(
        TEST_SYMBOL, TEST_TRADE_DATE, PriceType.CLOSE, null)).willReturn(new BigDecimal("8.97"));
      stubFeeRate("0.0025");

      // when
      TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(
        TEST_USER_ID, capacityRequest(null, null));

      // then
      assertThat(result.maxShares()).isEqualByComparingTo("0");
    }

    private TradingCapacityRequestDto capacityRequest(PriceType priceType,
      BigDecimal manualPrice) {
      return TradingCapacityRequestDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .symbol(TEST_SYMBOL)
        .tradeDate(TEST_TRADE_DATE)
        .priceType(priceType)
        .manualPrice(manualPrice)
        .build();
    }

    private AccountBalanceDto balance(String balanceUsd, String slippageRate) {
      return AccountBalanceDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .balanceUsd(new BigDecimal(balanceUsd))
        .commissionRate(new BigDecimal("0.0025"))
        .slippageRate(slippageRate == null ? null : new BigDecimal(slippageRate))
        .build();
    }

    // 수수료는 tradeUtils 목이 내니 비율만 정해 둔다
    private void stubFeeRate(String rate) {
      given(tradeUtils.calculateFee(any(), any())).willAnswer(invocation ->
        invocation.<BigDecimal>getArgument(1).multiply(new BigDecimal(rate))
          .setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("매도 가능 수량이 정확히 계산된다")
    void calculateSellingCapacity_Success() {
      // given
      BigDecimal currentHoldings = new BigDecimal("50");
      BigDecimal sellableQuantity = new BigDecimal("40");

      TradingCapacityRequestDto request = TradingCapacityRequestDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .symbol(TEST_SYMBOL)
        .tradeDate(TEST_TRADE_DATE)
        .build();

      Holdings holdings = Holdings.builder()
        .totalQuantity(currentHoldings)
        .build();

      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(holdings));
      given(tradeRepository.calculateSellableQuantity(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, TEST_TRADE_DATE))
        .willReturn(sellableQuantity);
      given(marketDataService.getOHLCPrice(TEST_SYMBOL, TEST_TRADE_DATE, PriceType.CLOSE))
        .willReturn(TEST_PRICE);

      // when
      TradingCapacityResponseDto result = tradingService.calculateSellingCapacity(
        TEST_USER_ID, request);

      // then
      assertThat(result).isNotNull();
      assertThat(result.symbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.currentHoldings()).isEqualByComparingTo(currentHoldings);
      assertThat(result.maxSellableShares()).isEqualByComparingTo(sellableQuantity);
      assertThat(result.currentPrice()).isEqualByComparingTo(TEST_PRICE);
    }

    @Test
    @DisplayName("시장 데이터 조회 실패 시 예외 발생")
    void calculateBuyingCapacity_MarketDataError_ThrowsException() {
      // given
      TradingCapacityRequestDto request = TradingCapacityRequestDto.builder()
        .accountId(String.valueOf(TEST_ACCOUNT_ID))
        .symbol(TEST_SYMBOL)
        .tradeDate(TEST_TRADE_DATE)
        .build();

      given(userServiceClientWrapper.getAccountBalance(TEST_ACCOUNT_ID))
        .willReturn(testAccountBalance);
      given(marketDataService.determineTradePrice(
        TEST_SYMBOL, TEST_TRADE_DATE, PriceType.CLOSE, null))
        .willThrow(new RuntimeException("Market data unavailable"));

      // when & then
      assertThatThrownBy(() -> tradingService.calculateBuyingCapacity(TEST_USER_ID, request))
        .isInstanceOf(TradeException.class)
        .hasMessageContaining("시장");
    }
  }

  // 헬퍼 메서드
  private Trade createTestTrade(Long id, TradeType tradeType) {
    return Trade.builder()
      .id(id)
      .userId(TEST_USER_ID)
      .accountId(TEST_ACCOUNT_ID)
      .symbol(TEST_SYMBOL)
      .tradeType(tradeType)
      .quantity(TEST_QUANTITY)
      .price(TEST_PRICE)
      .totalAmount(TEST_QUANTITY.multiply(TEST_PRICE))
      .fee(BigDecimal.ONE)
      .tradeDate(TEST_TRADE_DATE)
      .executedAt(LocalDateTime.now())
      .build();
  }
}
