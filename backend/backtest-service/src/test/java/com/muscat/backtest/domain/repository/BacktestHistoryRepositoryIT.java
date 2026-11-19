package com.muscat.backtest.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.entity.BacktestHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 운영과 같은 Postgres 에서 백테스트 이력 주인 바꾸기
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("백테스트 이력 주인 바꾸기")
class BacktestHistoryRepositoryIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired
  private BacktestHistoryRepository backtestHistoryRepository;

  private BacktestHistory saved(String userId) {
    return backtestHistoryRepository.saveAndFlush(BacktestHistory.builder().userId(userId)
      .backtestType(BacktestType.STRATEGY_SIMULATION).requestParams("{}").fxRateMode("auto").build());
  }

  @Test
  @DisplayName("옛 주인의 행만 새 주인으로 옮기고 먼저 읽어 둔 행도 새 주인으로 다시 읽는다")
  void reassignOwner_MovesOnlyLegacyRows() {
    BacktestHistory legacy = saved("old@example.com");
    saved("other@example.com");
    assertThat(backtestHistoryRepository.findById(legacy.getId())).get()
      .extracting(BacktestHistory::getUserId).isEqualTo("old@example.com");

    long moved = backtestHistoryRepository.reassignOwner("old@example.com", "user-1");

    assertThat(moved).isEqualTo(1);
    assertThat(backtestHistoryRepository.findById(legacy.getId())).get()
      .extracting(BacktestHistory::getUserId).isEqualTo("user-1");
    assertThat(backtestHistoryRepository.findByUserIdOrderByCreatedAtDesc("other@example.com", Pageable.unpaged())
      .getTotalElements()).isEqualTo(1);
  }
}
