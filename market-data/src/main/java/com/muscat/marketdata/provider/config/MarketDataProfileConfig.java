package com.muscat.marketdata.provider.config;

import com.muscat.marketdata.provider.MarketDataProvider;
import com.muscat.marketdata.provider.av.AlphaVantageCandleSource;
import com.muscat.marketdata.provider.av.AlphaVantageDividendSource;
import com.muscat.marketdata.provider.stooq.StooqNasdaq100JsoupSource;
import com.muscat.marketdata.provider.yf.YahooCandleSource;
import com.muscat.marketdata.provider.yf.YahooDividendSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * 환경별 데이터 소스 바인딩 설정
 * - dev: Yahoo Finance (무료 스크래핑)
 * - prod: Alpha Vantage (유료 API)
 */
@Configuration
public class MarketDataProfileConfig {

  /**
   * 공통 심볼 소스 (Stooq NASDAQ 100)
   */
  @Bean
  @Primary
  public StooqNasdaq100JsoupSource stooqNasdaq100Source() {
    return new StooqNasdaq100JsoupSource();
  }

  /**
   * 개발 환경 설정 (Yahoo Finance)
   */
  @Configuration
  @Profile("dev")
  @RequiredArgsConstructor
  static class DevelopmentConfig {

    private final YahooCandleSource yahooCandleSource;
    private final YahooDividendSource yahooDividendSource;

    @Bean
    @Primary
    public MarketDataProvider.CandleSource developmentCandleSource() {
      return yahooCandleSource::fetchDailyAdjusted;
    }

    @Bean
    @Primary
    public MarketDataProvider.DividendSource developmentDividendSource() {
      return yahooDividendSource::fetchDividends;
    }
  }

  /**
   * 운영 환경 설정 (Alpha Vantage)
   */
  @Configuration
  @Profile("prod")
  @RequiredArgsConstructor
  static class ProductionConfig {

    private final AlphaVantageCandleSource alphaVantageCandleSource;
    private final AlphaVantageDividendSource alphaVantageDividendSource;

    @Bean
    @Primary
    public MarketDataProvider.CandleSource productionCandleSource() {
      return alphaVantageCandleSource::fetchDailyAdjusted;
    }

    @Bean
    @Primary
    public MarketDataProvider.DividendSource productionDividendSource() {
      return alphaVantageDividendSource::fetchDividends;
    }
  }
}
