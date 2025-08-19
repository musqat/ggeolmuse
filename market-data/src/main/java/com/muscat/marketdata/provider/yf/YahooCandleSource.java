package com.muscat.marketdata.provider.yf;

import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import com.muscat.marketdata.provider.MarketDataProvider.CandleSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Yahoo Finance 일봉 데이터 수집 구현체 (개발 환경용)
 * Chart API를 통한 OHLCV + adjustedClose 데이터 수집
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class YahooCandleSource implements CandleSource {

  private static final String DATA_SOURCE = "Yahoo";

  private final YahooFinanceClient yahooClient;
  private final YahooParser yahooParser;

  /**
   * 지정된 기간의 일봉 데이터 수집
   *
   * @param symbol 심볼 (예: AAPL)
   * @param fromDate 시작일 (포함)
   * @param toDate 종료일 (포함)
   * @return 일봉 캔들 엔티티 목록
   */
  @Override
  public List<Candle> fetchDailyAdjusted(String symbol, LocalDate fromDate, LocalDate toDate) {
    log.debug("Yahoo 일봉 데이터 수집 시작: symbol={}, period=[{}~{}]", symbol, fromDate, toDate);

    try {
      String rawChartData = yahooClient.getDailyChartRaw(symbol, fromDate, toDate);
      List<CandleDto> dailyDtos = yahooParser.parseDailyAdjusted(rawChartData, symbol, fromDate, toDate);

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
      throw new RuntimeException("Yahoo 일봉 데이터 수집 실패: " + symbol, e);
    }
  }

  /**
   * adjustedClose가 null인 경우 close 값으로 대체
   * 엔티티의 NOT NULL 제약조건 충족을 위함
   */
  private void ensureAdjustedCloseNotNull(List<Candle> candles) {
    for (Candle candle : candles) {
      if (candle.getAdjustedClose() == null && candle.getClose() != null) {
        candle.setAdjustedClose(candle.getClose());
        log.trace("adjustedClose null 대체: symbol={}, date={}, close={}",
            candle.getId().getSymbol(), candle.getId().getDate(), candle.getClose());
      }
    }
  }
}