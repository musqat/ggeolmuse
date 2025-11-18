package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("TradingService 통합 테스트 (TestContainers)")
class TradingServiceIntegrationTest {

  @Container
  static KafkaContainer kafka = new KafkaContainer(
    DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired
  private TradingServiceImpl tradingService;

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private HoldingsRepository holdingsRepository;

  @MockBean
  private UserServiceClientWrapper userServiceClientWrapper;

  @MockBean
  private MarketDataService marketDataService;

  private static final String TEST_USER_ID = "test-user@example.com";
  private static final Long TEST_ACCOUNT_ID = 100L;
  private static final String TEST_SYMBOL = "AAPL";
  private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000.00");
  private static final BigDecimal STOCK_PRICE = new BigDecimal("150.00");

  private AccountBalanceDto testAccountBalance;

  @BeforeEach
  void setUp() {
    testAccountBalance = AccountBalanceDto.builder()
      .accountId(String.valueOf(TEST_ACCOUNT_ID))
      .balanceUsd(INITIAL_BALANCE)
      .balanceKrw(BigDecimal.ZERO)
      .commissionRate(new BigDecimal("0.001"))
      .build();

    given(marketDataService.determineTradePrice(eq(TEST_SYMBOL), any(LocalDate.class),
      any(PriceType.class), any())).willReturn(STOCK_PRICE);
    given(userServiceClientWrapper.getAccountBalance(any(Long.class)))
      .willReturn(testAccountBalance);
  }

  @AfterEach
  void tearDown() {
    tradeRepository.deleteAll();
    holdingsRepository.deleteAll();
  }

  @Nested
  @DisplayName("매수 거래 통합 테스트")
  class BuyTradeIntegrationTests {

    @Test
    @DisplayName("매수 거래 실행 시 Trade와 Holdings가 DB에 저장된다")
    void buyStock_SavesTradeAndHoldings() {
      // when
      TradeResponseDto response = tradingService.buyStock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // then
      assertThat(response).isNotNull();
      assertThat(response.tradeType()).isEqualTo(TradeType.BUY);
      assertThat(response.symbol()).isEqualTo(TEST_SYMBOL);
      assertThat(response.quantity()).isEqualByComparingTo("10");

      // Trade가 DB에 저장되었는지 확인
      List<Trade> trades = tradeRepository.findAll();
      assertThat(trades).hasSize(1);
      assertThat(trades.get(0).getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(trades.get(0).getQuantity()).isEqualByComparingTo("10");
      assertThat(trades.get(0).getTradeType()).isEqualTo(TradeType.BUY);

      // Holdings가 DB에 생성되었는지 확인
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings).isPresent();
      assertThat(holdings.get().getTotalQuantity()).isEqualByComparingTo("10");
      assertThat(holdings.get().getSymbol()).isEqualTo(TEST_SYMBOL);
    }

    @Test
    @DisplayName("동일 종목 연속 매수 시 Holdings 수량이 누적된다")
    void buyStock_AccumulatesHoldings() {
      // given - 첫 번째 매수
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // when - 두 번째 매수
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("5"), LocalDate.now(), PriceType.CLOSE, null);

      // then - Trade는 2개
      List<Trade> trades = tradeRepository.findAll();
      assertThat(trades).hasSize(2);

      // Holdings는 1개이며 수량이 15
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings).isPresent();
      assertThat(holdings.get().getTotalQuantity()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("잔고 부족 시 예외 발생하고 DB에 저장되지 않는다")
    void buyStock_InsufficientBalance_ThrowsException() {
      // given
      long tradesCountBefore = tradeRepository.count();
      long holdingsCountBefore = holdingsRepository.count();

      // when & then - 잔고보다 큰 금액 매수 시도
      assertThatThrownBy(() ->
        tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
          new BigDecimal("1000"), LocalDate.now(), PriceType.CLOSE, null))
        .isInstanceOf(TradeException.class);

      // DB에 저장되지 않았는지 확인
      assertThat(tradeRepository.count()).isEqualTo(tradesCountBefore);
      assertThat(holdingsRepository.count()).isEqualTo(holdingsCountBefore);
    }
  }

  @Nested
  @DisplayName("매도 거래 통합 테스트")
  class SellTradeIntegrationTests {

    @Test
    @DisplayName("매도 거래 실행 시 Holdings 수량이 감소한다")
    void sellStock_DecreasesHoldings() {
      // given - 먼저 매수하여 Holdings 생성 (FIFO 검증을 위해 실제 매수 거래 필요)
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("20"), LocalDate.now(), PriceType.CLOSE, null);

      // when - 매도
      TradeResponseDto response = tradingService.sellStock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // then
      assertThat(response).isNotNull();
      assertThat(response.tradeType()).isEqualTo(TradeType.SELL);

      // Holdings 수량이 10 감소했는지 확인
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings).isPresent();
      assertThat(holdings.get().getTotalQuantity()).isEqualByComparingTo("10");

      // Trade가 2개 저장되었는지 확인 (매수 1개 + 매도 1개)
      List<Trade> trades = tradeRepository.findAll();
      assertThat(trades).hasSize(2);
      assertThat(trades).anyMatch(t -> t.getTradeType() == TradeType.BUY);
      assertThat(trades).anyMatch(t -> t.getTradeType() == TradeType.SELL);
    }

    @Test
    @DisplayName("전량 매도 시 Holdings가 삭제된다")
    void sellStock_FullAmount_DeletesHoldings() {
      // given - 매수하여 Holdings 생성 (FIFO 검증을 위해 실제 매수 거래 필요)
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // when - 전량 매도
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // then - Holdings가 삭제되었는지 확인
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings).isEmpty();

      // Trade는 2개 저장되어 있어야 함 (매수 1개 + 매도 1개)
      List<Trade> trades = tradeRepository.findAll();
      assertThat(trades).hasSize(2);
      assertThat(trades).anyMatch(t -> t.getTradeType() == TradeType.BUY);
      assertThat(trades).anyMatch(t -> t.getTradeType() == TradeType.SELL);
    }

    @Test
    @DisplayName("보유 수량보다 많이 매도 시 예외 발생하고 rollback된다")
    void sellStock_ExceedsHoldings_ThrowsExceptionAndRollback() {
      // given - 매수하여 Holdings 생성 (수량 5)
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("5"), LocalDate.now(), PriceType.CLOSE, null);

      Holdings saved = holdingsRepository.findByUserIdAndSymbol(TEST_USER_ID, TEST_SYMBOL)
        .orElseThrow();
      BigDecimal originalQuantity = saved.getTotalQuantity();
      long tradesCountBefore = tradeRepository.count();

      // when & then - 10개 매도 시도 (보유 수량 5개보다 많음)
      assertThatThrownBy(() ->
        tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
          new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null))
        .isInstanceOf(NotEnoughHoldingsException.class);

      // Holdings가 변경되지 않았는지 확인 (rollback)
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings).isPresent();
      assertThat(holdings.get().getTotalQuantity()).isEqualByComparingTo(originalQuantity);

      // Trade가 저장되지 않았는지 확인
      assertThat(tradeRepository.count()).isEqualTo(tradesCountBefore);
    }

    @Test
    @DisplayName("Holdings가 없는 종목 매도 시 예외 발생")
    void sellStock_NoHoldings_ThrowsException() {
      // given
      given(marketDataService.determineTradePrice(eq("MSFT"), any(LocalDate.class),
        any(PriceType.class), any())).willReturn(new BigDecimal("300.00"));

      // when & then
      assertThatThrownBy(() ->
        tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, "MSFT",
          new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null))
        .isInstanceOf(NotEnoughHoldingsException.class);
    }
  }

  @Nested
  @DisplayName("전체 플로우 통합 테스트")
  class EndToEndFlowTests {

    @Test
    @DisplayName("매수 → 추가 매수 → 부분 매도 → 전량 매도 플로우가 정상 동작한다")
    void buyBuySellSell_EndToEndFlow() {
      // 1. 첫 번째 매수 (10주)
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      Optional<Holdings> holdings1 = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings1).isPresent();
      assertThat(holdings1.get().getTotalQuantity()).isEqualByComparingTo("10");

      // 2. 두 번째 매수 (5주)
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("5"), LocalDate.now(), PriceType.CLOSE, null);

      Optional<Holdings> holdings2 = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings2).isPresent();
      assertThat(holdings2.get().getTotalQuantity()).isEqualByComparingTo("15");

      // 3. 부분 매도 (7주)
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("7"), LocalDate.now(), PriceType.CLOSE, null);

      Optional<Holdings> holdings3 = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings3).isPresent();
      assertThat(holdings3.get().getTotalQuantity()).isEqualByComparingTo("8");

      // 4. 전량 매도 (8주)
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("8"), LocalDate.now(), PriceType.CLOSE, null);

      Optional<Holdings> holdings4 = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings4).isEmpty();

      // 전체 거래 내역 확인 (매수 2회 + 매도 2회 = 4개)
      List<Trade> allTrades = tradeRepository.findAll();
      assertThat(allTrades).hasSize(4);
      long buyCount = allTrades.stream()
        .filter(t -> t.getTradeType() == TradeType.BUY)
        .count();
      long sellCount = allTrades.stream()
        .filter(t -> t.getTradeType() == TradeType.SELL)
        .count();
      assertThat(buyCount).isEqualTo(2);
      assertThat(sellCount).isEqualTo(2);
    }

    @Test
    @DisplayName("다중 종목 동시 거래가 독립적으로 관리된다")
    void multipleSymbols_IndependentManagement() {
      // given - MSFT 시세 설정
      given(marketDataService.determineTradePrice(eq("MSFT"), any(LocalDate.class),
        any(PriceType.class), any())).willReturn(new BigDecimal("300.00"));

      // 1. AAPL 매수
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, "AAPL",
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // 2. MSFT 매수
      tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, "MSFT",
        new BigDecimal("5"), LocalDate.now(), PriceType.CLOSE, null);

      // then - 각각 독립적인 Holdings 생성
      List<Holdings> allHoldings = holdingsRepository.findAll();
      assertThat(allHoldings).hasSize(2);

      Optional<Holdings> aaplHoldings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, "AAPL");
      Optional<Holdings> msftHoldings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, "MSFT");

      assertThat(aaplHoldings).isPresent();
      assertThat(aaplHoldings.get().getTotalQuantity()).isEqualByComparingTo("10");

      assertThat(msftHoldings).isPresent();
      assertThat(msftHoldings.get().getTotalQuantity()).isEqualByComparingTo("5");
    }
  }
}
