package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.BacktestServiceClient;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.UserServiceClient;
import com.muscat.trade.infra.client.dto.DividendSettlementPageDto;
import com.muscat.trade.infra.kafka.DividendEventConsumer;
import com.muscat.trade.infra.kafka.DividendReconcileProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

// 원격 호출 · 카프카 발행을 부르는 순간 트랜잭션이 열려 있는지 Flyway 로 만든 Postgres 에서 봄
@SpringBootTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("원격 호출의 트랜잭션 범위 (TestContainers)")
class RemoteCallTransactionIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Container
  static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  private static final String USER = "it-scope@example.com";
  private static final long ACCOUNT = 401L;
  private static final String SYMBOL = "AAPL";

  @Autowired
  private HoldingsService holdingsService;

  @Autowired
  private DividendBackfillService dividendBackfillService;

  @Autowired
  private DividendEventConsumer dividendEventConsumer;

  @Autowired
  private HoldingsRepository holdingsRepository;

  @Autowired
  private TradeRepository tradeRepository;

  @MockitoBean
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @MockitoBean
  private BacktestServiceClient backtestServiceClient;

  @MockitoBean
  private UserServiceClient userServiceClient;

  @MockitoBean
  private DividendReconcileProducer dividendReconcileProducer;

  @MockitoBean
  private TradeLogger tradeLogger;

  private final List<Boolean> inTransaction = new CopyOnWriteArrayList<>();

  private <T> T record(T value) {
    inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
    return value;
  }

  private void saveBuy() {
    LocalDate tradeDate = LocalDate.of(2024, 1, 2);
    tradeRepository.save(Trade.builder()
      .userId(USER).accountId(ACCOUNT).symbol(SYMBOL).tradeType(TradeType.BUY)
      .quantity(new BigDecimal("10")).price(new BigDecimal("180")).totalAmount(new BigDecimal("1800"))
      .tradeDate(tradeDate).executedAt(tradeDate.atTime(10, 0))
      .build());
  }

  @AfterEach
  void tearDown() {
    tradeRepository.deleteAll();
    holdingsRepository.deleteAll();
  }

  @Test
  @DisplayName("포트폴리오는 현재가를 트랜잭션 밖에서 받는다")
  void portfolio_CurrentPricesOutsideTransaction() {
    holdingsRepository.save(Holdings.builder().userId(USER).accountId(ACCOUNT).symbol(SYMBOL)
      .totalQuantity(new BigDecimal("10")).avgPurchasePrice(new BigDecimal("150"))
      .totalInvestedAmount(new BigDecimal("1500")).build());
    given(marketServiceClientWrapper.getCurrentPrices(anyList())).willAnswer(invocation -> record(Map.of()));

    holdingsService.getPortfolio(USER, ACCOUNT);

    assertThat(inTransaction).containsExactly(false);
  }

  @Test
  @DisplayName("백테스트 포함 포트폴리오는 백테스트 결과를 기다리는 동안 트랜잭션을 잡지 않는다")
  void portfolioWithBacktest_WaitsOutsideTransaction() {
    willAnswer(invocation -> record(null)).given(tradeLogger)
      .logPortfolioAccess(eq(USER), eq("PORTFOLIO_WITH_BACKTEST"), anyString(), anyInt());

    holdingsService.getPortfolioSummaryWithBacktest(USER, Map.of(), "Bearer token");

    assertThat(inTransaction).containsExactly(false);
  }

  @Test
  @DisplayName("배당 공시를 받으면 재계산 요청을 트랜잭션 밖에서 보낸다")
  void dividendUpdated_RequestsOutsideTransaction() {
    saveBuy();
    willAnswer(invocation -> record(null)).given(dividendReconcileProducer).request(USER, ACCOUNT, SYMBOL);

    dividendEventConsumer.handleDividendUpdated(DividendUpdatedEvent.builder()
      .symbol(SYMBOL).exDate(LocalDate.of(2024, 2, 9)).amount(new BigDecimal("0.24")).currency("USD")
      .build(), 0, 0L, mock(Acknowledgment.class));

    assertThat(inTransaction).containsExactly(false);
  }

  @Test
  @DisplayName("배당 몫 미리 보기는 user · 시세 호출을 트랜잭션 밖에서 한다")
  void payoutDiff_RemoteCallsOutsideTransaction() {
    saveBuy();
    given(userServiceClient.getDividendSettlements(0, 500))
      .willAnswer(invocation -> record(new DividendSettlementPageDto(List.of(), true)));
    given(marketServiceClientWrapper.getDividends(eq(SYMBOL), anyString(), anyString()))
      .willAnswer(invocation -> record(List.of()));

    dividendBackfillService.diff();

    assertThat(inTransaction).containsExactly(false, false);
  }
}
