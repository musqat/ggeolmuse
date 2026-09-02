package com.muscat.marketdata.datasource.common;

import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.domain.entity.Asset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 상장 종목 목록을 AlphaVantage LISTING_STATUS 로 받아온다.
 *
 * 기존 alphavantage 패키지의 SymbolSource 는 {@code marketdata.provider=alphavantage}
 * 조건이 붙어 있어 yahoo 로 돌 때는 빈이 뜨지 않는다. 그래서 목록만 여기로 뗐다.
 *
 * LISTING_STATUS 는 호출 한 번에 CSV 로 14,000건을 준다. assetType 과 status 를
 * 필드로 주므로 이름으로 추측하거나 상장폐지를 따로 판정할 필요가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListingStatusSource {

  private static final Set<String> MAJOR_EXCHANGES =
      Set.of("NYSE", "NASDAQ", "NYSE ARCA", "NYSE MKT", "AMEX", "BATS");

  private final AlphaVantageClient client;

  @Value("${marketdata.symbol-listing.only-major-exchanges:true}")
  private boolean onlyMajorExchanges;

  // 0 = 무제한. 로컬에서 전체 적재가 무거울 때만 줄인다
  @Value("${marketdata.symbol-listing.max-symbols:0}")
  private int maxSymbols;

  public List<Asset> fetch() {
    String csv = client.getCsv("LISTING_STATUS", Map.of());

    if (csv == null || csv.isBlank()) {
      log.warn("[종목목록] LISTING_STATUS 응답이 비어 있다");
      return List.of();
    }

    List<Asset> assets = new ArrayList<>();
    String[] lines = csv.split("\n");

    // 헤더: symbol,name,exchange,assetType,ipoDate,delistingDate,status
    for (int i = 1; i < lines.length; i++) {
      Asset asset = parseLine(lines[i]);
      if (asset != null) {
        assets.add(asset);
        if (maxSymbols > 0 && assets.size() >= maxSymbols) {
          log.info("[종목목록] 수집 상한 적용: {}개", maxSymbols);
          break;
        }
      }
    }

    log.info("[종목목록] LISTING_STATUS 파싱 완료: {}개", assets.size());
    return assets;
  }

  private Asset parseLine(String line) {
    if (line == null || line.isBlank()) {
      return null;
    }

    // 이름에 콤마가 들어갈 수 있어 뒤에서부터 자른다.
    // 뒤 다섯 칸(exchange, assetType, ipoDate, delistingDate, status)은 콤마가 없다
    String[] parts = line.trim().split(",");
    if (parts.length < 7) {
      return null;
    }

    int n = parts.length;
    String status = parts[n - 1].trim();
    String assetType = parts[n - 4].trim();
    String exchange = parts[n - 5].trim();
    String symbol = parts[0].trim();
    String name = String.join(",", java.util.Arrays.copyOfRange(parts, 1, n - 5)).trim();

    if (symbol.isEmpty()) {
      return null;
    }

    // 상장폐지된 것은 목록에 넣지 않는다
    if (!"Active".equalsIgnoreCase(status)) {
      return null;
    }

    // Stock 과 ETF 만 받는다. 그 밖의 유형은 백테스트 대상이 아니다
    boolean isStock = "Stock".equalsIgnoreCase(assetType);
    boolean isEtf = "ETF".equalsIgnoreCase(assetType);
    if (!isStock && !isEtf) {
      return null;
    }

    if (onlyMajorExchanges && !MAJOR_EXCHANGES.contains(exchange.toUpperCase())) {
      return null;
    }

    return Asset.builder()
        .symbol(symbol)
        .name(name.isEmpty() ? symbol : name)
        .country("US")
        .currency("USD")
        .assetType(isEtf ? "ETF" : "EQUITY")
        .marketCap(null)   // LISTING_STATUS 에는 없다. 시가총액은 따로 채운다
        .build();
  }
}
