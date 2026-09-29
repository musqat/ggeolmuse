package com.muscat.marketdata.datasource.yf.provider;

import com.muscat.marketdata.datasource.common.BundledSymbolSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerClient;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerParser;
import com.muscat.marketdata.datasource.yf.client.YahooFinanceClient;
import com.muscat.marketdata.datasource.yf.client.YahooParser;
import com.muscat.marketdata.domain.entity.Asset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Yahoo Finance 기반 종목 정보 제공자
 * <p>
 * NASDAQ API를 우선 시도하고, 실패 시 CSV 파일로 fallback합니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
  name = "marketdata.provider",
  havingValue = "yahoo"
)
@RequiredArgsConstructor
public class YfSymbolSource implements MarketDataProvider.SymbolSource,
  MarketDataProvider.AssetInfoSource {

  private final BundledSymbolSource bundledSource;
  private final NasdaqScreenerClient nasdaqClient;
  private final NasdaqScreenerParser nasdaqParser;
  private final YahooFinanceClient yahooClient;
  private final YahooParser yahooParser;

  @Value("${marketdata.symbol-loader.market-cap-filter:mega,large}")
  private String marketCapFilter;

  @Override
  public List<Asset> fetchSymbols() {
    List<Asset> allAssets = new ArrayList<>();

    // 1. NASDAQ API 시도 (우선)
    try {
      allAssets = loadFromNasdaqApi();
      if (!allAssets.isEmpty()) {
        log.info("NASDAQ API에서 종목 로드 성공: {}개", allAssets.size());
        return allAssets;
      }
    } catch (Exception e) {
      log.warn("NASDAQ API 로드 실패, CSV로 fallback: {}", e.getMessage());
    }

    // 2. CSV fallback
    try {
      allAssets = bundledSource.fetch();
      if (!allAssets.isEmpty()) {
        log.info("CSV 파일에서 종목 로드 성공: {}개", allAssets.size());
        return allAssets;
      }
    } catch (Exception e) {
      log.error("CSV 파일 로드 실패", e);
    }

    log.warn("종목 로드 실패: NASDAQ API와 CSV 모두 실패");
    return List.of();
  }

  /**
   * Yahoo Finance Chart API로 단일 종목 정보 조회 (미리보기용)
   * Quote API(/v7)는 401을 반환하므로 동작하는 Chart API(/v8) meta를 사용한다.
   */
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
  }

  /**
   * NASDAQ API에서 종목 로드
   */
  private List<Asset> loadFromNasdaqApi() {
    List<Asset> allAssets = new ArrayList<>();

    // NYSE 종목
    log.debug("NASDAQ API에서 NYSE 종목 조회 시작...");
    String nyseJson = nasdaqClient.getAllStocks("nyse", marketCapFilter);
    List<Asset> nyseStocks = nasdaqParser.parseStocks(nyseJson);
    allAssets.addAll(nyseStocks);
    log.debug("NYSE 종목 로드 완료: {}개", nyseStocks.size());

    // Rate limit 방지
    sleep(1000);

    // NASDAQ 종목
    log.debug("NASDAQ API에서 NASDAQ 종목 조회 시작...");
    String nasdaqJson = nasdaqClient.getAllStocks("nasdaq", marketCapFilter);
    List<Asset> nasdaqStocks = nasdaqParser.parseStocks(nasdaqJson);
    allAssets.addAll(nasdaqStocks);
    log.debug("NASDAQ 종목 로드 완료: {}개", nasdaqStocks.size());

    return allAssets;
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Sleep interrupted", e);
    }
  }
}
