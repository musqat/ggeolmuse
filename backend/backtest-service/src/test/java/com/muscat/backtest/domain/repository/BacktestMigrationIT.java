package com.muscat.backtest.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.entity.BacktestHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 운영과 같이 Postgres 에 Flyway 로 테이블을 만들고 엔티티가 테이블과 맞는지(validate) 보는 컨텍스트
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("마이그레이션과 엔티티가 운영 Postgres 에서 맞는다")
class BacktestMigrationIT {

  // 운영 RDS 와 같은 Postgres 15
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private BacktestHistoryRepository backtestHistoryRepository;

  @Test
  @DisplayName("보유 종목 투자 결과 테이블은 지우고 백테스트 이력 테이블은 남긴다")
  void dropsInvestmentResultTable() {
    assertThat(tableExists("investment_backtest_result")).isFalse();
    assertThat(tableExists("backtest_history")).isTrue();
  }

  @Test
  @DisplayName("이력 요청 칸은 길이 제한 없는 text 라 2000자 넘는 요청 JSON 도 저장한다")
  void requestParamsIsText() {
    String type = jdbcTemplate.queryForObject(
      "SELECT data_type FROM information_schema.columns WHERE table_name = 'backtest_history' AND column_name = 'request_params'",
      String.class);
    BacktestHistory saved = backtestHistoryRepository.save(BacktestHistory.builder().userId("user-1")
      .backtestType(BacktestType.STRATEGY_SIMULATION).requestParams("x".repeat(5000)).fxRateMode("auto").build());

    assertThat(type).isEqualTo("text");
    assertThat(backtestHistoryRepository.findById(saved.getId())).get()
      .extracting(BacktestHistory::getRequestParams).asString().hasSize(5000);
  }

  private boolean tableExists(String name) {
    Integer count = jdbcTemplate.queryForObject(
      "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
      Integer.class, name);
    return count != null && count > 0;
  }
}
