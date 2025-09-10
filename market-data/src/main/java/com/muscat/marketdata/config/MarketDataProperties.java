package com.muscat.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "marketdata.constants")
public class MarketDataProperties {

    private Fx fx = new Fx();
    private Batch batch = new Batch();
    private Database database = new Database();
    private Calculation calculation = new Calculation();

    @Data
    public static class Fx {
        private BigDecimal defaultBaseRate = new BigDecimal("1350");
        private int scale = 6;
        private int maxFallbackDays = 7;
    }
    
    @Data
    public static class Calculation {
        private int percentScale = 4;  // 퍼센트 계산 소수점 자릿수
        private BigDecimal percentageMultiplier = new BigDecimal("100");
    }

    @Data
    public static class Batch {
        private int minRpm = 1;
        private long maxBackoffMs = 30000L;
        private long millisPerMinute = 60000L;
    }

    @Data
    public static class Database {
        private int symbolMaxLength = 16;
        private int currencyLength = 3;
        private int decimalPrecision = 19;
        private int decimalScale = 8;
    }
}