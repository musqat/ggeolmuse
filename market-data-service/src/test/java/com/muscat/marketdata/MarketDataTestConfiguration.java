package com.muscat.marketdata;

import com.muscat.marketdata.provider.MarketDataProvider;
import com.muscat.marketdata.provider.MarketDataProvider.CandleSource;
import com.muscat.marketdata.provider.MarketDataProvider.DividendSource;
import com.muscat.marketdata.provider.MarketDataProvider.SymbolSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MarketDataTestConfiguration {

    private final MarketDataProvider.DividendSource DividendSource;

    public MarketDataTestConfiguration(DividendSource DividendSource) {
        this.DividendSource = DividendSource;
    }

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