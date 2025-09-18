package com.muscat.marketdata.provider.alphavantage;

import com.fasterxml.jackson.databind.JsonNode;
import com.muscat.marketdata.common.exceptions.AlphaVantageException;
import com.muscat.marketdata.common.logging.MarketDataLogger;
import com.muscat.marketdata.domain.dto.CandleDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AlphaVantageCandleSource {

  private final AlphaVantageClient client;
  private final MarketDataLogger marketDataLogger;

  public List<CandleDto> fetchDailyAdjusted(String symbol, LocalDate from, LocalDate to) {
    try {
      log.info("AlphaVantage 캔들 데이터 요청: symbol={}, from={}, to={}", symbol, from, to);

      Map<String, String> params = Map.of(
          "symbol", symbol.toUpperCase(),
          "outputsize", "full"  // full = 20+ years, compact = 100 days
      );

      JsonNode response = client.get("TIME_SERIES_DAILY", params);

      if (!response.has("Time Series (Daily)")) {
        String errorMsg = "일일 시계열 데이터를 찾을 수 없습니다";
        log.warn("{}: symbol={}", errorMsg, symbol);
        marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "CANDLE", 0, false, errorMsg);
        throw new AlphaVantageException(symbol + " 캔들 데이터를 찾을 수 없습니다");
      }

      JsonNode timeSeriesData = response.get("Time Series (Daily)");
      List<CandleDto> candles = new ArrayList<>();
      int parseErrorCount = 0;

      Iterator<Map.Entry<String, JsonNode>> fields = timeSeriesData.properties().iterator();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        try {
          String dateStr = entry.getKey();
          JsonNode dailyData = entry.getValue();

          LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);

          // 날짜 범위 체크
          if (date.isBefore(from) || date.isAfter(to)) {
            continue;
          }

          CandleDto candle = parseDailyData(symbol, date, dailyData);
          if (candle != null) {
            candles.add(candle);
          }
        } catch (Exception e) {
          parseErrorCount++;
          log.debug("캔들 데이터 파싱 실패: symbol={}, entry={}", symbol, entry.getKey(), e);
        }
      }

      if (parseErrorCount > candles.size()) {
        log.warn("캔들 데이터 파싱 오류 다수 발생: symbol={}, errors={}, success={}",
            symbol, parseErrorCount, candles.size());
      }

      candles.sort(Comparator.comparing(CandleDto::getDate));

      if (candles.isEmpty()) {
        String errorMsg = "조회된 캔들 데이터가 없습니다";
        log.warn("{}: symbol={}, from={}, to={}", errorMsg, symbol, from, to);
        marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "CANDLE", 0, false, errorMsg);
        throw new AlphaVantageException(symbol + " 캔들 데이터를 찾을 수 없습니다");
      }

      marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "CANDLE", candles.size(), true,
          null);
      log.info("AlphaVantage 캔들 데이터 조회 성공: symbol={}, count={}", symbol, candles.size());
      return candles;

    } catch (AlphaVantageException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg = "캔들 데이터 조회 중 예상치 못한 오류";
      log.error("{}: symbol={}, error={}", errorMsg, symbol, e.getMessage(), e);
      marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "CANDLE", 0, false,
          e.getMessage());
      throw new AlphaVantageException(errorMsg + ": " + symbol, e);
    }
  }

  public Optional<CandleDto> fetchLatestQuote(String symbol) {
    try {
      log.debug("AlphaVantage 실시간 주가 요청: symbol={}", symbol);

      Map<String, String> params = Map.of("symbol", symbol.toUpperCase());
      JsonNode response = client.get("GLOBAL_QUOTE", params);

      if (!response.has("Global Quote")) {
        String errorMsg = "글로벌 quote 데이터를 찾을 수 없습니다";
        log.warn("{}: symbol={}", errorMsg, symbol);
        marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "QUOTE", 0, false, errorMsg);
        return Optional.empty();
      }

      JsonNode quoteData = response.get("Global Quote");
      CandleDto candle = parseQuoteData(symbol, quoteData);

      if (candle == null) {
        String errorMsg = "Quote 데이터 파싱 실패";
        log.warn("{}: symbol={}", errorMsg, symbol);
        marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "QUOTE", 0, false, errorMsg);
        return Optional.empty();
      }

      marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "QUOTE", 1, true, null);
      log.debug("AlphaVantage 실시간 주가 조회 성공: symbol={}, price={}", symbol, candle.getClose());
      return Optional.of(candle);

    } catch (Exception e) {
      String errorMsg = "실시간 주가 조회 실패";
      log.error("{}: symbol={}, error={}", errorMsg, symbol, e.getMessage(), e);
      marketDataLogger.logDataCollection("ALPHAVANTAGE", symbol, "QUOTE", 0, false, e.getMessage());
      return Optional.empty();
    }
  }

  private CandleDto parseDailyData(String symbol, LocalDate date, JsonNode dailyData) {
    try {
      BigDecimal open = getBigDecimalValue(dailyData, "1. open");
      BigDecimal high = getBigDecimalValue(dailyData, "2. high");
      BigDecimal low = getBigDecimalValue(dailyData, "3. low");
      BigDecimal close = getBigDecimalValue(dailyData, "4. close");
      Long volume = getLongValue(dailyData, "5. volume");

      if (close == null || close.compareTo(BigDecimal.ZERO) <= 0) {
        log.debug("유효하지 않은 종가 데이터: symbol={}, date={}, close={}", symbol, date, close);
        return null;
      }

      return CandleDto.builder()
          .symbol(symbol)
          .date(date)
          .open(open != null ? open : close)
          .high(high != null ? high : close)
          .low(low != null ? low : close)
          .close(close)
          .adjustedClose(close) // AlphaVantage TIME_SERIES_DAILY는 조정가격 미포함, 별도 API 필요
          .volume(volume != null ? volume : 0L)
          .currency("USD")
          .build();

    } catch (Exception e) {
      log.debug("일일 데이터 파싱 실패: symbol={}, date={}", symbol, date, e);
      return null;
    }
  }

  private CandleDto parseQuoteData(String symbol, JsonNode quoteData) {
    try {
      String priceStr = getStringValue(quoteData, "05. price");
      String openStr = getStringValue(quoteData, "02. open");
      String highStr = getStringValue(quoteData, "03. high");
      String lowStr = getStringValue(quoteData, "04. low");
      String volumeStr = getStringValue(quoteData, "06. volume");
      String dateStr = getStringValue(quoteData, "07. latest trading day");

      if (priceStr == null || dateStr == null) {
        return null;
      }

      BigDecimal price = new BigDecimal(priceStr);
      if (price.compareTo(BigDecimal.ZERO) <= 0) {
        return null;
      }

      LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
      BigDecimal open = openStr != null ? new BigDecimal(openStr) : price;
      BigDecimal high = highStr != null ? new BigDecimal(highStr) : price;
      BigDecimal low = lowStr != null ? new BigDecimal(lowStr) : price;
      Long volume = volumeStr != null ? Long.parseLong(volumeStr) : 0L;

      return CandleDto.builder()
          .symbol(symbol)
          .date(date)
          .open(open)
          .high(high)
          .low(low)
          .close(price)
          .adjustedClose(price)
          .volume(volume)
          .currency("USD")
          .build();

    } catch (Exception e) {
      log.debug("Quote 데이터 파싱 실패: symbol={}", symbol, e);
      return null;
    }
  }

  private String getStringValue(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    return (field != null && !field.isNull()) ? field.asText() : null;
  }

  private BigDecimal getBigDecimalValue(JsonNode node, String fieldName) {
    String value = getStringValue(node, fieldName);
    if (value == null) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Long getLongValue(JsonNode node, String fieldName) {
    String value = getStringValue(node, fieldName);
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}