package com.muscat.jpatest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TradePostgresJpaTest
@DisplayName("저장소 테스트 DB")
class TradePostgresJpaTestSetupTest {

  @Autowired
  private DataSource dataSource;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("저장소 테스트는 Flyway 로 만든 Postgres 에서 돈다")
  void runsOnFlywayPostgres() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
    }
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
      .isPositive();
  }
}
