package com.muscat.marketdata.datasource.alphavantage.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.datasource.common.MarketDataProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AlphaVantage 환율 데이터 소스
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class FxSource implements MarketDataProvider.FxSource {

    private final AlphaVantageClient client;
    private final AlphaVantageRateLimiter rateLimiter;

    @Override
    public Optional<BigDecimal> fetchFx(LocalDate date) {
        BigDecimal rate = fetchFxRate(date);
        return Optional.ofNullable(rate);
    }

    public BigDecimal fetchFxRate(LocalDate date) {
        rateLimiter.waitIfNeeded();

        JsonNode response = client.get("FX_DAILY",
            Map.of("from_symbol", "USD", "to_symbol", "KRW", "outputsize", "full"));

        JsonNode timeSeries = response.get("Time Series FX (Daily)");
        if (timeSeries == null) {
            log.warn("No FX data available");
            return null;
        }

        JsonNode dayData = timeSeries.get(date.toString());
        if (dayData == null) {
            return null;
        }

        String closeValue = dayData.get("4. close").asText();
        return new BigDecimal(closeValue);
    }

    /**
     * 전체 환율 데이터 수집 (초기 수집용)
     * outputsize=full을 사용하여 20년치 환율 데이터를 한 번의 API 호출로 가져옵니다.
     */
    public Map<LocalDate, BigDecimal> fetchAllFxRates() {
        log.info("=== 전체 환율 데이터 수집 시작 (outputsize=full) ===");
        rateLimiter.waitIfNeeded();

        JsonNode response = client.get("FX_DAILY",
            Map.of("from_symbol", "USD", "to_symbol", "KRW", "outputsize", "full"));

        JsonNode timeSeries = response.get("Time Series FX (Daily)");
        if (timeSeries == null) {
            log.warn("환율데이터가 없습니다.");
            return Map.of();
        }

        Map<LocalDate, BigDecimal> rates = new HashMap<>();
        timeSeries.fields().forEachRemaining(entry -> {
            try {
                LocalDate date = LocalDate.parse(entry.getKey());
                String closeValue = entry.getValue().get("4. close").asText();
                rates.put(date, new BigDecimal(closeValue));
            } catch (Exception e) {
                log.warn("환율 데이터를 파싱에 실패했습니다. : {}", entry.getKey(), e);
            }
        });

        log.info("=== 전체 환율 데이터 수집 완료: {}개 ===", rates.size());
        return rates;
    }
}
