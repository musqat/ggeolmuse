package com.muscat.marketdata.datasource.yf.provider;

import com.muscat.marketdata.datasource.common.MarketDataProvider;
import com.muscat.marketdata.datasource.yf.client.YahooFinanceClient;
import com.muscat.marketdata.datasource.yf.client.YahooParser;
import com.muscat.marketdata.domain.entity.Asset;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Yahoo Finance 기반 종목 정보 제공자(최근 차트 meta 로 읽음)
@Slf4j
@Component
@ConditionalOnProperty(
  name = "marketdata.provider",
  havingValue = "yahoo"
)
@RequiredArgsConstructor
public class YfSymbolSource implements MarketDataProvider.AssetInfoSource {

  private final YahooFinanceClient yahooClient;
  private final YahooParser yahooParser;

  @Override
  public Asset getAsset(String symbol) {
    try {
      String upper = symbol.toUpperCase();
      // 최근 5일 범위로 chart 조회 (meta만 필요)
      LocalDate to = LocalDate.now();
      LocalDate from = to.minusDays(5);
      String raw = yahooClient.getDailyChartRaw(upper, from, to);
      if (raw == null || raw.isBlank()) {
        log.warn("Yahoo 종목 정보 빈 응답: symbol={}", symbol);
        return null;
      }
      return yahooParser.parseAssetInfoFromChart(raw, upper);
    } catch (Exception e) {
      log.error("Yahoo 종목 정보 조회 실패: symbol={}", symbol, e);
      return null;
    }
  }}
