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

// 운영과 같은 Postgres 에서 한 사용자의 백테스트 이력 지우기
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("백테스트 이력 지우기")
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
  @DisplayName("한 사용자의 이력만 지우고 다른 사용자 · 비로그인 이력은 남긴다")
  void deleteByUserId_OnlyThatUser() {
    saved("user-1");
    saved("user-1");
    saved("user-2");
    saved(null);

    long deleted = backtestHistoryRepository.deleteByUserId("user-1");

    assertThat(deleted).isEqualTo(2);
    assertThat(backtestHistoryRepository.count()).isEqualTo(2);
    assertThat(backtestHistoryRepository.findByUserIdOrderByCreatedAtDesc("user-2", Pageable.unpaged())
      .getTotalElements()).isEqualTo(1);
  }
}
