package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.dto.request.IdempotencyKey;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.IdempotencyRecordRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.IdempotencyGuard;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.domain.service.PortfolioStatsCalculator;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.SymbolTotals;
import com.muscat.trade.domain.service.TradeCancellationService;
import com.muscat.trade.domain.service.TradeConfirmationService;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

  @Autowired
  private IdempotencyRecordRepository idempotencyRecordRepository;

  @MockitoSpyBean
  private IdempotencyGuard idempotencyGuard;

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
    idempotencyRecordRepository.deleteAll();
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
      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)).isEmpty();
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
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
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
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
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
      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)).get()
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
      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)).isEmpty();
      settle(first.id());

      assertThat(statusOf(first.id())).isEqualTo(TradeStatus.COMPLETED);
      assertThat(statusOf(second.id())).isEqualTo(TradeStatus.CANCELLED);
      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)).get()
        .satisfies(holdings -> assertThat(holdings.getTotalQuantity()).isEqualByComparingTo("10"));
    }

    @Test
    @DisplayName("대기 중에 같은 종목을 팔고 나서 정산되면 보유를 거래일 · 체결 시각 순으로 다시 쌓는다(10주@100, 대기 10주@200, 10주 매도 → 10주@150)")
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

      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)).get()
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
      // given - 먼저 매수하여 Holdings 생성 (매도 가능 수량은 완료된 매수 거래로 셈)
      buyAndSettle(TEST_SYMBOL, "20");

      // when - 매도
      TradeResponseDto response = tradingService.sellStock(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // then
      assertThat(response).isNotNull();
      assertThat(response.tradeType()).isEqualTo(TradeType.SELL);

      // Holdings 수량이 10 감소했는지 확인
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
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
      // given - 매수하여 Holdings 생성 (매도 가능 수량은 완료된 매수 거래로 셈)
      buyAndSettle(TEST_SYMBOL, "10");

      // when - 전량 매도
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null);

      // then - Holdings가 삭제되었는지 확인
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
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

      Holdings saved = holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)
        .orElseThrow();
      BigDecimal originalQuantity = saved.getTotalQuantity();
      long tradesCountBefore = tradeRepository.count();

      // when & then - 10개 매도 시도 (보유 수량 5개보다 많음)
      assertThatThrownBy(() ->
        tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
          new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null))
        .isInstanceOf(NotEnoughHoldingsException.class);

      // Holdings가 변경되지 않았는지 확인 (rollback)
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
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

      Optional<Holdings> holdings1 = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
      assertThat(holdings1).isPresent();
      assertThat(holdings1.get().getTotalQuantity()).isEqualByComparingTo("10");

      // 2. 두 번째 매수 (5주)
      buyAndSettle(TEST_SYMBOL, "5");

      Optional<Holdings> holdings2 = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
      assertThat(holdings2).isPresent();
      assertThat(holdings2.get().getTotalQuantity()).isEqualByComparingTo("15");

      // 3. 부분 매도 (7주)
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("7"), LocalDate.now(), PriceType.CLOSE, null);

      Optional<Holdings> holdings3 = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
      assertThat(holdings3).isPresent();
      assertThat(holdings3.get().getTotalQuantity()).isEqualByComparingTo("8");

      // 4. 전량 매도 (8주)
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL,
        new BigDecimal("8"), LocalDate.now(), PriceType.CLOSE, null);

      Optional<Holdings> holdings4 = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
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

      Optional<Holdings> aaplHoldings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, "AAPL");
      Optional<Holdings> msftHoldings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, "MSFT");

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

  @Nested
  @DisplayName("과거 날짜 매도")
  class PastDateSellTests {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 1);

    private void buyOnAndSettle(LocalDate date, String quantity) {
      settle(tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, new BigDecimal(quantity), date,
        PriceType.CLOSE, null).id());
    }

    private TradeResponseDto sellOn(LocalDate date, String quantity) {
      return tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, new BigDecimal(quantity), date,
        PriceType.CLOSE, null);
    }

    private BigDecimal holdingQuantity() {
      return holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)
        .map(Holdings::getTotalQuantity).orElse(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("1일 매수 10 · 3일 매도 10 · 4일 매수 10 뒤 2일 매도 10 은 3일 보유가 음수가 되어 거절하고 체결 · 보유는 그대로다")
    void laterHoldingWouldGoNegative_Rejected() {
      buyOnAndSettle(DAY1, "10");
      sellOn(DAY1.plusDays(2), "10");
      buyOnAndSettle(DAY1.plusDays(3), "10");

      assertThatThrownBy(() -> sellOn(DAY1.plusDays(1), "10"))
        .isInstanceOf(TradeException.class)
        .hasMessage(TradeResponse.INSUFFICIENT_SELLABLE_QUANTITY.getMessage());
      assertThat(tradeRepository.count()).isEqualTo(3);
      assertThat(holdingQuantity()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("과거 날짜 매도는 뒤 날짜 누적 보유가 모두 0 이상인 만큼까지 받는다")
    void laterHoldingStaysNonNegative_Accepted() {
      buyOnAndSettle(DAY1, "10");
      sellOn(DAY1.plusDays(2), "4");
      buyOnAndSettle(DAY1.plusDays(3), "10");

      assertThatThrownBy(() -> sellOn(DAY1.plusDays(1), "7")).isInstanceOf(TradeException.class);
      sellOn(DAY1.plusDays(1), "6");

      assertThat(holdingQuantity()).isEqualByComparingTo("10");
    }
  }

  @Nested
  @DisplayName("보유 평균 단가 순서")
  class AveragePriceOrderTests {

    private void priceAt(String price) {
      given(marketDataService.determineTradePrice(eq(TEST_SYMBOL), any(LocalDate.class), any(PriceType.class), any()))
        .willReturn(new BigDecimal(price));
    }

    private void buyAndSettleOn(LocalDate date, String quantity, String price) {
      priceAt(price);
      settle(tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, new BigDecimal(quantity), date,
        PriceType.CLOSE, null).id());
    }

    private void sellOn(LocalDate date, String quantity, String price) {
      priceAt(price);
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, new BigDecimal(quantity), date,
        PriceType.CLOSE, null);
    }

    private Holdings holdings() {
      return holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL)
        .orElseThrow();
    }

    // 계좌 통계 수익 탭과 같은 재생으로 낸 평균 단가(투자금액 ÷ 수량)
    private BigDecimal statsAveragePrice() {
      SymbolTotals totals = PortfolioStatsCalculator.replay(tradeRepository.findAll(), List.of(), LocalDate.now(), 2)
        .symbols().get(TEST_SYMBOL);
      return totals.investedUsd().divide(totals.quantity(), 2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("오늘 매수 @100 · 오늘 매도 @110 뒤 과거 날짜 매수 @20 이면 보유도 거래일 순서로 쌓아 통계와 같은 평균 60 이다")
    void pastBuyAfterSell_SameAsStats() {
      LocalDate today = LocalDate.now();
      buyAndSettleOn(today, "10", "100.00");
      sellOn(today, "10", "110.00");
      buyAndSettleOn(today.minusDays(30), "10", "20.00");

      assertThat(holdings().getTotalQuantity()).isEqualByComparingTo("10");
      assertThat(holdings().getAvgPurchasePrice()).isEqualByComparingTo("60.00");
      assertThat(statsAveragePrice()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("1일 매수 10 @100 · 3일 매수 10 @200 뒤 2일 매도 10 이면 보유를 다시 쌓아 통계와 같은 평균 200 이다")
    void pastSellBetweenBuys_SameAsStats() {
      LocalDate day1 = LocalDate.of(2026, 10, 1);
      buyAndSettleOn(day1, "10", "100.00");
      buyAndSettleOn(day1.plusDays(2), "10", "200.00");
      sellOn(day1.plusDays(1), "10", "150.00");

      assertThat(holdings().getTotalQuantity()).isEqualByComparingTo("10");
      assertThat(holdings().getAvgPurchasePrice()).isEqualByComparingTo("200.00");
      assertThat(statsAveragePrice()).isEqualByComparingTo("200.00");
    }
  }

  @Nested
  @DisplayName("보유 잠금 · 버전")
  class HoldingsLockTests {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 웹 요청처럼 스레드에 영속성 컨텍스트 하나를 묶어 두고 실행
    private void inOpenContext(Runnable call) {
      EntityManager entityManager = entityManagerFactory.createEntityManager();
      TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
      try {
        call.run();
      } finally {
        TransactionSynchronizationManager.unbindResource(entityManagerFactory);
        entityManager.close();
      }
    }

    private void sellNow(String quantity) {
      tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL, new BigDecimal(quantity), LocalDate.now(),
        PriceType.CLOSE, null);
    }

    @Test
    @DisplayName("먼저 읽은 보유가 컨텍스트에 남아 있어도 잠금 뒤 다시 읽어 그새 다른 매도가 줄인 수량에서 뺀다")
    void rereadsHoldingsAfterLock() throws Exception {
      buyAndSettle(TEST_SYMBOL, "10");
      CountDownLatch firstChecked = new CountDownLatch(1);
      CountDownLatch otherSold = new CountDownLatch(1);
      // 먼저 온 매도는 보유를 읽은 뒤 가격을 정하는 자리에서 다른 매도가 끝나기를 기다림
      given(marketDataService.determineTradePrice(eq(TEST_SYMBOL), any(LocalDate.class), any(PriceType.class), any()))
        .willAnswer(invocation -> {
          if ("first-seller".equals(Thread.currentThread().getName())) {
            firstChecked.countDown();
            assertThat(otherSold.await(10, TimeUnit.SECONDS)).isTrue();
          }
          return STOCK_PRICE;
        });

      ExecutorService pool = Executors.newSingleThreadExecutor(task -> new Thread(task, "first-seller"));
      Future<?> first = pool.submit(() -> inOpenContext(() -> sellNow("3")));
      assertThat(firstChecked.await(10, TimeUnit.SECONDS)).isTrue();
      sellNow("4");
      otherSold.countDown();
      first.get(15, TimeUnit.SECONDS);
      pool.shutdown();

      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .get().satisfies(holdings -> assertThat(holdings.getTotalQuantity()).isEqualByComparingTo("3"));
    }

    @Test
    @DisplayName("키 없는 매도 둘이 같이 들어오면 잠금 순서대로 하나만 체결되고 다른 하나는 매도 가능 수량 부족이다")
    void concurrentSellsWithoutKey_OneFilled() throws Exception {
      buyAndSettle(TEST_SYMBOL, "10");
      CountDownLatch bothPriced = new CountDownLatch(2);
      // 둘 다 사전 검사를 지나 가격을 정한 뒤 같이 트랜잭션에 들어감
      given(marketDataService.determineTradePrice(eq(TEST_SYMBOL), any(LocalDate.class), any(PriceType.class), any()))
        .willAnswer(invocation -> {
          bothPriced.countDown();
          assertThat(bothPriced.await(10, TimeUnit.SECONDS)).isTrue();
          return STOCK_PRICE;
        });

      ExecutorService pool = Executors.newFixedThreadPool(2);
      Callable<String> sell = () -> {
        try {
          sellNow("6");
          return "FILLED";
        } catch (TradeException e) {
          return e.getErrorCode();
        }
      };
      Future<String> first = pool.submit(sell);
      Future<String> second = pool.submit(sell);
      List<String> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
      pool.shutdown();

      assertThat(outcomes).containsExactlyInAnyOrder("FILLED", "INSUFFICIENT_SELLABLE_QUANTITY");
      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .get().satisfies(holdings -> assertThat(holdings.getTotalQuantity()).isEqualByComparingTo("4"));
    }

    @Test
    @DisplayName("보유를 바꿀 때마다 보유 버전이 하나씩 오른다")
    void versionIncrementsOnChange() {
      buyAndSettle(TEST_SYMBOL, "10");
      long before = holdingsVersion();

      sellNow("3");
      sellNow("2");

      assertThat(holdingsVersion()).isEqualTo(before + 2);
    }

    private long holdingsVersion() {
      return jdbcTemplate.queryForObject("select version from holdings where user_id = ? and account_id = ? and symbol = ?",
        Long.class, TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);
    }
  }

  @Nested
  @DisplayName("같은 중복 요청 키 동시 주문")
  class IdempotencyTests {

    private final IdempotencyKey key = new IdempotencyKey("3f2b8c1e-9d4a-4e6f-8a7b-1c2d3e4f5a6b");

    // 먼저 키를 넣은 쪽은 다른 쪽이 키 넣기에 들어설 때까지 커밋하지 않음(다른 쪽은 유니크 인덱스에서 기다림)
    private void holdFirstClaimUntilBothChecked() {
      CountDownLatch claimEntries = new CountDownLatch(2);
      willAnswer(invocation -> {
        claimEntries.countDown();
        Object claimed = invocation.callRealMethod();
        assertThat(claimEntries.await(10, TimeUnit.SECONDS)).isTrue();
        return claimed;
      }).given(idempotencyGuard).claim(any(), any(), any());
    }

    // 같은 주문 둘을 동시에 보내고 두 응답
    private List<TradeResponseDto> twice(Callable<TradeResponseDto> order) throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      Callable<TradeResponseDto> gated = () -> {
        start.await();
        return order.call();
      };
      Future<TradeResponseDto> first = pool.submit(gated);
      Future<TradeResponseDto> second = pool.submit(gated);
      start.countDown();
      List<TradeResponseDto> responses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
      pool.shutdown();
      return responses;
    }

    @Test
    @DisplayName("같은 키 매수 둘이 동시에 오면 체결은 하나이고 두 응답이 같은 체결이다")
    void concurrentBuys_SameKey_OneTrade() throws Exception {
      holdFirstClaimUntilBothChecked();

      List<TradeResponseDto> responses = twice(() -> tradingService.buyStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null, key));

      assertThat(tradeRepository.findAll()).hasSize(1);
      assertThat(responses).extracting(TradeResponseDto::id).containsOnly(tradeRepository.findAll().get(0).getId());
      // 둘 다 먼저 보고(없음) 키 충돌 뒤 한 번 더 봄(있음)
      verify(idempotencyGuard, times(3)).findDone(eq(TEST_USER_ID), eq(key), any());
    }

    @Test
    @DisplayName("같은 키 매도 둘이 동시에 오면 둘째도 보유 부족이 아니라 처음 체결을 받는다")
    void concurrentSells_SameKey_SecondGetsFirstTrade() throws Exception {
      buyAndSettle(TEST_SYMBOL, "10");
      holdFirstClaimUntilBothChecked();

      List<TradeResponseDto> responses = twice(() -> tradingService.sellStock(TEST_USER_ID, TEST_ACCOUNT_ID,
        TEST_SYMBOL, new BigDecimal("10"), LocalDate.now(), PriceType.CLOSE, null, key));

      List<Trade> sells = tradeRepository.findAll().stream()
        .filter(trade -> trade.getTradeType() == TradeType.SELL).toList();
      assertThat(sells).hasSize(1);
      assertThat(responses).extracting(TradeResponseDto::id).containsOnly(sells.get(0).getId());
      assertThat(holdingsRepository.findByUserIdAndAccountIdAndSymbol(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .isEmpty();
    }
  }
}
