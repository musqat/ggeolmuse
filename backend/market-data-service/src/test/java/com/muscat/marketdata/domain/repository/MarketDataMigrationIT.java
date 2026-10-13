package com.muscat.marketdata.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 다른 테스트는 H2 에 create-drop 으로 떠 마이그레이션을 거치지 않는다. 여기서만 운영과 같이 Postgres 에
// Flyway 로 테이블을 만들고 엔티티가 테이블과 맞는지(validate) 본다. 마이그레이션 없이 엔티티만 바꾸면 여기서 걸린다
@SpringBootTest(properties = {
  "spring.flyway.enabled=true",
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("마이그레이션과 엔티티가 운영 Postgres 에서 맞는다")
class MarketDataMigrationIT {

  // 운영 RDS 와 같은 Postgres 15
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired
  private CandleRepository candleRepository;

  @Autowired
  private AssetRepository assetRepository;

  @Autowired
  private FxRateRepository fxRateRepository;

  @MockitoBean
  private AssetEventProducer assetEventProducer;

  @MockitoBean
  private AlphaVantageScheduler alphaVantageScheduler;

  @AfterEach
  void tearDown() {
    candleRepository.deleteAll();
    assetRepository.deleteAll();
  }

  @Test
  @DisplayName("마이그레이션으로 만든 테이블에 종목 · 봉을 쓰고 QueryDSL 조회로 읽는다")
  void writesAndReadsThroughMigratedSchema() {
    assetRepository.save(Asset.builder().symbol("AAPL").name("Apple").country("US").currency("USD")
      .assetType("EQUITY").build());
    candleRepository.save(Candle.builder().symbol("AAPL").date(LocalDate.of(2024, 3, 14)).currency("USD")
      .open(BigDecimal.TEN).high(BigDecimal.TEN).low(BigDecimal.TEN).close(new BigDecimal("172.50"))
      .adjustedClose(new BigDecimal("172.50")).volume(1L).build());
    candleRepository.save(Candle.builder().symbol("AAPL").date(LocalDate.of(2024, 3, 15)).currency("USD")
      .open(BigDecimal.TEN).high(BigDecimal.TEN).low(BigDecimal.TEN).close(new BigDecimal("173.00"))
      .adjustedClose(new BigDecimal("173.00")).volume(1L).build());

    assertThat(candleRepository.findLatestBySymbol("AAPL"))
      .hasValueSatisfying(candle -> assertThat(candle.getDate()).isEqualTo(LocalDate.of(2024, 3, 15)));
    assertThat(candleRepository.countDistinctSymbols()).isEqualTo(1);
    assertThat(assetRepository.findActiveSymbols()).containsExactly("AAPL");
  }

  @Test
  @DisplayName("환율이 없으면 최신 환율 조회가 비어 있다")
  void latestFxRate_EmptyOnFreshSchema() {
    assertThat(fxRateRepository.findLatestRate()).isEmpty();
  }
}
