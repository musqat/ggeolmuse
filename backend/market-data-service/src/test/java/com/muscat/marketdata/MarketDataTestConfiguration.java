package com.muscat.marketdata;

import com.muscat.marketdata.datasource.alphavantage.provider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.SymbolSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MarketDataTestConfiguration {

  @Bean
  @Primary
  public SymbolSource mockSymbolSource() {
    return Mockito.mock(SymbolSource.class);
  }

  @Bean
  @Primary
  public CandleSource mockCandleSource() {
    return Mockito.mock(CandleSource.class);
  }

  @Bean
  @Primary
  public DividendSource mockDividendSource() {
    return Mockito.mock(DividendSource.class);
  }
}
