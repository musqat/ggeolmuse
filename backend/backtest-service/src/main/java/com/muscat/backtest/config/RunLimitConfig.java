package com.muscat.backtest.config;

import com.muscat.backtest.common.web.BacktestRunLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 분석 · 시뮬레이션 경로의 동시 실행 상한(기본 10건, 자리가 없으면 기본 5초까지 기다림)
@Configuration
public class RunLimitConfig implements WebMvcConfigurer {

  private final int maxConcurrentRuns;
  private final Duration wait;

  public RunLimitConfig(@Value("${backtest.run-limit.max-concurrent:10}") int maxConcurrentRuns,
                        @Value("${backtest.run-limit.wait:5s}") Duration wait) {
    this.maxConcurrentRuns = maxConcurrentRuns;
    this.wait = wait;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new BacktestRunLimiter(maxConcurrentRuns, wait))
      .addPathPatterns("/api/analysis/**", "/api/trading-simulation/**");
  }
}
