package com.muscat.marketdata.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


/**
 * Alpha Vantage API 설정
 *   api-key: ${ALPHAVANTAGE_API_KEY}
 */
@Validated
@ConfigurationProperties(prefix = "alpha-vantage")
public record AlphaVantageProperties(
    String apiKey
) {}