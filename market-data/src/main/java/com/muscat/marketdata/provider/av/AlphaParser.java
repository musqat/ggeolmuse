package com.muscat.marketdata.provider.av;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.dto.DividendDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Alpha Vantage JSON 응답 파서 TIME_SERIES_DAILY_ADJUSTED(유료) 및 TIME_SERIES_DAILY(무료) 형식 지원
 */
public final class AlphaParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  // Alpha Vantage JSON 키들
  private static final String KEY_TIME_SERIES = "Time Series (Daily)";
  private static final String KEY_META_DATA = "Meta Data";
  private static final String KEY_SYMBOL_IN_META = "2. Symbol";

  // OHLCV 필드 키들
  private static final String KEY_OPEN = "1. open";
  private static final String KEY_HIGH = "2. high";
  private static final String KEY_LOW = "3. low";
  private static final String KEY_CLOSE = "4. close";
  private static final String KEY_ADJUSTED_CLOSE = "5. adjusted close";
  private static final String KEY_VOLUME = "6. volume";
  private static final String KEY_DIVIDEND = "7. dividend amount";
  private static final String KEY_SPLIT = "8. split coefficient";

  private AlphaParser() {
  }

  /**
   * 일봉 데이터 파싱 (adjustedClose 없으면 close로 폴백)
   */
  public static List<CandleDto> parseDailyAdjusted(String rawJson, LocalDate fromDate,
      LocalDate toDate) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(rawJson);

      validateResponse(root);
      String symbol = extractSymbolFromMeta(root);
      JsonNode timeSeries = getTimeSeries(root);

      return parseTimeSeriesData(timeSeries, symbol, fromDate, toDate);

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Alpha Vantage 일봉 파싱 실패", e);
    }
  }

  /**
   * 배당 데이터 파싱 (dividend amount > 0인 날짜들)
   */
  public static List<DividendDto> parseDividends(String rawJson, String symbol, LocalDate fromDate,
      LocalDate toDate) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(rawJson);

      validateResponse(root);
      JsonNode timeSeries = getTimeSeries(root);

      return extractDividendEvents(timeSeries, symbol, fromDate, toDate);

    } catch (Exception e) {
      throw new IllegalStateException("Alpha Vantage 배당 파싱 실패", e);
    }
  }

  // ===== 내부 메서드 =====

  private static void validateResponse(JsonNode root) {
    if (root.hasNonNull("Note")) {
      throw new IllegalStateException("Alpha Vantage 제한: " + root.get("Note").asText());
    }
    if (root.hasNonNull("Information")) {
      throw new IllegalStateException("Alpha Vantage 정보: " + root.get("Information").asText());
    }
    if (root.hasNonNull("Error Message")) {
      throw new IllegalStateException("Alpha Vantage 오류: " + root.get("Error Message").asText());
    }
  }

  private static String extractSymbolFromMeta(JsonNode root) {
    JsonNode metaData = root.get(KEY_META_DATA);
    if (metaData != null && metaData.has(KEY_SYMBOL_IN_META)) {
      String symbol = metaData.get(KEY_SYMBOL_IN_META).asText();
      return symbol != null ? symbol.trim() : null;
    }
    return null;
  }

  private static JsonNode getTimeSeries(JsonNode root) {
    JsonNode timeSeries = root.get(KEY_TIME_SERIES);
    if (timeSeries == null || timeSeries.isNull() || !timeSeries.fields().hasNext()) {
      throw new IllegalStateException("Alpha Vantage 응답에 시계열 데이터가 없습니다");
    }
    return timeSeries;
  }

  private static List<CandleDto> parseTimeSeriesData(JsonNode timeSeries, String symbol,
      LocalDate fromDate, LocalDate toDate) {
    List<CandleDto> results = new ArrayList<>();

    Iterator<Map.Entry<String, JsonNode>> entries = timeSeries.fields();
    while (entries.hasNext()) {
      Map.Entry<String, JsonNode> entry = entries.next();

      LocalDate date = parseDate(entry.getKey());
      if (date == null || !isDateInRange(date, fromDate, toDate)) {
        continue;
      }

      JsonNode dayData = entry.getValue();
      if (dayData == null || !dayData.isObject()) {
        continue;
      }

      CandleDto dto = createDailyAdjustedDto(dayData, symbol, date);
      if (dto != null) {
        results.add(dto);
      }
    }

    // 날짜 오름차순 정렬
    results.sort(Comparator.comparing(CandleDto::getDate));
    return results;
  }

  private static List<DividendDto> extractDividendEvents(JsonNode timeSeries, String symbol,
      LocalDate fromDate, LocalDate toDate) {
    List<DividendDto> dividends = new ArrayList<>();

    Iterator<Map.Entry<String, JsonNode>> entries = timeSeries.fields();
    while (entries.hasNext()) {
      Map.Entry<String, JsonNode> entry = entries.next();

      LocalDate date = parseDate(entry.getKey());
      if (date == null || !isDateInRange(date, fromDate, toDate)) {
        continue;
      }

      JsonNode dayData = entry.getValue();
      BigDecimal dividendAmount = getDecimalValue(dayData, KEY_DIVIDEND);

      if (dividendAmount != null && dividendAmount.compareTo(BigDecimal.ZERO) > 0) {
        DividendDto dividend = DividendDto.builder()
            .symbol(symbol)
            .exDate(date)
            .amount(dividendAmount)
            .currency("USD")
            .source("AlphaVantage")
            .build();
        dividends.add(dividend);
      }
    }

    return dividends;
  }

  private static CandleDto createDailyAdjustedDto(JsonNode dayData, String symbol,
      LocalDate date) {
    BigDecimal open = getDecimalValue(dayData, KEY_OPEN);
    BigDecimal high = getDecimalValue(dayData, KEY_HIGH);
    BigDecimal low = getDecimalValue(dayData, KEY_LOW);
    BigDecimal close = getDecimalValue(dayData, KEY_CLOSE);
    BigDecimal adjustedClose = getDecimalValue(dayData, KEY_ADJUSTED_CLOSE);
    Long volume = getLongValue(dayData, KEY_VOLUME);
    BigDecimal splitCoefficient = getDecimalValue(dayData, KEY_SPLIT);

    // adjustedClose가 없으면 close로 폴백
    if (adjustedClose == null) {
      adjustedClose = close;
    }

    return CandleDto.builder()
        .symbol(symbol)
        .date(date)
        .open(open)
        .high(high)
        .low(low)
        .close(close)
        .adjustedClose(adjustedClose)
        .volume(volume)
        .adjustFactor(splitCoefficient)
        .currency("USD")
        .build();
  }

  private static LocalDate parseDate(String dateString) {
    try {
      return LocalDate.parse(dateString, DATE_FORMATTER);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean isDateInRange(LocalDate date, LocalDate fromDate, LocalDate toDate) {
    if (fromDate != null && date.isBefore(fromDate)) {
      return false;
    }
    if (toDate != null && date.isAfter(toDate)) {
      return false;
    }
    return true;
  }

  private static BigDecimal getDecimalValue(JsonNode node, String fieldName) {
    if (!node.hasNonNull(fieldName)) {
      return null;
    }

    String value = node.get(fieldName).asText();
    if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
      return null;
    }

    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long getLongValue(JsonNode node, String fieldName) {
    if (!node.hasNonNull(fieldName)) {
      return null;
    }

    String value = node.get(fieldName).asText();
    if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
      return null;
    }

    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}