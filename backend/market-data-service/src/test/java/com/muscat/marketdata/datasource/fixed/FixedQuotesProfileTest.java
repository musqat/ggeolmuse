package com.muscat.marketdata.datasource.fixed;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.datasource.common.MarketDataProvider.AssetInfoSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.MarketCapSource;
import com.muscat.marketdata.datasource.common.SymbolCatalog;
import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// fixed-quotes 프로필의 외부 시세 자리 배선(야후 공급원 위에 고정 공급원)
@SpringBootTest
@ActiveProfiles({"test", FixedQuoteSource.PROFILE})
@DisplayName("고정 시세 프로필")
class FixedQuotesProfileTest {

  @Autowired
  private CandleSource candleSource;

  @Autowired
  private DividendSource dividendSource;

  @Autowired
  private FxSource fxSource;

  @Autowired
  private AssetInfoSource assetInfoSource;

  @Autowired
  private MarketCapSource marketCapSource;

  @Autowired
  private SymbolCatalog symbolCatalog;

  @Autowired
  private ConfigurableListableBeanFactory beanFactory;

  @Test
  @DisplayName("일봉 · 배당 · 환율 · 종목 정보 · 시가총액 자리에 고정 공급원이 들어간다")
  void sourcesAreFixed() {
    assertThat(List.of(candleSource, dividendSource, fxSource, assetInfoSource, marketCapSource))
        .allMatch(FixedQuoteSource.class::isInstance);
  }

  @Test
  @DisplayName("종목 목록은 고정 종목 셋이고 상한 · 대체 없는 전체 목록이다")
  void catalogListsFixedSymbols() {
    SymbolCatalog.Listing listing = symbolCatalog.fetchListing();

    assertThat(listing.assets()).extracting(Asset::getSymbol).containsExactly("FQA", "FQB", "SPY");
    assertThat(listing.complete()).isTrue();
    assertThat(beanFactory.getDependenciesForBean("symbolCatalog")).contains("fixedQuoteSource")
        .doesNotContain("listingStatusSource");
  }

  @Test
  @DisplayName("야후 스케줄러의 시가총액 갱신도 고정 공급원을 부른다")
  void schedulerUsesFixedMarketCaps() {
    assertThat(beanFactory.getDependenciesForBean("yahooScheduler")).contains("fixedQuoteSource")
        .doesNotContain("yahooMarketCapService");
  }
}
