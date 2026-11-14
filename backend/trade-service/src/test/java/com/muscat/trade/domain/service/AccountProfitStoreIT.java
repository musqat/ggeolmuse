package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.domain.entity.AccountProfitDaily;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Flyway 로 만든 Postgres 스키마에서 계좌 수익 흐름 저장분 교체와 같은 계좌 동시 저장 확인
@DataJpaTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@Import(AccountProfitStore.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DisplayName("계좌 수익 흐름 저장 통합 테스트 (TestContainers)")
class AccountProfitStoreIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  private static final LocalDate D1 = LocalDate.of(2024, 1, 2);
  private static final LocalDate D2 = LocalDate.of(2024, 1, 3);
  private static final LocalDate D3 = LocalDate.of(2024, 1, 4);

  @Autowired
  private AccountProfitStore accountProfitStore;

  @Autowired
  private AccountProfitDailyRepository accountProfitDailyRepository;

  @AfterEach
  void tearDown() {
    accountProfitDailyRepository.deleteAllInBatch();
  }

  private static AccountProfitDaily row(long accountId, LocalDate date, long profitKrw) {
    return AccountProfitDaily.of("user-1", accountId, new DailyProfit(date, new BigDecimal("1.00"),
      new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("0.50"), new BigDecimal("1300.5"),
      BigDecimal.valueOf(profitKrw), new BigDecimal("10.00"), new BigDecimal("13005")),
      LocalDateTime.of(2024, 1, 5, 9, 0));
  }

  @Test
  @DisplayName("기준 날짜부터의 저장분을 지우고 같은 날짜를 새 값으로 다시 넣으며, 앞 날짜와 다른 계좌는 그대로 둔다")
  void replaceFrom_ReplacesSameDatesKeepsOthers() {
    accountProfitDailyRepository.saveAll(List.of(row(20L, D1, 100), row(20L, D2, 200), row(20L, D3, 300),
      row(21L, D2, 900)));

    accountProfitStore.replaceFrom(20L, D2, List.of(row(20L, D2, 250)));

    assertThat(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc("user-1", 20L))
      .extracting(AccountProfitDaily::getTradeDate, row -> row.getProfitKrw().longValue())
      .containsExactly(tuple(D1, 100L), tuple(D2, 250L));
    assertThat(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc("user-1", 21L))
      .hasSize(1);
  }

  @Test
  @DisplayName("같은 계좌의 저장분을 열두 요청이 동시에 바꿔도 모두 성공하고 날짜마다 새 값 한 줄만 남는다")
  void replaceFrom_ConcurrentSameAccount_AllSucceed() throws Exception {
    List<LocalDate> dates = IntStream.range(0, 30).mapToObj(D1::plusDays).toList();
    accountProfitDailyRepository.saveAll(dates.stream().map(date -> row(20L, date, 100)).toList());

    int requests = 12;
    ExecutorService executor = Executors.newFixedThreadPool(requests);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < requests; i++) {
      LocalDate from = dates.get(i % 3);
      List<AccountProfitDaily> rows = dates.stream()
        .filter(date -> !date.isBefore(from))
        .map(date -> row(20L, date, 200))
        .toList();
      results.add(executor.submit(() -> {
        start.await();
        accountProfitStore.replaceFrom(20L, from, rows);
        return null;
      }));
    }
    start.countDown();
    List<Throwable> failures = new ArrayList<>();
    try {
      for (Future<?> result : results) {
        try {
          result.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
          failures.add(e.getCause());
        }
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(failures).isEmpty();
    assertThat(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc("user-1", 20L))
      .extracting(AccountProfitDaily::getTradeDate, row -> row.getProfitKrw().longValue())
      .containsExactlyElementsOf(dates.stream().map(date -> tuple(date, 200L)).toList());
  }
}
