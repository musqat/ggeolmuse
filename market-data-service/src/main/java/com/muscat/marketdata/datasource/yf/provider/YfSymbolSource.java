package com.muscat.marketdata.datasource.yf.provider;

import com.muscat.marketdata.datasource.common.MarketDataProvider;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerClient;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerParser;
import com.muscat.marketdata.domain.entity.Asset;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
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
public class YfSymbolSource implements MarketDataProvider.SymbolSource {

  private final NasdaqScreenerClient nasdaqClient;
  private final NasdaqScreenerParser nasdaqParser;

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
      allAssets = loadFromCsvFiles();
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

  /**
   * CSV 파일에서 종목 로드 (Fallback)
   */
  private List<Asset> loadFromCsvFiles() {
    List<Asset> allAssets = new ArrayList<>();

    // NYSE 종목
    List<Asset> nyseStocks = loadNasdaqCsv("symbols/nyse_stocks.csv");
    allAssets.addAll(nyseStocks);
    log.debug("NYSE 주식 종목 로드 완료: {}개 (CSV)", nyseStocks.size());

    // NASDAQ 종목
    List<Asset> nasdaqStocks = loadNasdaqCsv("symbols/nasdaq_stocks.csv");
    allAssets.addAll(nasdaqStocks);
    log.debug("NASDAQ 주식 종목 로드 완료: {}개 (CSV)", nasdaqStocks.size());

    return allAssets;
  }

  /**
   * NASDAQ CSV 형식 파일 로드 형식: Symbol,Name,Last Sale,Net Change,% Change,Market Cap,Country,IPO
   * Year,Volume,Sector,Industry
   */
  private List<Asset> loadNasdaqCsv(String filePath) {
    List<Asset> assets = new ArrayList<>();

    try {
      ClassPathResource resource = new ClassPathResource(filePath);

      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

        String line;
        boolean isFirstLine = true;

        while ((line = reader.readLine()) != null) {
          // 헤더 라인 스킵
          if (isFirstLine) {
            isFirstLine = false;
            continue;
          }

          // 빈 줄 스킵
          line = line.trim();
          if (line.isEmpty()) {
            continue;
          }

          // CSV 파싱
          String[] parts = line.split(",");
          if (parts.length < 11) {
            log.debug("잘못된 CSV 라인 (컬럼 수 부족): {}", line);
            continue;
          }

          try {
            String symbol = parts[0].trim();
            String name = parts[1].trim();
            String marketCapStr = parts[5].trim();
            String country = parts[6].trim();

            // Market Cap 파싱
            Long marketCap = null;
            if (!marketCapStr.isEmpty()) {
              try {
                marketCap = Long.parseLong(marketCapStr.split("\\.")[0]);
              } catch (NumberFormatException e) {
                log.trace("시가총액 파싱 실패: symbol={}, marketCap={}", symbol, marketCapStr);
              }
            }

            // Country 정규화
            if (country.isEmpty() || "United States".equals(country)) {
              country = "US";
            }

            // Asset 생성
            Asset asset = Asset.builder()
              .symbol(symbol)
              .name(name)
              .country(country)
              .currency("USD")
              .assetType("EQUITY")
              .marketCap(marketCap)
              .build();

            assets.add(asset);
            log.trace("종목 로드: {} - {}", symbol, name);

          } catch (Exception e) {
            log.debug("종목 파싱 실패: line={}, error={}", line, e.getMessage());
          }
        }
      }

    } catch (Exception e) {
      log.error("종목 파일 로드 실패: {}", filePath, e);
    }

    return assets;
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
