package com.muscat.marketdata.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

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
        private boolean enabled = false;
        private LocalDate start;
        private LocalDate end;
    }

    @Getter
    @Setter
    public static class Incremental {
        private boolean enabled = true;
        private int defaultDays = 7;
    }

    @Getter
    @Setter
    public static class Scheduler {
        private boolean enabled = true;
        private String cron = "0 10 11 * * MON-FRI";
        private String zone = "Asia/Seoul";
    }
}