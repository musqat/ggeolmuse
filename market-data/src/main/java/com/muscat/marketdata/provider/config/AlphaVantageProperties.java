package com.muscat.marketdata.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "alphavantage")
public class AlphaVantageProperties {
    private String apiKey;
    private boolean enabled = true;
    private int rateLimitPerSecond = 5;
    private int timeoutSeconds = 15;
    private String baseUrl = "https://www.alphavantage.co/query";
}