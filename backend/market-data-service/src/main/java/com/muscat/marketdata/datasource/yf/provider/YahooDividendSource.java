package com.muscat.marketdata.datasource.yf.provider;

import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.datasource.yf.client.YahooFinanceClient;
import com.muscat.marketdata.datasource.yf.client.YahooParser;
import com.muscat.marketdata.domain.dto.DividendDto;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Yahoo Finance 기반 배당 데이터 제공자 (기본 Provider)
 * <p>
 * 무료로 배당 데이터를 제공합니다. AlphaVantage가 비활성화되면 자동으로 활성화됩니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
  name = "marketdata.provider",
  havingValue = "yahoo"
)
@RequiredArgsConstructor
public class YahooDividendSource implements DividendSource {

  private final YahooFinanceClient yahooClient;
  private final YahooParser yahooParser;

  @Override
  public List<DividendDto> fetchDividends(String symbol, LocalDate fromDate, LocalDate toDate) {
    log.debug("Yahoo 배당 데이터 수집 시작: symbol={}, period=[{}~{}]", symbol, fromDate, toDate);

    try {
      String rawChartData = yahooClient.getDailyChartRaw(symbol, fromDate, toDate);
      List<DividendDto> dividends = yahooParser.parseDividends(rawChartData, symbol, fromDate,
        toDate);

      log.debug("Yahoo 배당 데이터 수집 완료: symbol={}, 배당건수={}", symbol, dividends.size());
      return dividends;

    } catch (Exception e) {
      log.warn("Yahoo 배당 데이터 수집 실패: symbol={}, error={}", symbol, e.getMessage());
      return List.of();
    }
  }
}
