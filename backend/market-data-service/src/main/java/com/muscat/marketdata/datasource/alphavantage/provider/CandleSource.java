package com.muscat.marketdata.datasource.alphavantage.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.datasource.common.MarketDataProvider;
import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AlphaVantage 캔들 데이터 소스
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class CandleSource implements MarketDataProvider.CandleSource {

    private final AlphaVantageClient client;
    private final AlphaVantageRateLimiter rateLimiter;

    @Override
    public List<Candle> fetchDailyAdjusted(String symbol, LocalDate from, LocalDate to) {
        rateLimiter.waitIfNeeded();

        // 기간 기반 outputsize 자동 선택
        // - 100일 이상: full (20년치 전부, 초기 수집용)
        // - 100일 미만: compact (최근 100개, 증분 수집용)
        long days = ChronoUnit.DAYS.between(from, to);
        String outputsize = days >= 100 ? "full" : "compact";
        boolean filterByDate = days < 100;  // compact만 날짜 필터링

        log.debug("[AV-CandleSource] {} - 요청 기간: {}일, outputsize: {}, 필터링: {}",
                  symbol, days, outputsize, filterByDate);

        JsonNode response = client.get("TIME_SERIES_DAILY_ADJUSTED",
            Map.of("symbol", symbol, "outputsize", outputsize));

        JsonNode timeSeries = response.get("Time Series (Daily)");
        if (timeSeries == null) {
            log.warn("No candle data for symbol: {}", symbol);
            return List.of();
        }

        List<CandleDto> dtos = new ArrayList<>();

        // AlphaVantage는 최신 날짜 → 과거 날짜 순서로 반환
        var iterator = timeSeries.fields();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            LocalDate date = LocalDate.parse(entry.getKey());

            if (filterByDate) {
                // compact: 날짜 범위 필터링 (증분 수집)
                if (date.isBefore(from)) {
                    log.debug("[조기 종료] {} - 범위 이전 날짜 도달: {}", symbol, date);
                    break;
                }
                if (!date.isAfter(to)) {
                    CandleDto dto = parseCandle(symbol, date, entry.getValue());
                    if (dto != null) dtos.add(dto);
                }
            } else {
                // full: 필터링 없이 20년치 전부 저장 (초기 수집)
                CandleDto dto = parseCandle(symbol, date, entry.getValue());
                if (dto != null) dtos.add(dto);
            }
        }

        return MarketDataMapper.toCandles(dtos, symbol);
    }

    private CandleDto parseCandle(String symbol, LocalDate date, JsonNode data) {
        try {
            return CandleDto.builder()
                .symbol(symbol)
                .date(date)
                .open(getBigDecimal(data, "1. open"))
                .high(getBigDecimal(data, "2. high"))
                .low(getBigDecimal(data, "3. low"))
                .close(getBigDecimal(data, "4. close"))
                .adjustedClose(getBigDecimal(data, "5. adjusted close"))
                .volume(getLong(data, "6. volume"))
                .dividendAmount(getBigDecimal(data, "7. dividend amount"))
                .splitCoefficient(getBigDecimal(data, "8. split coefficient"))
                .currency("USD")
                .build();
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal getBigDecimal(JsonNode node, String field) {
        return node.has(field) ? new BigDecimal(node.get(field).asText()) : null;
    }

    private Long getLong(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asLong() : 0L;
    }
}
