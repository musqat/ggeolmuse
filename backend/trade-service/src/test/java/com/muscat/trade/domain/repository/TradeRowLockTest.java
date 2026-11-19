package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.jpatest.TradePostgresJpaTest;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 커밋한 체결 행을 두 트랜잭션에서 잠가 읽는 순서
@TradePostgresJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("체결 행 잠금")
class TradeRowLockTest {

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private final ExecutorService pool = Executors.newFixedThreadPool(2);

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
    tradeRepository.deleteAll();
  }

  @Test
  @DisplayName("잠가 읽은 체결은 앞 트랜잭션이 끝날 때까지 다른 잠금 읽기가 기다린다")
  void secondLockWaitsForFirstTransaction() throws Exception {
    Long id = tradeRepository.save(Trade.builder()
      .userId("user-1").accountId(20L).symbol("AAPL").tradeType(TradeType.BUY)
      .quantity(new BigDecimal("1")).price(new BigDecimal("100.00")).totalAmount(new BigDecimal("100.00"))
      .tradeDate(LocalDate.of(2026, 10, 7)).executedAt(LocalDateTime.of(2026, 10, 7, 9, 0))
      .status(TradeStatus.PENDING)
      .build()).getId();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    Future<?> first = pool.submit(() -> transaction.executeWithoutResult(status -> {
      tradeRepository.findByIdForUpdate(id).orElseThrow();
      locked.countDown();
      awaitQuietly(release);
    }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
    Future<Optional<Trade>> second = pool.submit(() -> transaction.execute(status -> tradeRepository.findByIdForUpdate(id)));

    assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    release.countDown();
    first.get(10, TimeUnit.SECONDS);
    assertThat(second.get(10, TimeUnit.SECONDS)).get().extracting(Trade::getId).isEqualTo(id);
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
