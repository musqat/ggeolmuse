package com.muscat.marketdata.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketdata.fx.ingest")
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
        private int lookbackDays = 365;  // 캔들과 동일한 방식으로 통일
    }

    @Getter
    @Setter
    public static class Incremental {
        private boolean enabled = true;
        private int defaultDays = 30;
    }

    @Getter
    @Setter
    public static class Scheduler {
        private boolean enabled = true;
        private String cron = "0 10 11 * * MON-FRI";
        private String zone = "Asia/Seoul";
    }
}