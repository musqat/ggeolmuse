package com.muscat.trade.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 오늘 날짜 · 계산 시각용 시계(시스템 기본 시간대)
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
