package com.muscat.marketdata.datasource.fixed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// 클래스패스 고정 데이터(fixed-quotes/*.csv)를 읽는 공급원
@DisplayName("고정 시세 공급원")
class FixedQuoteSourceTest {

  private final FixedQuoteSource source = new FixedQuoteSource();

  private static ApplicationContextRunner withProfiles(String... profiles) {
    return new ApplicationContextRunner()
        .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
        .withUserConfiguration(FixedQuoteSource.class);
  }

  @Test
  @DisplayName("fixed-quotes 프로필만이면 등록, prod 와 같이 켜면 등록하지 않음")
  void notRegisteredWithProd() {
    withProfiles(FixedQuoteSource.PROFILE).run(context -> assertThat(context).hasSingleBean(FixedQuoteSource.class));
    withProfiles("prod", FixedQuoteSource.PROFILE)
        .run(context -> assertThat(context).doesNotHaveBean(FixedQuoteSource.class));
  }

  @Test
  @DisplayName("목록은 고정 종목 셋을 시가총액 · 유형과 함께 주고 상한 없는 전체 목록이다")
  void listing() {
    List<Asset> assets = source.fetch();

    assertThat(assets)
        .extracting(Asset::getSymbol, Asset::getName, Asset::getAssetType, Asset::getMarketCap, Asset::getCurrency)
        .containsExactly(
            tuple("FQA", "Fixed Quote Alpha Inc.", "EQUITY", 3_000_000_000_000L, "USD"),
            tuple("FQB", "Fixed Quote Beta Corp.", "EQUITY", 2_000_000_000_000L, "USD"),
            tuple("SPY", "SPDR S&P 500 ETF Trust", "ETF", 600_000_000_000L, "USD"));
    assertThat(assets).extracting(Asset::getSourceName).containsExactly(
        "Fixed Quote Alpha Inc.", "Fixed Quote Beta Corp.", "SPDR S&P 500 ETF Trust");
    assertThat(source.isCapped()).isFalse();
  }

  @Test
  @DisplayName("목록은 부를 때마다 새 종목 객체라 받은 쪽이 고쳐도 다음 목록은 그대로다")
  void listingReturnsFreshAssets() {
    source.fetch().get(0).setName("바뀐 이름");

    assertThat(source.fetch().get(0).getName()).isEqualTo("Fixed Quote Alpha Inc.");
  }

  @Test
  @DisplayName("일봉은 요청 기간 안의 거래일만 날짜순으로 주고 휴장일은 없다")
  void candlesInWindow() {
    List<Candle> candles = source.fetchDailyAdjusted("FQA", LocalDate.of(2023, 7, 1), LocalDate.of(2023, 7, 6));

    assertThat(candles)
        .extracting(Candle::getSymbol, Candle::getDate, Candle::getOpen, Candle::getHigh, Candle::getLow,
            Candle::getClose, Candle::getAdjustedClose, Candle::getVolume, Candle::getCurrency)
        .containsExactly(
            tuple("FQA", LocalDate.of(2023, 7, 3), new BigDecimal("102.11"), new BigDecimal("102.48"),
                new BigDecimal("101.73"), new BigDecimal("102.09"), new BigDecimal("102.09"), 36_313_981L, "USD"),
            tuple("FQA", LocalDate.of(2023, 7, 5), new BigDecimal("101.75"), new BigDecimal("104.57"),
                new BigDecimal("101.38"), new BigDecimal("104.24"), new BigDecimal("104.24"), 29_105_848L, "USD"),
            tuple("FQA", LocalDate.of(2023, 7, 6), new BigDecimal("104.53"), new BigDecimal("106.00"),
                new BigDecimal("104.11"), new BigDecimal("105.73"), new BigDecimal("105.73"), 33_267_515L, "USD"));
  }

  @Test
  @DisplayName("고정 기간 전체는 종목마다 2023-07-03 ~ 2025-06-30 의 500 거래일이다")
  void candlesFullRange() {
    for (String symbol : List.of("FQA", "FQB", "SPY")) {
      List<Candle> candles = source.fetchDailyAdjusted(symbol, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1));

      assertThat(candles).hasSize(500);
      assertThat(candles.get(0).getDate()).isEqualTo(LocalDate.of(2023, 7, 3));
      assertThat(candles.get(499).getDate()).isEqualTo(LocalDate.of(2025, 6, 30));
    }
  }

  @Test
  @DisplayName("모르는 종목이나 고정 기간 밖 요청은 빈 일봉이다")
  void candlesOutside() {
    assertThat(source.fetchDailyAdjusted("AAPL", LocalDate.of(2023, 7, 1), LocalDate.of(2025, 7, 1))).isEmpty();
    assertThat(source.fetchDailyAdjusted("fqa", LocalDate.of(2023, 7, 3), LocalDate.of(2023, 7, 3))).hasSize(1);
    assertThat(source.fetchDailyAdjusted("FQA", LocalDate.of(2025, 7, 1), LocalDate.of(2026, 11, 13))).isEmpty();
  }

  @Test
  @DisplayName("배당은 요청 기간 안의 권리락일과 주당 금액을 달러로 준다")
  void dividends() {
    List<DividendDto> dividends = source.fetchDividends("SPY", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));

    assertThat(dividends)
        .extracting(DividendDto::getSymbol, DividendDto::getExDate, DividendDto::getAmount, DividendDto::getCurrency)
        .containsExactly(
            tuple("SPY", LocalDate.of(2023, 9, 15), new BigDecimal("1.5800"), "USD"),
            tuple("SPY", LocalDate.of(2023, 12, 15), new BigDecimal("1.6100"), "USD"));
    assertThat(source.fetchDividends("FQB", LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1))).isEmpty();
  }

  @Test
  @DisplayName("환율은 요청한 날 값, 주말은 앞 평일 값, 고정 기간 앞뒤는 첫날 · 마지막 날 값이다")
  void fx() {
    assertThat(source.fetchFx(LocalDate.of(2023, 7, 3))).contains(new BigDecimal("1344.04"));
    assertThat(source.fetchFx(LocalDate.of(2023, 7, 8))).isEqualTo(source.fetchFx(LocalDate.of(2023, 7, 7)));
    assertThat(source.fetchFx(LocalDate.of(2016, 1, 4))).contains(new BigDecimal("1344.04"));
    assertThat(source.fetchFx(LocalDate.of(2026, 11, 13))).isEqualTo(source.fetchFx(LocalDate.of(2025, 6, 30)));
  }

  @Test
  @DisplayName("종목 정보는 목록에 있는 종목만 주고 대소문자는 가리지 않는다")
  void assetInfo() {
    assertThat(source.getAsset("fqb")).extracting(Asset::getSymbol, Asset::getName)
        .containsExactly("FQB", "Fixed Quote Beta Corp.");
    assertThat(source.getAsset("AAPL")).isNull();
  }

  @Test
  @DisplayName("시가총액은 목록 값 그대로라 갱신이 없다")
  void marketCapUnchanged() {
    List<Asset> assets = source.fetch();

    assertThat(source.updateAllMarketCaps(assets)).isZero();
    assertThat(source.updateMarketCap("FQA")).isFalse();
    assertThat(assets).extracting(Asset::getMarketCap)
        .containsExactly(3_000_000_000_000L, 2_000_000_000_000L, 600_000_000_000L);
  }
}
