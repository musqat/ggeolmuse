package com.muscat.marketdata.datasource.yf.provider;

import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.yf.client.YahooFinanceClient;
import com.muscat.marketdata.datasource.yf.client.YahooParser;
import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Yahoo Finance 기반 일봉 데이터 제공자 (기본 Provider)
 *  OHLCV, Adjusted Close, 배당 정보를 제공합니다.
 * AlphaVantage가 비활성화되면 자동으로 활성화됩니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
  name = "marketdata.provider",
  havingValue = "yahoo"
)
@RequiredArgsConstructor
public class YahooCandleSource implements CandleSource {

  private final YahooFinanceClient yahooClient;
  private final YahooParser yahooParser;

  @Override
  public List<Candle> fetchDailyAdjusted(String symbol, LocalDate fromDate, LocalDate toDate) {
    log.debug("Yahoo 일봉 데이터 수집 시작: symbol={}, period=[{}~{}]", symbol, fromDate, toDate);

    try {
      String rawChartData = yahooClient.getDailyChartRaw(symbol, fromDate, toDate);
      List<CandleDto> dailyDtos = yahooParser.parseDailyAdjusted(rawChartData, symbol, fromDate,
        toDate);

      if (dailyDtos.isEmpty()) {
        log.info("Yahoo 일봉 데이터 없음: symbol={}, period=[{}~{}]", symbol, fromDate, toDate);
        return List.of();
      }

      List<Candle> candles = MarketDataMapper.toCandles(dailyDtos, symbol);
      ensureAdjustedCloseNotNull(candles);

      log.info("Yahoo 일봉 데이터 수집 완료: symbol={}, 파싱건수={}, 변환건수={}, period=[{}~{}]",
        symbol, dailyDtos.size(), candles.size(), fromDate, toDate);

      return candles;

    } catch (Exception e) {
      log.warn("Yahoo 일봉 데이터 수집 실패: symbol={}, period=[{}~{}], error={}",
        symbol, fromDate, toDate, e.getMessage());
      return List.of(); // 빈 리스트 반환
    }
  }

  private void ensureAdjustedCloseNotNull(List<Candle> candles) {
    for (Candle candle : candles) {
      if (candle.getAdjustedClose() == null && candle.getClose() != null) {
        candle.setAdjustedClose(candle.getClose());
        log.trace("adjustedClose null 대체: symbol={}, date={}, close={}",
          candle.getSymbol(), candle.getDate(), candle.getClose());
      }
    }
  }
}
