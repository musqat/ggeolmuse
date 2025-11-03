package com.muscat.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Market Data Service
 * - SymbolCollector: 종목 자동 수집 (설정 기반)
 * - FxCollector: 환율 데이터 수집
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
@ConfigurationPropertiesScan
@ComponentScan(basePackages = {"com.muscat.marketdata", "com.muscat.commonlib"})
public class MarketDataApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarketDataApplication.class, args);
  }
}
