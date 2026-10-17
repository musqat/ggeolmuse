package com.muscat.marketdata.datasource.alphavantage.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

// AlphaVantage API Rate Limiter(무료 분당 5회 · 프리미엄 분당 75회)
@Slf4j
@Component
public class AlphaVantageRateLimiter {

    private final long millisBetweenCalls;
    private Instant lastCallTime = Instant.EPOCH;

    public AlphaVantageRateLimiter(
        @Value("${alphavantage.rate-limit-per-minute:5}") long callsPerMinute
    ) {
        this.millisBetweenCalls = 60_000 / callsPerMinute;
        log.info("RateLimiter: {} calls/min ({}ms interval)", callsPerMinute, millisBetweenCalls);
    }

    public synchronized void waitIfNeeded() {
        try {
            long elapsed = Instant.now().toEpochMilli() - lastCallTime.toEpochMilli();
            if (elapsed < millisBetweenCalls) {
                Thread.sleep(millisBetweenCalls - elapsed);
            }
            lastCallTime = Instant.now();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
