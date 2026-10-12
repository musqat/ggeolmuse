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

    // 수수료는 거래금액 × 계좌 수수료율. 계좌에 수수료율이 없을 때만 이 기본값을 쓴다
    @Data
    public static class Fee {
        private BigDecimal defaultRate = new BigDecimal("0.0025");
    }

    @Data
    public static class Calculation {
        private int pricePrecision = 2;
    }
}
