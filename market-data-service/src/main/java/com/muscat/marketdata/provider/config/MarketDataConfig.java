package com.muscat.marketdata.provider.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    AlphaVantageProperties.class,
    SymbolDataCollectProps.class
})
public class MarketDataConfig {
}