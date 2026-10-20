package com.muscat.marketdata.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.model.DividendYieldRule;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
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

  @Autowired
  private DividendRepository dividendRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoBean
  private AssetEventProducer assetEventProducer;

  @MockitoBean
  private AlphaVantageScheduler alphaVantageScheduler;

  @AfterEach
  void tearDown() {
    candleRepository.deleteAll();
    dividendRepository.deleteAll();
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

  @Test
  @DisplayName("배당수익률 정렬 쿼리는 Postgres 에서도 특별 배당을 뺀 수익률 순이고, 못 구하거나 상한을 넘는 종목은 뒤에 두며, 유형 필터와 개수를 지킨다")
  void dividendYieldSortOnPostgres() {
    LocalDate today = LocalDate.now();
    assetRepository.save(yieldAsset("AAPL", "EQUITY", "200"));
    assetRepository.save(yieldAsset("KO", "EQUITY", "60"));
    assetRepository.save(yieldAsset("NODIV", "EQUITY", "10"));
    assetRepository.save(yieldAsset("SPY", "ETF", "500"));
    assetRepository.save(yieldAsset("SPECIAL", "EQUITY", "10"));
    assetRepository.save(yieldAsset("OUTLIER", "ETF", "10"));
    dividendRepository.save(Dividend.builder().symbol("AAPL").exDate(today.minusMonths(2)).amount(new BigDecimal("4.0")).currency("USD").build());
    dividendRepository.save(Dividend.builder().symbol("KO").exDate(today.minusMonths(1)).amount(new BigDecimal("1.5")).currency("USD").build());
    dividendRepository.save(Dividend.builder().symbol("SPECIAL").exDate(today.minusMonths(5)).amount(new BigDecimal("5.0")).currency("USD").build());
    dividendRepository.save(Dividend.builder().symbol("SPECIAL").exDate(today.minusMonths(1)).amount(new BigDecimal("0.3")).currency("USD").build());
    for (int m = 1; m <= 4; m++) {
      dividendRepository.save(Dividend.builder().symbol("OUTLIER").exDate(today.minusMonths(m)).amount(new BigDecimal("1.5")).currency("USD").build());
    }
    LocalDate from = today.minusYears(1);
    DividendYieldRule rule = new DividendYieldRule(from, new BigDecimal("0.20"), new BigDecimal("0.50"));

    Page<Asset> all = assetRepository.findActiveSortedByDividendYield(PageRequest.of(0, 10), false, "ALL", rule);
    Page<Asset> etf = assetRepository.findActiveSortedByDividendYield(PageRequest.of(0, 10), false, "etf", rule);

    assertThat(all.getContent()).extracting(Asset::getSymbol).containsExactly("SPECIAL", "KO", "AAPL", "NODIV", "OUTLIER", "SPY");
    assertThat(all.getTotalElements()).isEqualTo(6);
    assertThat(etf.getContent()).extracting(Asset::getSymbol).containsExactly("OUTLIER", "SPY");
    assertThat(dividendRepository.sumRegularAmountBySymbolsSince(List.of("AAPL", "KO", "SPY", "SPECIAL"), from, rule.specialRatio()))
      .containsOnlyKeys("AAPL", "KO", "SPECIAL");
  }

  @Test
  @DisplayName("마이그레이션 뒤 배당 금액 순 인덱스는 없다")
  void highDividendIndexDropped() {
    Integer count = jdbcTemplate.queryForObject(
      "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_dividend_amount_date'", Integer.class);

    assertThat(count).isZero();
  }

  private static Asset yieldAsset(String symbol, String type, String latestClose) {
    return Asset.builder().symbol(symbol).name(symbol).country("US").currency("USD").assetType(type)
      .latestClose(new BigDecimal(latestClose)).build();
  }

  @Test
  @DisplayName("V10 은 이름이 펀드인데 주식으로 든 종목만 ETF 로 바꾸고, Netflix 처럼 낱말 안에 든 etf 는 그대로 둔다")
  void fundAssetTypesFixed() throws IOException {
    assetRepository.save(typedAsset("YBST", "GraniteShares YieldBoost Single Stock Universe ETF", "EQUITY"));
    assetRepository.save(typedAsset("JMMF", "JPMorgan 100 U.S. Treasury Securities Money Market", "EQUITY"));
    assetRepository.save(typedAsset("XYZ", "Some Exchange Traded Fund", "EQUITY"));
    assetRepository.save(typedAsset("NFLX", "Netflix Inc", "EQUITY"));
    assetRepository.save(typedAsset("SPY", "SPDR S&P 500 ETF Trust", "ETF"));

    jdbcTemplate.execute(migration("V10__Fix_Fund_Asset_Types.sql"));

    assertThat(assetRepository.findAll()).extracting(Asset::getSymbol, Asset::getAssetType).containsExactlyInAnyOrder(
      tuple("YBST", "ETF"), tuple("JMMF", "ETF"), tuple("XYZ", "ETF"), tuple("NFLX", "EQUITY"), tuple("SPY", "ETF"));
  }

  @Test
  @DisplayName("V11 은 거래소 시험 종목을 기호 · 이름으로 찾아 비활성으로 바꾸고, ETF 인 TEST 와 다른 종목은 그대로 둔다")
  void testIssuesDeactivated() throws IOException {
    assetRepository.save(typedAsset("MTEST-A", "Nasdaq Test Issue", "EQUITY"));
    assetRepository.save(typedAsset("ZTST", "Ztst", "EQUITY"));
    assetRepository.save(typedAsset("CBO", "Listed Test Security", "EQUITY"));
    assetRepository.save(typedAsset("TEST", "Test Ticker ETF", "ETF"));
    assetRepository.save(typedAsset("AAPL", "Apple Inc", "EQUITY"));

    jdbcTemplate.execute(migration("V11__Deactivate_Test_Issues.sql"));

    assertThat(assetRepository.findAll()).extracting(Asset::getSymbol, Asset::getActive).containsExactlyInAnyOrder(
      tuple("MTEST-A", false), tuple("ZTST", false), tuple("CBO", false), tuple("TEST", true), tuple("AAPL", true));
  }

  @Test
  @DisplayName("V12 는 상장 목록 이름이 빈 행을 지금 이름으로 채운다")
  void sourceNameBackfilled() throws IOException {
    assetRepository.save(typedAsset("AAPL", "Apple Inc", "EQUITY"));

    jdbcTemplate.execute(migration("V12__Add_Asset_Source_Name.sql"));

    assertThat(jdbcTemplate.queryForObject("SELECT source_name FROM asset WHERE symbol = 'AAPL'", String.class))
      .isEqualTo("Apple Inc");
  }

  private static Asset typedAsset(String symbol, String name, String type) {
    return Asset.builder().symbol(symbol).name(name).country("US").currency("USD").assetType(type).build();
  }

  private static String migration(String file) throws IOException {
    return new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8);
  }
}
