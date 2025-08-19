package com.muscat.marketdata.provider.yf;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.provider.MarketDataProvider.DividendSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Yahoo Finance 배당 데이터 수집 구현체 (개발 환경용)
 * Chart API의 events.dividends 데이터 활용
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class YahooDividendSource implements DividendSource {

  private final YahooFinanceClient yahooClient;
  private final YahooParser yahooParser;

  /**
   * 지정된 기간의 배당 데이터 수집
   *
   * @param symbol 심볼 (예: AAPL)
   * @param fromDate 시작일 (포함)
   * @param toDate 종료일 (포함)
   * @return 배당 이벤트 목록
   */
  @Override
  public List<DividendDto> fetchDividends(String symbol, LocalDate fromDate, LocalDate toDate) {
    log.debug("Yahoo 배당 데이터 수집 시작: symbol={}, period=[{}~{}]", symbol, fromDate, toDate);

    try {
      String rawChartData = yahooClient.getDailyChartRaw(symbol, fromDate, toDate);

      List<DividendDto> dividends = yahooParser.parseDividends(rawChartData, symbol, fromDate, toDate);

      log.debug("Yahoo 배당 데이터 수집 완료: symbol={}, 배당건수={}", symbol, dividends.size());
      return dividends;

    } catch (Exception e) {
      log.warn("Yahoo 배당 데이터 수집 실패: symbol={}, error={}", symbol, e.getMessage());
      throw new RuntimeException("Yahoo 배당 데이터 수집 실패: " + symbol, e);
    }
  }
}