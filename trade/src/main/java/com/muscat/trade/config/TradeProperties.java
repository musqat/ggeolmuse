package com.muscat.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "trade")
public class TradeProperties {

    private Fee fee = new Fee();
    private Calculation calculation = new Calculation();
    private Validation validation = new Validation();
    private Cache cache = new Cache();

    @Data
    public static class Fee {
        private BigDecimal defaultRate = new BigDecimal("0.0025");
        private BigDecimal minimumAmount = new BigDecimal("1.00");
    }

    @Data
    public static class Calculation {
        private int pricePrecision = 2;
        private int quantityPrecision = 6;
    }

    @Data
    public static class Validation {
        private BigDecimal maxPriceDeviation = new BigDecimal("0.20");
        private BigDecimal minTradeAmount = new BigDecimal("1.00");
    }

    @Data
    public static class Cache {
        private long marketDataTtl = 300; // seconds
    }
}