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

// 상장 종목 목록을 AlphaVantage LISTING_STATUS 로 받는 소스(공급원이 yahoo 여도 쓰도록 alphavantage 패키지 밖)
@Slf4j
@Component
@RequiredArgsConstructor
public class ListingStatusSource {

  private static final Set<String> MAJOR_EXCHANGES =
      Set.of("NYSE", "NASDAQ", "NYSE ARCA", "NYSE MKT", "AMEX", "BATS");

  private final AlphaVantageClient client;

  @Value("${marketdata.symbol-listing.only-major-exchanges:true}")
  private boolean onlyMajorExchanges;

  // 0 = 무제한(로컬에서 전체 적재가 무거울 때만 줄임)
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

    // 레이트리밋 안내문처럼 CSV 가 아닌 본문도 0개로 끝남(그때는 본문으로 구분)
    if (assets.isEmpty()) {
      log.warn("[종목목록] 파싱된 종목이 없다. 응답 앞부분: {}", head(csv));
    }

    return assets;
  }

  private static String head(String csv) {
    String oneLine = csv.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
  }

  private Asset parseLine(String line) {
    if (line == null || line.isBlank()) {
      return null;
    }

    // 뒤 다섯 칸(exchange · assetType · ipoDate · delistingDate · status)부터 자름(이름에 콤마 가능)
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

    // 상장폐지 종목 제외
    if (!"Active".equalsIgnoreCase(status)) {
      return null;
    }

    // Stock 과 ETF 만(그 밖의 유형은 백테스트 대상 아님)
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
        .sourceName(name.isEmpty() ? symbol : name)
        .country("US")
        .currency("USD")
        .assetType(AssetTypeRule.resolve(isEtf, name))
        .marketCap(null)   // LISTING_STATUS 에 없는 시가총액은 따로 채움
        .build();
  }
}
