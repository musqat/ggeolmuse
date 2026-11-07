package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.request.IdempotencyKey;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.IdempotencyRecordRepository;
import com.muscat.user.domain.account.service.AccountHistoryService;
import com.muscat.user.domain.account.service.IdempotencyGuard;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 운영처럼 Flyway 로 만든 테이블에서 같은 키 동시 요청(test 프로필의 H2 를 여기서만 덮어씀)
@SpringBootTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("중복 요청 키 동시 요청 (TestContainers)")
class AccountIdempotencyIT {

  // 운영 RDS 와 같은 Postgres 15(마이그레이션 실행)
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  private static final IdempotencyKey KEY = new IdempotencyKey("3f2b8c1e-9d4a-4e6f-8a7b-1c2d3e4f5a6b");
  private static final BigDecimal AMOUNT = new BigDecimal("500000");

  @Autowired
  private AccountServiceImpl accountService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private AccountHistoryRepository accountHistoryRepository;

  @Autowired
  private IdempotencyRecordRepository idempotencyRecordRepository;

  @Autowired
  private UserRepository userRepository;

  @MockitoSpyBean
  private AccountHistoryService accountHistoryService;

  @MockitoSpyBean
  private IdempotencyGuard idempotencyGuard;

  @MockitoBean
  private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

  @MockitoBean
  private AccountEventProducer accountEventProducer;

  @Value("${app.account.initial-krw-amount:1000000}")
  private BigDecimal initialKrwAmount;

  @AfterEach
  void tearDown() {
    idempotencyRecordRepository.deleteAll();
    accountHistoryRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("같은 키 입금 둘이 동시에 오면 둘째는 첫째 커밋을 기다렸다 같은 결과를 받고 잔액은 한 번만 바뀐다")
  void concurrentDeposits_SameKey_AppliedOnce() throws Exception {
    User user = userRepository.save(User.builder()
      .email("idem@example.com").passwordHash("encodedPassword").nickname("중복 키 사용자")
      .keycloakId("keycloak-idem-user-id").build());
    CreateAccountRequestDto request = new CreateAccountRequestDto();
    request.setAccountName("중복 키 계좌");
    request.setCommissionRate(BigDecimal.ZERO);
    Account account = accountService.createAccount(user.getId(), request);
    long historiesBefore = accountHistoryRepository.count();

    // 먼저 키를 넣은 쪽은 두 요청의 첫 조회가 모두 끝날 때까지 커밋하지 않음(다른 쪽은 키 넣기에서 기다림)
    CountDownLatch firstChecks = new CountDownLatch(2);
    willAnswer(invocation -> {
      firstChecks.countDown();
      return invocation.callRealMethod();
    }).given(idempotencyGuard).isDone(any(), any(), any());
    willAnswer(invocation -> {
      assertThat(firstChecks.await(10, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(200);
      return invocation.callRealMethod();
    }).given(accountHistoryService).createDepositHistory(any(), any(), any(), any(), any());

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Void> deposit = () -> {
      start.await();
      accountService.depositKrw(account.getId(), user.getId(), AMOUNT, KEY);
      return null;
    };
    Future<Void> first = pool.submit(deposit);
    Future<Void> second = pool.submit(deposit);
    start.countDown();
    first.get(15, TimeUnit.SECONDS);
    second.get(15, TimeUnit.SECONDS);
    pool.shutdown();

    assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalanceKrw())
      .isEqualByComparingTo(initialKrwAmount.add(AMOUNT));
    assertThat(accountHistoryRepository.count()).isEqualTo(historiesBefore + 1);
    assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    // 둘 다 먼저 보고(없음) 키 충돌 뒤 한 번 더 봄(있음)
    verify(idempotencyGuard, times(3)).isDone(eq(user.getId()), eq(KEY), any());
  }
}
