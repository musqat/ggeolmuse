package com.muscat.marketdata.datasource.alphavantage.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.datasource.common.MarketDataProvider;
import com.muscat.marketdata.domain.dto.DividendDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AlphaVantage 배당 데이터 제공자
 *
 * TIME_SERIES_DAILY_ADJUSTED API를 사용하여 배당 데이터를 수집
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class DividendSource implements MarketDataProvider.DividendSource {

    private final AlphaVantageClient client;
    private final AlphaVantageRateLimiter rateLimiter;

    @Override
    public List<DividendDto> fetchDividends(String symbol, LocalDate fromDate, LocalDate toDate) {
        log.debug("[AV-배당] 배당 데이터 수집 시작: symbol={}, period=[{}~{}]", symbol, fromDate, toDate);

        rateLimiter.waitIfNeeded();

        try {
            JsonNode response = client.get("TIME_SERIES_DAILY_ADJUSTED",
                Map.of("symbol", symbol, "outputsize", "full"));

            JsonNode timeSeries = response.get("Time Series (Daily)");
            if (timeSeries == null) {
                log.warn("[AV-배당] 데이터 없음: symbol={}", symbol);
                return List.of();
            }

            List<DividendDto> dividends = new ArrayList<>();
            timeSeries.fields().forEachRemaining(entry -> {
                LocalDate date = LocalDate.parse(entry.getKey());
                if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                    DividendDto dividend = parseDividend(symbol, date, entry.getValue());
                    if (dividend != null) {
                        dividends.add(dividend);
                    }
                }
            });

            log.debug("[AV-배당] 수집 완료: symbol={}, 배당건수={}", symbol, dividends.size());
            return dividends;

        } catch (Exception e) {
            log.warn("[AV-배당] 수집 실패: symbol={}, error={}", symbol, e.getMessage());
            return List.of();
        }
    }

    private DividendDto parseDividend(String symbol, LocalDate date, JsonNode data) {
        try {
            // "7. dividend amount" 필드 확인
            if (!data.has("7. dividend amount")) {
                return null;
            }

            BigDecimal dividendAmount = new BigDecimal(data.get("7. dividend amount").asText());

            // 배당금이 0보다 클 때만 반환
            if (dividendAmount.compareTo(BigDecimal.ZERO) > 0) {
                return DividendDto.builder()
                    .symbol(symbol)
                    .exDate(date)
                    .amount(dividendAmount)
                    .currency("USD")
                    .build();
            }

            return null;
        } catch (Exception e) {
            log.debug("[AV-배당] 파싱 실패: symbol={}, date={}, error={}", symbol, date, e.getMessage());
            return null;
        }
    }
}
