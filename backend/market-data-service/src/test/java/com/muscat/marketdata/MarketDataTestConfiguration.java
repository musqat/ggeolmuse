package com.muscat.marketdata;

import com.muscat.marketdata.datasource.alphavantage.provider.AlphaVantageCandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.SymbolSource;
import com.muscat.marketdata.datasource.fixed.FixedQuoteSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

// 고정 시세 프로필 밖 테스트 컨텍스트의 외부 시세 목
@TestConfiguration
@Profile("!" + FixedQuoteSource.PROFILE)
public class MarketDataTestConfiguration {

  @Bean
  @Primary
  public SymbolSource mockSymbolSource() {
    return Mockito.mock(SymbolSource.class);
  }

  @Bean
  @Primary
  public AlphaVantageCandleSource mockCandleSource() {
    return Mockito.mock(AlphaVantageCandleSource.class);
  }

  @Bean
  @Primary
  public DividendSource mockDividendSource() {
    return Mockito.mock(DividendSource.class);
  }
}
