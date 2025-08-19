package com.muscat.marketdata.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SymbolData 수집 설정
 */
@Configuration
@ConfigurationProperties(prefix = "marketdata.feed")
@Getter
@Setter
public class SymbolDataCollectProps {

  private Symbol symbol = new Symbol();
  private Schedule schedule = new Schedule();

  @Getter
  @Setter
  public static class Symbol {
    /**
     * 앱 시작 시 심볼 수집 여부
     */
    private boolean enabled = true;
  }

  @Getter
  @Setter
  public static class Schedule {
    /**
     * 일일 배치 스케줄 활성화 여부
     */
    private boolean enabled = true;

    /**
     * 배치 실행 크론 표현식
     */
    private String cron = "0 0 3 * * *";

    /**
     * 시장 타임존
     */
    private String timezone = "America/New_York";

    /**
     * 조회 기간 (일)
     */
    private int lookbackDays = 365;
  }
}