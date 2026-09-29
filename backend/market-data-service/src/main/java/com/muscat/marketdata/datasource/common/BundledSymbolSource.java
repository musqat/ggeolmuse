package com.muscat.marketdata.datasource.common;

import com.muscat.marketdata.domain.entity.Asset;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 이미지에 넣어 둔 CSV 로 종목 목록을 만든다. 외부 목록 API 가 막혔을 때 쓴다.
 *
 * 형식: Symbol,Name,Last Sale,Net Change,% Change,Market Cap,Country,IPO Year,Volume,Sector,Industry
 */
@Slf4j
@Component
public class BundledSymbolSource {

  private static final List<String> FILES =
      List.of("symbols/nyse_stocks.csv", "symbols/nasdaq_stocks.csv");

  private static final int MIN_COLUMNS = 11;

  public List<Asset> fetch() {
    List<Asset> assets = new ArrayList<>();
    for (String file : FILES) {
      List<Asset> loaded = load(file);
      assets.addAll(loaded);
      log.debug("[번들목록] {} 에서 {}개", file, loaded.size());
    }
    return assets;
  }

  List<Asset> load(String filePath) {
    List<Asset> assets = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        new ClassPathResource(filePath).getInputStream(), StandardCharsets.UTF_8))) {

      String line;
      boolean header = true;

      while ((line = reader.readLine()) != null) {
        if (header) {
          header = false;
          continue;
        }

        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }

        Asset asset = parseLine(line);
        if (asset != null) {
          assets.add(asset);
        }
      }

    } catch (Exception e) {
      log.error("[번들목록] 파일을 읽지 못했다: {}", filePath, e);
    }

    return assets;
  }

  private Asset parseLine(String line) {
    String[] parts = line.split(",");
    if (parts.length < MIN_COLUMNS) {
      log.debug("[번들목록] 칸이 모자란 줄: {}", line);
      return null;
    }

    try {
      String symbol = parts[0].trim();
      String name = parts[1].trim();
      String country = parts[6].trim();

      if (country.isEmpty() || "United States".equals(country)) {
        country = "US";
      }

      return Asset.builder()
          .symbol(symbol)
          .name(name)
          .country(country)
          .currency("USD")
          .assetType("EQUITY")
          .marketCap(parseMarketCap(parts[5].trim(), symbol))
          .build();

    } catch (Exception e) {
      log.debug("[번들목록] 줄을 읽지 못했다: line={}, error={}", line, e.getMessage());
      return null;
    }
  }

  private Long parseMarketCap(String value, String symbol) {
    if (value.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(value.split("\\.")[0]);
    } catch (NumberFormatException e) {
      log.trace("[번들목록] 시가총액을 읽지 못했다: symbol={}, value={}", symbol, value);
      return null;
    }
  }
}
