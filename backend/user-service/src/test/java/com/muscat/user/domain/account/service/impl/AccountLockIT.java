package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.DividendSettlementRepository;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.domain.user.service.KeycloakService;
import com.muscat.user.domain.user.service.UserService;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 같은 계좌 잔액을 바꾸는 두 트랜잭션이 겹칠 때(Flyway 로 만든 Postgres 테이블, 실제 행 잠금)
@SpringBootTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("계좌 잠금 · 버전 (TestContainers)")
class AccountLockIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  private static final String KEYCLOAK_ID = "keycloak-lock-user";

  @Autowired
  private AccountServiceImpl accountService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private AccountHistoryRepository accountHistoryRepository;

  @Autowired
  private DividendSettlementRepository dividendSettlementRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoBean
  private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

  @MockitoBean
  private AccountEventProducer accountEventProducer;

  @MockitoBean
  private KeycloakService keycloakService;

  @MockitoSpyBean
  private PasswordEncoder passwordEncoder;

  @Autowired
  private UserService userService;

  private Long userId;
  private Account account;

  @BeforeEach
  void setUp() {
    User user = userRepository.save(User.builder()
      .email("lock@example.com").passwordHash("encodedPassword").nickname("잠금 사용자")
      .keycloakId(KEYCLOAK_ID).build());
    userId = user.getId();
    CreateAccountRequestDto request = new CreateAccountRequestDto();
    request.setAccountName("잠금 계좌");
    request.setCommissionRate(BigDecimal.ZERO);
    account = accountService.createAccount(userId, request);
    accountService.updateUsdBalance(account.getId(), userId, new BigDecimal("100.00"), TransactionType.TRADE_SELL,
      "시작 잔액");
  }

  @AfterEach
  void tearDown() {
    dividendSettlementRepository.deleteAll();
    accountHistoryRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("배당 반영이 정산 커밋을 기다리면 정산이 끝난 잔액에 배당을 더한다")
  void dividendWaitingForSettlement_AddsToSettledBalance() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch settled = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);

    // 정산은 매수 30달러를 반영한 뒤 잠금을 쥔 채 커밋을 미룸
    Future<?> settlement = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
      accountService.processTradeEvent(buy("30.00"));
      settled.countDown();
      await(commit);
    }));
    assertThat(settled.await(10, TimeUnit.SECONDS)).isTrue();

    Future<?> dividend = pool.submit(() -> accountService.applyDividendPayout(payout("10.00")));
    awaitLockWaiter();
    commit.countDown();

    settlement.get(10, TimeUnit.SECONDS);
    dividend.get(10, TimeUnit.SECONDS);
    pool.shutdown();

    assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalanceUsd())
      .isEqualByComparingTo("80.00");
  }

  @Test
  @DisplayName("탈퇴가 계좌를 읽은 뒤 배당 반영이 먼저 커밋돼도 계좌를 잠가 다시 읽고 탈퇴 마침")
  void deleteAfterConcurrentDividend_DeletesUserAndAccounts() {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    // 비밀번호 확인 사이(계좌는 이미 읽음)에 다른 트랜잭션이 배당을 반영해 커밋
    doAnswer(invocation -> {
      pool.submit(() -> accountService.applyDividendPayout(payout("10.00"))).get(10, TimeUnit.SECONDS);
      return true;
    }).when(passwordEncoder).matches(any(), any());

    userService.deleteAccount(KEYCLOAK_ID, "password", null);
    pool.shutdown();

    assertThat(accountRepository.findById(account.getId())).isEmpty();
    assertThat(userRepository.findById(userId)).isEmpty();
  }

  @Test
  @DisplayName("잔액을 바꿀 때마다 계좌 버전이 하나씩 오른다")
  void versionIncrementsOnChange() {
    long before = version();

    accountService.applyDividendPayout(payout("10.00"));
    accountService.processTradeEvent(buy("30.00"));

    assertThat(version()).isEqualTo(before + 2);
  }

  @Test
  @DisplayName("버전 칸은 비울 수 없고 기존 행은 0 으로 채운다")
  void versionColumn() {
    assertThat(jdbcTemplate.queryForMap(
      "select is_nullable, column_default from information_schema.columns where table_name = 'account' and column_name = 'version'"))
      .containsEntry("is_nullable", "NO")
      .containsEntry("column_default", "0");
  }

  private TradeCompletedEvent buy(String total) {
    return TradeCompletedEvent.builder()
      .userId(KEYCLOAK_ID).tradeId(1L).accountId(account.getId()).symbol("AAPL")
      .tradeType(TradeCompletedEvent.TradeType.BUY).quantity(BigDecimal.ONE)
      .price(new BigDecimal(total)).totalAmount(new BigDecimal(total)).currency("USD").fee(BigDecimal.ZERO)
      .build();
  }

  private DividendPayoutEvent payout(String netAmount) {
    return DividendPayoutEvent.builder()
      .userId(KEYCLOAK_ID).accountId(account.getId()).symbol("AAPL").exDate(LocalDate.of(2024, 2, 9))
      .dividendPerShare(new BigDecimal("1.00")).quantity(BigDecimal.TEN).grossAmount(new BigDecimal(netAmount))
      .taxAmount(BigDecimal.ZERO).netAmount(new BigDecimal(netAmount)).currency("USD")
      .build();
  }

  private long version() {
    return jdbcTemplate.queryForObject("select version from account where id = ?", Long.class, account.getId());
  }

  // 행 잠금을 기다리는 세션이 생길 때까지
  private void awaitLockWaiter() throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      Integer waiting = jdbcTemplate.queryForObject(
        "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()",
        Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("행 잠금을 기다리는 세션이 없음");
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
