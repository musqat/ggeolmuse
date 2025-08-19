package com.muscat.marketdata.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

/**
 * FX 수집 설정
 */
@Configuration
@ConfigurationProperties(prefix = "marketdata.fx.feed")
@Getter
@Setter
public class FxCollectProps {

  private Backfill backfill = new Backfill();
  private Incremental incremental = new Incremental();
  private Scheduler scheduler = new Scheduler();

  @Getter
  @Setter
  public static class Backfill {
    /**
     * 앱 기동 시 1회 백필 수행 여부
     */
    private boolean enabled = false;

    /**
     * 포함 시작일 (enabled=true일 때 필수)
     */
    private LocalDate start;

    /**
     * 포함 종료일 (null이면 오늘)
     */
    private LocalDate end;
  }

  @Getter
  @Setter
  public static class Incremental {
    /**
     * 앱 기동 시 증분 collect 수행 여부
     */
    private boolean enabled = true;

    /**
     * 기존 데이터가 없을 때 기본으로 몇 일 전부터 채울지 (기본 7일)
     */
    private int defaultDays = 7;
  }

  @Getter
  @Setter
  public static class Scheduler {
    /**
     * 매일 11:10 KST 스케줄 구동 여부
     */
    private boolean enabled = true;

    /**
     * 스케줄 크론 표현식
     */
    private String cron = "0 10 11 * * MON-FRI";

    /**
     * 스케줄 타임존
     */
    private String zone = "Asia/Seoul";
  }
}