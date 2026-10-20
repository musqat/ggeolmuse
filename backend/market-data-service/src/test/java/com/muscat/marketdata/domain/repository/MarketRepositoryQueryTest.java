package com.muscat.marketdata.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// QueryDSL 로 조립한 조회. 실제 쿼리를 H2 에 보내 정렬 · 필터 · 페이지가 맞는지 본다
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("종목 · 캔들 · 배당 · 환율 조회")
class MarketRepositoryQueryTest {

  private static final LocalDate D1 = LocalDate.of(2024, 3, 1);
  private static final LocalDate D2 = LocalDate.of(2024, 3, 4);
  private static final LocalDate D3 = LocalDate.of(2024, 3, 5);

  @Autowired
  private AssetRepository assetRepository;

  @Autowired
  private CandleRepository candleRepository;

  @Autowired
  private DividendRepository dividendRepository;

  @Autowired
  private FxRateRepository fxRateRepository;

  @MockitoBean
  private AssetEventProducer assetEventProducer;

  @MockitoBean
  private AlphaVantageScheduler alphaVantageScheduler;

  @AfterEach
  void tearDown() {
    candleRepository.deleteAll();
    dividendRepository.deleteAll();
    fxRateRepository.deleteAll();
    assetRepository.deleteAll();
  }

  private void asset(String symbol, String name, String type, Long marketCap, boolean active) {
    Asset asset = Asset.builder().symbol(symbol).name(name).country("US").currency("USD").assetType(type)
      .marketCap(marketCap).build();
    asset.setActive(active);
    assetRepository.save(asset);
  }

  private void yieldAsset(String symbol, String type, String latestClose, boolean active) {
    Asset asset = Asset.builder().symbol(symbol).name(symbol).country("US").currency("USD").assetType(type)
      .latestClose(latestClose == null ? null : new BigDecimal(latestClose)).build();
    asset.setActive(active);
    assetRepository.save(asset);
  }

  private void candle(String symbol, LocalDate date, String dividend) {
    candleRepository.save(Candle.builder().symbol(symbol).date(date).currency("USD")
      .open(BigDecimal.TEN).high(BigDecimal.TEN).low(BigDecimal.TEN).close(BigDecimal.TEN).adjustedClose(BigDecimal.TEN)
      .volume(1L).dividendAmount(new BigDecimal(dividend)).splitCoefficient(BigDecimal.ONE).build());
  }

  private void dividend(String symbol, LocalDate exDate, String amount) {
    dividendRepository.save(Dividend.builder().symbol(symbol).exDate(exDate).amount(new BigDecimal(amount)).currency("USD").build());
  }

  @Nested
  @DisplayName("종목")
  class AssetQueries {

    // 시총 AAPL 3조 · MSFT 2.8조 · SPY(ETF) 5천억 · NOCAP 없음, GONE 은 폐지
    private void assets() {
      asset("MSFT", "Microsoft Corp", "EQUITY", 2_800_000_000_000L, true);
      asset("AAPL", "Apple Inc", "EQUITY", 3_000_000_000_000L, true);
      asset("SPY", "SPDR S&P 500 ETF", "ETF", 500_000_000_000L, true);
      asset("NOCAP", "No Cap Inc", "EQUITY", null, true);
      asset("GONE", "Gone Apple Corp", "EQUITY", 1L, false);
    }

    @Test
    @DisplayName("활성 종목 코드만 이름순으로 준다")
    void activeSymbols() {
      assets();

      assertThat(assetRepository.findActiveSymbols()).containsExactly("AAPL", "MSFT", "NOCAP", "SPY");
    }

    @Test
    @DisplayName("키워드는 대소문자 없이 종목 코드나 이름에서 찾고 개수를 자른다")
    void searchByKeyword() {
      assets();

      assertThat(assetRepository.searchByKeyword("apple", 10)).extracting(Asset::getSymbol).containsExactly("AAPL", "GONE");
      assertThat(assetRepository.searchByKeyword("p", 2)).hasSize(2);
    }

    @Test
    @DisplayName("시총 정렬은 활성 종목만, 시총 없는 종목은 오름 · 내림 모두 맨 뒤다")
    void sortedByMarketCap() {
      assets();

      Page<Asset> desc = assetRepository.findActiveSortedByMarketCap(PageRequest.of(0, 10), false, null);
      Page<Asset> asc = assetRepository.findActiveSortedByMarketCap(PageRequest.of(0, 10), true, "ALL");

      assertThat(desc.getContent()).extracting(Asset::getSymbol).containsExactly("AAPL", "MSFT", "SPY", "NOCAP");
      assertThat(asc.getContent()).extracting(Asset::getSymbol).containsExactly("SPY", "MSFT", "AAPL", "NOCAP");
      assertThat(desc.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("자산 유형은 대소문자 없이 거르고, 페이지는 전체 건수와 함께 자른다")
    void filterAndPage() {
      assets();

      Page<Asset> etf = assetRepository.findActiveSortedByMarketCap(PageRequest.of(0, 10), false, "etf");
      Page<Asset> second = assetRepository.findActiveSortedByMarketCap(PageRequest.of(1, 2), false, null);

      assertThat(etf.getContent()).extracting(Asset::getSymbol).containsExactly("SPY");
      assertThat(second.getContent()).extracting(Asset::getSymbol).containsExactly("SPY", "NOCAP");
      assertThat(second.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("배당수익률 정렬은 최근 1년 배당 합 ÷ 최신 종가 순이고, 수익률을 못 구하는 종목은 뒤에 종목 코드순이며, 비활성은 빼고 유형으로 거른다")
    void sortedByDividendYield() {
      LocalDate today = LocalDate.now();
      yieldAsset("AAPL", "EQUITY", "200", true);
      yieldAsset("MSFT", "EQUITY", "400", true);
      yieldAsset("KO", "EQUITY", "60", true);
      yieldAsset("NOCLOSE", "EQUITY", null, true);
      yieldAsset("OLD", "EQUITY", "100", true);
      yieldAsset("SPY", "ETF", "500", true);
      yieldAsset("GONE", "EQUITY", "10", false);
      for (int m = 2; m <= 8; m += 2) {
        dividend("AAPL", today.minusMonths(m), "1.0");
      }
      dividend("MSFT", today.minusMonths(3), "3.0");
      dividend("KO", today.minusMonths(1), "1.5");
      dividend("NOCLOSE", today.minusMonths(1), "9.0");
      dividend("OLD", today.minusYears(2), "50.0");
      dividend("GONE", today.minusMonths(1), "5.0");
      LocalDate from = today.minusYears(1);

      Page<Asset> desc = assetRepository.findActiveSortedByDividendYield(PageRequest.of(0, 10), false, "ALL", from);
      Page<Asset> asc = assetRepository.findActiveSortedByDividendYield(PageRequest.of(0, 10), true, null, from);
      Page<Asset> etf = assetRepository.findActiveSortedByDividendYield(PageRequest.of(0, 10), false, "etf", from);

      assertThat(desc.getContent()).extracting(Asset::getSymbol).containsExactly("KO", "AAPL", "MSFT", "NOCLOSE", "OLD", "SPY");
      assertThat(asc.getContent()).extracting(Asset::getSymbol).containsExactly("MSFT", "AAPL", "KO", "NOCLOSE", "OLD", "SPY");
      assertThat(etf.getContent()).extracting(Asset::getSymbol).containsExactly("SPY");
      assertThat(desc.getTotalElements()).isEqualTo(6);
      assertThat(dividendRepository.sumAmountBySymbolsSince(List.of("AAPL", "KO", "SPY", "OLD"), from))
        .extracting(DividendRepository.SymbolAmount::getSymbol, sum -> sum.getAmount().stripTrailingZeros())
        .containsExactlyInAnyOrder(tuple("AAPL", new BigDecimal("4")), tuple("KO", new BigDecimal("1.5")));
    }

    @Test
    @DisplayName("요약 정렬은 허용한 기준만 쓰고 모르는 기준은 무시하며, 활성 여부로 거른다")
    void findSorted() {
      assets();

      Page<Asset> byCap = assetRepository.findSorted(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "marketCap")), true);
      Page<Asset> unknownSort = assetRepository.findSorted(PageRequest.of(0, 10, Sort.by("password")), true);
      Page<Asset> delisted = assetRepository.findSorted(PageRequest.of(0, 10, Sort.by("name")), false);

      assertThat(byCap.getContent()).extracting(Asset::getSymbol).containsExactly("AAPL", "MSFT", "SPY", "NOCAP");
      assertThat(unknownSort.getContent()).extracting(Asset::getSymbol).containsExactly("AAPL", "MSFT", "NOCAP", "SPY");
      assertThat(delisted.getContent()).extracting(Asset::getSymbol).containsExactly("GONE");
      assertThat(delisted.getTotalElements()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("캔들")
  class CandleQueries {

    @Test
    @DisplayName("여러 종목 기간 조회는 종목 · 날짜순이고 기간 양 끝을 넣는다")
    void bySymbolsAndRange() {
      candle("MSFT", D2, "0");
      candle("AAPL", D3, "0");
      candle("AAPL", D1, "0");
      candle("AAPL", LocalDate.of(2024, 3, 6), "0");
      candle("NVDA", D2, "0");

      assertThat(candleRepository.findBySymbolsAndDateRange(List.of("AAPL", "MSFT"), D1, D3))
        .extracting(Candle::getSymbol, Candle::getDate)
        .containsExactly(tuple("AAPL", D1), tuple("AAPL", D3), tuple("MSFT", D2));
    }

    @Test
    @DisplayName("배당 포함 캔들은 배당이 0 보다 큰 날만 준다")
    void withDividends() {
      candle("AAPL", D1, "0");
      candle("AAPL", D2, "0.24");

      assertThat(candleRepository.findCandlesWithDividends("AAPL", D1, D3)).extracting(Candle::getDate).containsExactly(D2);
    }

    @Test
    @DisplayName("최신 캔들 · 어떤 날 이전의 최신 캔들 · 캔들 있는 종목 수")
    void latestAndRecent() {
      candle("AAPL", D1, "0");
      candle("AAPL", D2, "0");
      candle("AAPL", D3, "0");
      candle("MSFT", D1, "0");

      assertThat(candleRepository.findLatestBySymbol("AAPL")).hasValueSatisfying(c -> assertThat(c.getDate()).isEqualTo(D3));
      assertThat(candleRepository.findLatestBySymbol("NONE")).isEmpty();
      assertThat(candleRepository.findLatestBySymbolBeforeDate("AAPL", D3)).hasValueSatisfying(c -> assertThat(c.getDate()).isEqualTo(D2));
      assertThat(candleRepository.findLatestBySymbolBeforeDate("AAPL", D1)).isEmpty();
      assertThat(candleRepository.countDistinctSymbols()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("배당 · 환율")
  class DividendAndFxQueries {

    @Test
    @DisplayName("여러 종목 배당은 종목순 · 최신 배당락일 먼저")
    void dividends() {
      dividend("AAPL", D1, "0.24");
      dividend("AAPL", D3, "0.25");
      dividend("KO", D2, "0.48");

      assertThat(dividendRepository.findBySymbolsAndDateRange(List.of("AAPL", "KO"), D1, D3))
        .extracting(Dividend::getSymbol, Dividend::getExDate)
        .containsExactly(tuple("AAPL", D3), tuple("AAPL", D1), tuple("KO", D2));
    }

    @Test
    @DisplayName("기간이 비면 그쪽 경계 없이 찾고, 둘 다 비면 그 종목 배당 전체를 준다")
    void dividendsWithoutRange() {
      dividend("AAPL", D1, "0.24");
      dividend("AAPL", D3, "0.25");

      assertThat(dividendRepository.findBySymbolsAndDateRange(List.of("AAPL"), null, null))
        .extracting(Dividend::getExDate).containsExactly(D3, D1);
      assertThat(dividendRepository.findBySymbolsAndDateRange(List.of("AAPL"), D2, null))
        .extracting(Dividend::getExDate).containsExactly(D3);
      assertThat(dividendRepository.findBySymbolsAndDateRange(List.of("AAPL"), null, D2))
        .extracting(Dividend::getExDate).containsExactly(D1);
    }

    @Test
    @DisplayName("최신 환율은 날짜가 가장 늦은 값, 없으면 비운다")
    void latestFx() {
      assertThat(fxRateRepository.findLatestRate()).isEmpty();

      fxRateRepository.save(FxRate.builder().date(D1).rate(new BigDecimal("1320")).build());
      fxRateRepository.save(FxRate.builder().date(D3).rate(new BigDecimal("1335")).build());

      assertThat(fxRateRepository.findLatestRate()).hasValueSatisfying(r -> assertThat(r.getRate()).isEqualByComparingTo("1335"));
    }
  }
}
