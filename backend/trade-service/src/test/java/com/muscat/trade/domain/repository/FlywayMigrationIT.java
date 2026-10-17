package com.muscat.trade.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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

  @Test
  @DisplayName("이전 이미지가 뜨도록 옛 dividends 표를 한 릴리스 남긴다")
  void migrate_KeepsOldDividendsTable() throws Exception {
    Flyway.configure()
      .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .load()
      .migrate();

    try (Connection connection = DriverManager.getConnection(
           postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
         ResultSet tables = connection.getMetaData().getTables(null, "public", "dividends", new String[] {"TABLE"})) {
      assertThat(tables.next()).isTrue();
    }
  }
}
