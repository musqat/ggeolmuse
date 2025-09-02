package com.muscat.marketdata.provider.yf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.dto.DividendDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class YahooParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_CURRENCY = "USD";

    public List<CandleDto> parseDailyAdjusted(String rawJson, String symbolOverride,
                                              LocalDate fromDate, LocalDate toDate) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            JsonNode chartResult = getChartResult(root);

            ChartMetadata metadata = extractMetadata(chartResult, symbolOverride);
            TimeSeriesData timeSeriesData = extractTimeSeriesData(chartResult);

            return buildDailyAdjustedDtos(timeSeriesData, metadata, fromDate, toDate);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Yahoo 일봉 파싱 실패", e);
        }
    }

    public List<DividendDto> parseDividends(String rawJson, String symbol, LocalDate fromDate, LocalDate toDate) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            JsonNode chartResult = getChartResult(root);

            JsonNode dividends = chartResult.path("events").path("dividends");
            if (dividends.isMissingNode() || dividends.isNull()) {
                return List.of();
            }

            return extractDividendEvents(dividends, symbol, fromDate, toDate);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Yahoo 배당 파싱 실패", e);
        }
    }

    private JsonNode getChartResult(JsonNode root) {
        JsonNode chartResult = root.path("chart").path("result").get(0);
        if (chartResult == null || chartResult.isNull()) {
            throw new IllegalStateException("Yahoo Finance 응답에 차트 결과가 없습니다");
        }
        return chartResult;
    }

  private ChartMetadata extractMetadata(JsonNode chartResult, String symbolOverride) {
    JsonNode meta = chartResult.path("meta");
    String symbol = symbolOverride != null ? symbolOverride : getTextValue(meta, "symbol");
    String currency = getTextValue(meta, "currency");

    return new ChartMetadata(symbol, currency != null ? currency : DEFAULT_CURRENCY);
  }

  private TimeSeriesData extractTimeSeriesData(JsonNode chartResult) {
    JsonNode timestamps = chartResult.path("timestamp");
    JsonNode quote = chartResult.path("indicators").path("quote").get(0);
    JsonNode adjClose = chartResult.path("indicators").path("adjclose").get(0);

    if (timestamps == null || quote == null || timestamps.isNull() || quote.isNull()
        || !timestamps.isArray() || !quote.isObject()) {
      throw new IllegalStateException("Yahoo Finance 응답에 필수 데이터가 없습니다");
    }

    return new TimeSeriesData(
        timestamps,
        quote.path("open"),
        quote.path("high"),
        quote.path("low"),
        quote.path("close"),
        quote.path("volume"),
        adjClose != null && !adjClose.isNull() ? adjClose.path("adjclose") : null
    );
  }

  private List<CandleDto> buildDailyAdjustedDtos(TimeSeriesData data, ChartMetadata metadata,
      LocalDate fromDate, LocalDate toDate) {
    List<CandleDto> results = new ArrayList<>();

    for (int i = 0; i < data.timestamps().size(); i++) {
      Long epochSecond = getLongValueAt(data.timestamps(), i);
      if (epochSecond == null) continue;

      LocalDate date = Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC).toLocalDate();
      if (!isDateInRange(date, fromDate, toDate)) continue;

      CandleDto dto = createDailyAdjustedDto(data, metadata, date, i);
      if (dto != null) {
        results.add(dto);
      }
    }

    return results;
  }

  private CandleDto createDailyAdjustedDto(TimeSeriesData data, ChartMetadata metadata,
      LocalDate date, int index) {
    BigDecimal open = getDecimalValueAt(data.open(), index);
    BigDecimal high = getDecimalValueAt(data.high(), index);
    BigDecimal low = getDecimalValueAt(data.low(), index);
    BigDecimal close = getDecimalValueAt(data.close(), index);
    Long volume = getLongValueAt(data.volume(), index);

    BigDecimal adjustedClose = data.adjustedClose() != null
        ? getDecimalValueAt(data.adjustedClose(), index)
        : null;

    // adjustedClose가 없으면 close로 폴백
    if (adjustedClose == null) {
      adjustedClose = close;
    }

    return CandleDto.builder()
        .symbol(metadata.symbol())
        .date(date)
        .open(open)
        .high(high)
        .low(low)
        .close(close)
        .adjustedClose(adjustedClose)
        .volume(volume)
        .adjustFactor(null)
        .currency(metadata.currency())
        .build();
  }

  private List<DividendDto> extractDividendEvents(JsonNode dividends, String symbol,
      LocalDate fromDate, LocalDate toDate) {
    List<DividendDto> results = new ArrayList<>();

    Iterator<String> fieldNames = dividends.fieldNames();
    while (fieldNames.hasNext()) {
      String eventKey = fieldNames.next();
      JsonNode event = dividends.get(eventKey);
      if (event == null || event.isNull()) continue;

      DividendDto dividend = createDividendDto(event, eventKey, symbol, fromDate, toDate);
      if (dividend != null) {
        results.add(dividend);
      }
    }

    return results;
  }

  private DividendDto createDividendDto(JsonNode event, String eventKey, String symbol,
      LocalDate fromDate, LocalDate toDate) {
    Long timestamp = event.hasNonNull("date")
        ? event.get("date").asLong()
        : parseLongSafely(eventKey);

    if (timestamp == null) return null;

    LocalDate exDate = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
    if (!isDateInRange(exDate, fromDate, toDate)) return null;

    BigDecimal amount = event.hasNonNull("amount")
        ? new BigDecimal(event.get("amount").asText())
        : null;

    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return null;

    return DividendDto.builder()
        .symbol(symbol)
        .exDate(exDate)
        .amount(amount)
        .currency(DEFAULT_CURRENCY)
        .source("Yahoo")
        .build();
  }

  // ===== 유틸리티 메서드 =====

  private boolean isDateInRange(LocalDate date, LocalDate fromDate, LocalDate toDate) {
    if (fromDate != null && date.isBefore(fromDate)) return false;
    if (toDate != null && date.isAfter(toDate)) return false;
    return true;
  }

  private String getTextValue(JsonNode node, String fieldName) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    JsonNode fieldNode = node.path(fieldName);
    return fieldNode.isMissingNode() || fieldNode.isNull() ? null : fieldNode.asText();
  }

  private BigDecimal getDecimalValueAt(JsonNode arrayNode, int index) {
    if (arrayNode == null || !arrayNode.isArray() || index >= arrayNode.size()) return null;

    JsonNode valueNode = arrayNode.get(index);
    if (valueNode == null || valueNode.isNull()) return null;

    String textValue = valueNode.asText();
    if (textValue == null || textValue.isBlank() || "null".equalsIgnoreCase(textValue)) {
      return null;
    }

    try {
      return new BigDecimal(textValue);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Long getLongValueAt(JsonNode arrayNode, int index) {
    if (arrayNode == null || !arrayNode.isArray() || index >= arrayNode.size()) return null;

    JsonNode valueNode = arrayNode.get(index);
    if (valueNode == null || valueNode.isNull()) return null;

    try {
      return valueNode.asLong();
    } catch (Exception e) {
      return null;
    }
  }

  private Long parseLongSafely(String text) {
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // ===== 내부 레코드 클래스 =====

  private record ChartMetadata(String symbol, String currency) {}

  private record TimeSeriesData(
      JsonNode timestamps,
      JsonNode open,
      JsonNode high,
      JsonNode low,
      JsonNode close,
      JsonNode volume,
      JsonNode adjustedClose
  ) {}
}