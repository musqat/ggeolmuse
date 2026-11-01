package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.domain.service.TradeCancellationService;
import com.muscat.trade.domain.service.TradeConfirmationService;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

// 운영처럼 Flyway 로 만든 테이블과 엔티티 대조(test 프로필의 H2 를 여기서만 덮어씀)
@SpringBootTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("TradingService 통합 테스트 (TestContainers)")
class TradingServiceIT {

  // 운영 RDS 와 같은 Postgres 15(마이그레이션 실행)
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Container
  static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");

  // ServiceConnection 대신 프로퍼티(앱의 Kafka 설정이 이 값을 직접 읽음)
  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired
  private TradingServiceImpl tradingService;

  @Autowired
  private TradeConfirmationService tradeConfirmationService;

  @Autowired
  private TradeCancellationService tradeCancellationService;

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private HoldingsRepository holdingsRepository;

  @MockitoBean
  private UserServiceClientWrapper userServiceClientWrapper;

  @MockitoBean
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

  private TradeResponseDto buy(String symbol, String quantity) {
    return tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, symbol,
      new BigDecimal(quantity), LocalDate.now(), PriceType.CLOSE, null);
  }

  // 정산됨 이벤트를 받은 것처럼 완료
  private void settle(Long tradeId) {
    tradeConfirmationService.confirm(TradeSettledEvent.builder()
      .tradeId(tradeId).userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID).build());
  }

  private TradeResponseDto buyAndSettle(String symbol, String quantity) {
    TradeResponseDto response = buy(symbol, quantity);
    settle(response.id());
    return response;
  }

  private TradeStatus statusOf(Long tradeId) {
    return tradeRepository.findById(tradeId).orElseThrow().getStatus();
  }

  @Nested
  @DisplayName("매수 거래 통합 테스트")
  class BuyTradeIntegrationTests {

    @Test
    @DisplayName("매수는 대기로 저장되고 보유는 그대로다")
    void buyStock_SavesPendingAndKeepsHoldings() {
      TradeResponseDto response = buy(TEST_SYMBOL, "10");

      assertThat(response.status()).isEqualTo(TradeStatus.PENDING);
      assertThat(statusOf(response.id())).isEqualTo(TradeStatus.PENDING);
      assertThat(holdingsRepository.findByUserIdAndSymbol(TEST_USER_ID, TEST_SYMBOL)).isEmpty();
    }

    @Test
    @DisplayName("정산된 매수는 완료되고 Holdings 가 DB 에 저장된다")
    void buyStock_Settled_SavesTradeAndHoldings() {
      // when
      TradeResponseDto response = buyAndSettle(TEST_SYMBOL, "10");

      // then
      assertThat(response.tradeType()).isEqualTo(TradeType.BUY);
      assertThat(response.symbol()).isEqualTo(TEST_SYMBOL);
      assertThat(response.quantity()).isEqualByComparingTo("10");

      // Trade가 DB에 저장되었는지 확인
      List<Trade> trades = tradeRepository.findAll();
      assertThat(trades).hasSize(1);
      assertThat(trades.get(0).getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(trades.get(0).getQuantity()).isEqualByComparingTo("10");
      assertThat(trades.get(0).getStatus()).isEqualTo(TradeStatus.COMPLETED);

      // Holdings가 DB에 생성되었는지 확인
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings).isPresent();
      assertThat(holdings.get().getTotalQuantity()).isEqualByComparingTo("10");
      assertThat(holdings.get().getSymbol()).isEqualTo(TEST_SYMBOL);
    }

    @Test
    @DisplayName("동일 종목 연속 매수가 정산되면 Holdings 수량이 누적된다")
    void buyStock_AccumulatesHoldings() {
      buyAndSettle(TEST_SYMBOL, "10");
      buyAndSettle(TEST_SYMBOL, "5");

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
      assertThatThrownBy(() -> buy(TEST_SYMBOL, "1000"))
        .isInstanceOf(TradeException.class);

      // DB에 저장되지 않았는지 확인
      assertThat(tradeRepository.count()).isEqualTo(tradesCountBefore);
      assertThat(holdingsRepository.count()).isEqualTo(holdingsCountBefore);
    }
  }

  @Nested
  @DisplayName("정산 전 매수")
  class PendingBuyTests {

    @Test
    @DisplayName("정산 전 매수의 주식은 보유에 없어 팔 수 없다")
    void pendingBuy_CannotBeSold() {
      buy(TEST_SYMBOL, "10");
      long tradesCountBefore = tradeRepository.count();

      assertThatThrownBy(() -> tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null))
        .isInstanceOf(NotEnoughHoldingsException.class);
      assertThat(tradeRepository.count()).isEqualTo(tradesCountBefore);
    }

    @Test
    @DisplayName("연달아 넣은 매수 둘 중 하나만 정산되고 하나는 반영 실패면 보유는 정산된 만큼만 남는다")
    void racingBuys_OneSettledOneRejected() {
      TradeResponseDto first = buy(TEST_SYMBOL, "10");
      TradeResponseDto second = buy(TEST_SYMBOL, "10");
      assertThat(statusOf(first.id())).isEqualTo(TradeStatus.PENDING);
      assertThat(statusOf(second.id())).isEqualTo(TradeStatus.PENDING);

      settle(first.id());
      tradeCancellationService.cancel(TradeRejectedEvent.builder()
        .tradeId(second.id()).userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID)
        .reasonCode("INSUFFICIENT_USD_BALANCE").build());

      assertThat(statusOf(first.id())).isEqualTo(TradeStatus.COMPLETED);
      assertThat(statusOf(second.id())).isEqualTo(TradeStatus.CANCELLED);
      assertThat(holdingsRepository.findByUserIdAndSymbol(TEST_USER_ID, TEST_SYMBOL)).get()
        .satisfies(holdings -> assertThat(holdings.getTotalQuantity()).isEqualByComparingTo("10"));
    }

    @Test
    @DisplayName("늦은 매수의 반영 실패가 먼저 오고 앞 매수가 뒤에 정산돼도 보유는 정산된 만큼만 남는다")
    void racingBuys_RejectedFirstThenSettled() {
      TradeResponseDto first = buy(TEST_SYMBOL, "10");
      TradeResponseDto second = buy(TEST_SYMBOL, "10");

      tradeCancellationService.cancel(TradeRejectedEvent.builder()
        .tradeId(second.id()).userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID)
        .reasonCode("INSUFFICIENT_USD_BALANCE").build());
      assertThat(holdingsRepository.findByUserIdAndSymbol(TEST_USER_ID, TEST_SYMBOL)).isEmpty();
      settle(first.id());

      assertThat(statusOf(first.id())).isEqualTo(TradeStatus.COMPLETED);
      assertThat(statusOf(second.id())).isEqualTo(TradeStatus.CANCELLED);
      assertThat(holdingsRepository.findByUserIdAndSymbol(TEST_USER_ID, TEST_SYMBOL)).get()
        .satisfies(holdings -> assertThat(holdings.getTotalQuantity()).isEqualByComparingTo("10"));
    }

    @Test
    @DisplayName("대기 중에 같은 종목을 팔고 나서 정산되면 보유를 체결 시각 순으로 다시 쌓는다(10주@100, 대기 10주@200, 10주 매도 → 10주@150)")
    void pendingBuy_SellInBetween_RebuildsInExecutionOrder() {
      given(marketDataService.determineTradePrice(eq(TEST_SYMBOL), any(LocalDate.class),
        any(PriceType.class), any())).willReturn(new BigDecimal("100.00"));
      buyAndSettle(TEST_SYMBOL, "10");
      given(marketDataService.determineTradePrice(eq(TEST_SYMBOL), any(LocalDate.class),
        any(PriceType.class), any())).willReturn(new BigDecimal("200.00"));
      TradeResponseDto pending = buy(TEST_SYMBOL, "10");

      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);
      settle(pending.id());

      assertThat(holdingsRepository.findByUserIdAndSymbol(TEST_USER_ID, TEST_SYMBOL)).get()
        .satisfies(holdings -> {
          assertThat(holdings.getTotalQuantity()).isEqualByComparingTo("10");
          assertThat(holdings.getAvgPurchasePrice()).isEqualByComparingTo("150");
        });
    }
  }

  @Nested
  @DisplayName("매도 거래 통합 테스트")
  class SellTradeIntegrationTests {

    @Test
    @DisplayName("매도 거래 실행 시 Holdings 수량이 감소한다")
    void sellStock_DecreasesHoldings() {
      // given - 먼저 매수하여 Holdings 생성 (FIFO 검증을 위해 실제 매수 거래 필요)
      buyAndSettle(TEST_SYMBOL, "20");

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
      buyAndSettle(TEST_SYMBOL, "10");

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
      buyAndSettle(TEST_SYMBOL, "5");

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
      buyAndSettle(TEST_SYMBOL, "10");

      Optional<Holdings> holdings1 = holdingsRepository.findByUserIdAndSymbol(
        TEST_USER_ID, TEST_SYMBOL);
      assertThat(holdings1).isPresent();
      assertThat(holdings1.get().getTotalQuantity()).isEqualByComparingTo("10");

      // 2. 두 번째 매수 (5주)
      buyAndSettle(TEST_SYMBOL, "5");

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
      buyAndSettle("AAPL", "10");

      // 2. MSFT 매수
      buyAndSettle("MSFT", "5");

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

  @Nested
  @DisplayName("트랜잭션 범위")
  class TransactionScopeTests {

    @Test
    @DisplayName("시세 · 계좌 잔액은 트랜잭션 밖에서 부르고, 체결 이벤트는 커밋 뒤 발행돼 발행 시각이 남는다")
    void remoteCallsOutsideTransaction() {
      List<Boolean> inTransaction = new ArrayList<>();
      given(marketDataService.determineTradePrice(eq(TEST_SYMBOL), any(LocalDate.class), any(PriceType.class), any()))
        .willAnswer(invocation -> {
          inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
          return STOCK_PRICE;
        });
      given(userServiceClientWrapper.getAccountBalance(any(Long.class)))
        .willAnswer(invocation -> {
          inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
          return testAccountBalance;
        });

      TradeResponseDto response = tradingService.buyStock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      assertThat(inTransaction).hasSize(2).containsOnly(false);
      assertThat(tradeRepository.findById(response.id())).get()
        .satisfies(trade -> assertThat(trade.getEventPublishedAt()).isNotNull());
    }
  }
}
