package com.muscat.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MarketData 애플리케이션

 * 시작 시 자동 실행:
 * 1. SymbolDataCollector (Order=1): 심볼 수집
 * 2. FxCollector (Order=2): FX 데이터 수집
 */
@SpringBootApplication
@EnableScheduling
public class MarketDataApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarketDataApplication.class, args);
  }
}