package com.muscat.user.domain.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// V11 까지의 계좌 · 환전 기록에 V12 를 돌린 Postgres 값 확인
@Testcontainers
@DisplayName("환전 달러 누계 마이그레이션 (TestContainers)")
class ExchangeTotalsMigrationIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 2, 10, 0);

  @BeforeEach
  void emptySchema() throws SQLException {
    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA public CASCADE");
      statement.execute("CREATE SCHEMA public");
    }
    Flyway.configure()
      .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .target("11")
      .load()
      .migrate();
  }

  private static void migrateToLatest() {
    Flyway.configure()
      .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .load()
      .migrate();
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  @Test
  @DisplayName("마지막 초기화 뒤 원화 → 달러 환전만으로 달러 누계와 평균 환율을 다시 구하고 환전 없는 계좌는 그대로 둔다")
  void migrate_RecomputesTotalsFromExchangesSinceLastReset() throws SQLException {
    long traded;
    long idle;
    try (Connection connection = connect()) {
      long user = insertUser(connection);
      // 환전 → 역환전으로 달러 0(초기화) → 환전 → 매수 → 환전, 옛 계산의 원화 누계 · 평균 환율
      traded = insertAccount(connection, user, "1001", "2750000", "2500");
      exchange(connection, traded, "KRW", "USD", "1300000", "1000.00", "1300", 0);
      exchange(connection, traded, "USD", "KRW", "1000.00", "1300000", "1300", 1);
      exchange(connection, traded, "KRW", "USD", "1400000", "1000.00", "1400", 2);
      buy(connection, traded, "900.00", 3);
      exchange(connection, traded, "KRW", "USD", "1350000", "1000.00", "1350", 4);
      idle = insertAccount(connection, user, "1002", "0", "0");
    }

    migrateToLatest();

    try (Connection connection = connect()) {
      // (1,400,000 + 1,350,000) 원 / (1,000 + 1,000) 달러
      assertTotals(connection, traded, "2750000", "2000.00", "1375");
      assertTotals(connection, idle, "0", "0", "0");
    }
  }

  @Test
  @DisplayName("환전 기록이 없는 원화 누계는 지금 평균 환율로 달러 누계를 역산한다")
  void migrate_DerivesUsdTotalFromAverageWithoutExchangeRecords() throws SQLException {
    long account;
    try (Connection connection = connect()) {
      account = insertAccount(connection, insertUser(connection), "1003", "1300000", "1300");
    }

    migrateToLatest();

    try (Connection connection = connect()) {
      assertTotals(connection, account, "1300000", "1000.00", "1300");
    }
  }

  @Test
  @DisplayName("초기화 없이 부분 역환전만 있는 계좌는 모든 원화 → 달러 환전으로 두 누계를 다시 구한다")
  void migrate_PartialReverseWithoutReset_KeepsEveryExchange() throws SQLException {
    long account;
    try (Connection connection = connect()) {
      // 환전 → 달러 절반 역환전 → 환전, 옛 계산의 원화 누계 · 평균 환율
      account = insertAccount(connection, insertUser(connection), "1004", "2700000", "1800");
      exchange(connection, account, "KRW", "USD", "1300000", "1000.00", "1300", 0);
      exchange(connection, account, "USD", "KRW", "500.00", "650000", "1300", 1);
      exchange(connection, account, "KRW", "USD", "1400000", "1000.00", "1400", 2);
    }

    migrateToLatest();

    try (Connection connection = connect()) {
      // (1,300,000 + 1,400,000) 원 / (1,000 + 1,000) 달러
      assertTotals(connection, account, "2700000", "2000.00", "1350");
    }
  }

  @Test
  @DisplayName("같은 시각에 기록된 환전은 id 가 큰 쪽을 나중으로 보고 초기화 앞 환전을 뺀다")
  void migrate_SameTimestamp_OrdersById() throws SQLException {
    long account;
    try (Connection connection = connect()) {
      // 환전 → 전액 역환전(초기화) → 환전이 모두 같은 시각
      account = insertAccount(connection, insertUser(connection), "1005", "1400000", "1400");
      exchange(connection, account, "KRW", "USD", "1300000", "1000.00", "1300", 0);
      exchange(connection, account, "USD", "KRW", "1000.00", "1300000", "1300", 0);
      exchange(connection, account, "KRW", "USD", "1400000", "1000.00", "1400", 0);
    }

    migrateToLatest();

    try (Connection connection = connect()) {
      assertTotals(connection, account, "1400000", "1000.00", "1400");
    }
  }

  @Test
  @DisplayName("V12 를 한 번 더 돌려도 두 누계와 평균 환율이 같다")
  void migrate_RunTwice_SameTotals() throws SQLException, IOException {
    long traded;
    long derived;
    try (Connection connection = connect()) {
      long user = insertUser(connection);
      traded = insertAccount(connection, user, "1006", "2750000", "2500");
      exchange(connection, traded, "KRW", "USD", "1300000", "1000.00", "1300", 0);
      exchange(connection, traded, "USD", "KRW", "1000.00", "1300000", "1300", 1);
      exchange(connection, traded, "KRW", "USD", "1400000", "1000.00", "1400", 2);
      buy(connection, traded, "900.00", 3);
      exchange(connection, traded, "KRW", "USD", "1350000", "1000.00", "1350", 4);
      derived = insertAccount(connection, user, "1007", "1300000", "1300");
    }

    migrateToLatest();
    try (Connection connection = connect(); Statement statement = connection.createStatement();
         InputStream script = ExchangeTotalsMigrationIT.class.getResourceAsStream(
           "/db/migration/V12__Add_Account_Total_Exchanged_Usd.sql")) {
      statement.execute(new String(script.readAllBytes(), StandardCharsets.UTF_8));
    }

    try (Connection connection = connect()) {
      assertTotals(connection, traded, "2750000", "2000.00", "1375");
      assertTotals(connection, derived, "1300000", "1000.00", "1300");
    }
  }

  private static void assertTotals(Connection connection, long accountId, String krw, String usd, String rate)
    throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
      "SELECT total_exchanged_krw, total_exchanged_usd, avg_exchange_rate FROM account WHERE id = ?")) {
      statement.setLong(1, accountId);
      try (ResultSet row = statement.executeQuery()) {
        assertThat(row.next()).isTrue();
        assertThat(row.getBigDecimal("total_exchanged_krw")).isEqualByComparingTo(krw);
        assertThat(row.getBigDecimal("total_exchanged_usd")).isEqualByComparingTo(usd);
        assertThat(row.getBigDecimal("avg_exchange_rate")).isEqualByComparingTo(rate);
      }
    }
  }

  private static long insertUser(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO users (email, nickname, keycloak_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?) RETURNING id")) {
      statement.setString(1, "migration@example.com");
      statement.setString(2, "마이그레이션");
      statement.setString(3, "keycloak-migration");
      statement.setTimestamp(4, Timestamp.valueOf(DAY));
      statement.setTimestamp(5, Timestamp.valueOf(DAY));
      return returnedId(statement);
    }
  }

  private static long insertAccount(Connection connection, long userId, String number, String totalKrw, String avgRate)
    throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO account (user_id, account_number, account_name, total_exchanged_krw, avg_exchange_rate, "
        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
      statement.setLong(1, userId);
      statement.setString(2, number);
      statement.setString(3, "계좌 " + number);
      statement.setBigDecimal(4, new BigDecimal(totalKrw));
      statement.setBigDecimal(5, new BigDecimal(avgRate));
      statement.setTimestamp(6, Timestamp.valueOf(DAY));
      statement.setTimestamp(7, Timestamp.valueOf(DAY));
      return returnedId(statement);
    }
  }

  // 환전 내역(amount 는 받은 금액, original_amount 는 낸 금액)
  private static void exchange(Connection connection, long accountId, String from, String to, String paid,
    String received, String rate, int dayOffset) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO account_history (account_id, transaction_type, amount, currency, created_at, from_currency, "
        + "to_currency, exchange_rate, original_amount) VALUES (?, 'EXCHANGE', ?, ?, ?, ?, ?, ?, ?)")) {
      statement.setLong(1, accountId);
      statement.setBigDecimal(2, new BigDecimal(received));
      statement.setString(3, to);
      statement.setTimestamp(4, Timestamp.valueOf(DAY.plusDays(dayOffset)));
      statement.setString(5, from);
      statement.setString(6, to);
      statement.setBigDecimal(7, new BigDecimal(rate));
      statement.setBigDecimal(8, new BigDecimal(paid));
      statement.executeUpdate();
    }
  }

  private static void buy(Connection connection, long accountId, String usd, int dayOffset) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO account_history (account_id, transaction_type, amount, currency, created_at) "
        + "VALUES (?, 'TRADE_BUY', ?, 'USD', ?)")) {
      statement.setLong(1, accountId);
      statement.setBigDecimal(2, new BigDecimal(usd));
      statement.setTimestamp(3, Timestamp.valueOf(DAY.plusDays(dayOffset)));
      statement.executeUpdate();
    }
  }

  private static long returnedId(PreparedStatement statement) throws SQLException {
    try (ResultSet keys = statement.executeQuery()) {
      keys.next();
      return keys.getLong(1);
    }
  }
}
