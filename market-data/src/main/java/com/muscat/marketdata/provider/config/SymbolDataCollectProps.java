package com.muscat.marketdata.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketdata.feed")
@Getter
@Setter
public class SymbolDataCollectProps {

    private Symbol symbol = new Symbol();
    private Schedule schedule = new Schedule();

    @Getter
    @Setter
    public static class Symbol {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Schedule {
        private boolean enabled = true;
        private String cron = "0 0 3 * * *";
        private String timezone = "America/New_York";
        private int lookbackDays = 365;
    }
}