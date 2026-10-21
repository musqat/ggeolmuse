package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Flyway 마이그레이션을 끝까지 돌린 Postgres 스키마 확인
@Testcontainers
@DisplayName("Flyway 마이그레이션 (TestContainers)")
class FlywayMigrationIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  private static void migrate() {
    Flyway.configure()
      .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .load()
      .migrate();
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  @Test
  @DisplayName("배당 몫 표로 옮긴 옛 dividends 표를 지운다")
  void migrate_DropsOldDividendsTable() throws Exception {
    migrate();

    try (Connection connection = connect();
         ResultSet tables = connection.getMetaData().getTables(null, "public", "dividends", new String[] {"TABLE"})) {
      assertThat(tables.next()).isFalse();
    }
  }

  @Test
  @DisplayName("대기 매수 다시 보내기 · 지표 조회가 대기 행만 훑도록 부분 인덱스를 만든다")
  void migrate_AddsPendingBuyIndex() throws Exception {
    migrate();

    try (Connection connection = connect();
         PreparedStatement statement = connection.prepareStatement(
           "SELECT indexdef FROM pg_indexes WHERE tablename = 'trades' AND indexname = 'idx_trades_pending'");
         ResultSet index = statement.executeQuery()) {
      assertThat(index.next()).isTrue();
      assertThat(index.getString("indexdef")).contains("(event_published_at)").contains("'PENDING'");
    }
  }
}
