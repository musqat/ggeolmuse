package com.muscat.marketdata.provider.stooq;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stooq에서 NASDAQ 100 종목 리스트 스크래핑
 * 올바른 URL: https://stooq.com/t/?i=580 (나스닥100 구성종목)
 */
@Slf4j
@Component
public class StooqNasdaq100JsoupSource {

  private static final String URL_ENGLISH = "https://stooq.com/t/?i=580&l=en";
  private static final String URL_DEFAULT = "https://stooq.com/t/?i=580";

  private static final Pattern US_TICKER_PATTERN = Pattern.compile("([A-Za-z0-9.-]+)\\.us", Pattern.CASE_INSENSITIVE);
  private static final int TIMEOUT_MS = (int) Duration.ofSeconds(15).toMillis();
  private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

  // 나스닥100 주요 종목들 (검증용)
  private static final Set<String> EXPECTED_NASDAQ100 = Set.of(
      "AAPL", "MSFT", "NVDA", "AMZN", "META", "GOOGL", "GOOG", "TSLA",
      "AVGO", "COST", "NFLX", "AMD", "ADBE", "PEP", "QCOM", "TMUS"
  );

  /**
   * 심볼과 종목명 쌍
   */
  public record Row(String symbolPlain, String name) {}

  /**
   * NASDAQ 100 Asset 리스트 반환 (메인 메서드)
   */
  public List<Asset> fetchNasdaq100() {
    Map<String, String> symbolNameMap = fetchSymbolNameMap();

    validateNasdaq100Content(symbolNameMap);

    List<Asset> assets = new ArrayList<>(symbolNameMap.size());

    for (Map.Entry<String, String> entry : symbolNameMap.entrySet()) {
      String symbol = entry.getKey();
      String name = entry.getValue();

      if (name == null || name.isBlank()) {
        name = symbol;
      }

      Asset asset = MarketDataMapper.toAsset(symbol, name);
      assets.add(asset);
    }

    log.info("Stooq NASDAQ 100 종목 수집 완료: {}개", assets.size());
    return assets;
  }

  /**
   * Row 형태로 반환
   */
  public List<Row> top100Rows() {
    Map<String, String> symbolNameMap = fetchSymbolNameMap();
    List<Row> rows = new ArrayList<>(symbolNameMap.size());
    symbolNameMap.forEach((symbol, name) -> rows.add(new Row(symbol, name)));
    return rows;
  }

  /**
   * 심볼만 반환
   */
  public List<String> top100Symbols() {
    return top100Rows().stream()
        .map(Row::symbolPlain)
        .toList();
  }

  // ===== 내부 스크래핑 로직 =====

  private Map<String, String> fetchSymbolNameMap() {
    Document document = getDocumentWithFallback();
    Map<String, String> symbolNameMap = new LinkedHashMap<>(128);

    extractSymbolsFromAnchors(document, symbolNameMap);

    if (symbolNameMap.isEmpty()) {
      log.warn("Stooq 앵커 파싱 실패, 테이블 셀 직접 파싱 시도");
      extractSymbolsFromTableCells(document, symbolNameMap);
    }

    if (symbolNameMap.size() < 50) {
      log.warn("추출된 심볼 수가 적음 ({}개), 테이블 행 파싱 시도", symbolNameMap.size());
      extractSymbolsFromTableRows(document, symbolNameMap);
    }

    validateExtractionResult(document, symbolNameMap);

    log.info("Stooq에서 {}개 심볼 추출 완료", symbolNameMap.size());
    return symbolNameMap;
  }

  private Document getDocumentWithFallback() {
    try {
      Document document = fetchDocument(URL_ENGLISH);
      if (hasValidContent(document)) {
        return document;
      }

      log.warn("Stooq 페이지가 비어있음, 기본 URL로 재시도");
      return fetchDocument(URL_DEFAULT);

    } catch (Exception e) {
      log.warn("Stooq 페이지 접근 실패: {} -> 기본 URL로 재시도", e.getMessage());
      return fetchDocument(URL_DEFAULT);
    }
  }

  private Document fetchDocument(String url) {
    try {
      log.info("Stooq 페이지 요청: {}", url);
      return Jsoup.connect(url)
          .userAgent(USER_AGENT)
          .timeout(TIMEOUT_MS)
          .header("Accept-Charset", StandardCharsets.UTF_8.name())
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          .get();
    } catch (Exception e) {
      throw new IllegalStateException("Stooq 페이지 조회 실패: " + e.getMessage(), e);
    }
  }

  private void extractSymbolsFromAnchors(Document document, Map<String, String> symbolNameMap) {
    Elements links = document.select("a[href]");

    for (Element anchor : links) {
      String href = anchor.attr("href");
      String symbol = extractUsTickerFromText(href);

      if (symbol != null) {
        String name = guessCompanyNameFromTableRow(anchor);
        if (name == null || name.isBlank()) {
          name = anchor.text();
        }
        symbolNameMap.putIfAbsent(symbol, cleanCompanyName(name));
      }
    }
  }

  private void extractSymbolsFromTableCells(Document document, Map<String, String> symbolNameMap) {
    Elements tableCells = document.select("td");

    for (Element cell : tableCells) {
      String symbol = extractUsTickerFromText(cell.text());
      if (symbol != null) {
        String name = cell.nextElementSibling() != null
            ? cell.nextElementSibling().text()
            : null;
        symbolNameMap.putIfAbsent(symbol, cleanCompanyName(name));
      }
    }
  }

  private void extractSymbolsFromTableRows(Document document, Map<String, String> symbolNameMap) {
    Elements tableRows = document.select("tr");

    for (Element row : tableRows) {
      Elements cells = row.select("td");
      if (cells.size() >= 2) {
        String firstCell = cells.get(0).text();
        String secondCell = cells.get(1).text();

        String symbol = extractUsTickerFromText(firstCell);
        if (symbol == null) {
          if (firstCell.matches("[A-Z]{1,5}")) {
            symbol = firstCell.trim().toUpperCase();
          }
        }

        if (symbol != null) {
          symbolNameMap.putIfAbsent(symbol, cleanCompanyName(secondCell));
        }
      }
    }
  }

  private String extractUsTickerFromText(String text) {
    if (text == null) return null;

    Matcher matcher = US_TICKER_PATTERN.matcher(text);
    if (matcher.find()) {
      return matcher.group(1).toUpperCase(Locale.ROOT); // aapl.us -> AAPL
    }
    return null;
  }

  private String guessCompanyNameFromTableRow(Element anchor) {
    Element tableCell = anchor.closest("td");
    if (tableCell != null) {
      Element nextCell = tableCell.nextElementSibling();
      if (nextCell != null) {
        String name = nextCell.text();
        if (name != null && !name.isBlank()) {
          return name;
        }
      }
    }

    Element parent = anchor.parent();
    if (parent != null) {
      String name = parent.ownText();
      if (name != null && !name.isBlank()) {
        return name;
      }
    }

    return null;
  }

  private String cleanCompanyName(String name) {
    if (name == null) return null;

    String cleaned = name.trim();
    cleaned = cleaned.replaceAll("\\s{2,}", " ");

    return cleaned.isEmpty() ? null : cleaned;
  }

  private boolean hasValidContent(Document document) {
    return document != null && document.selectFirst("a[href], table, td") != null;
  }

  private void validateExtractionResult(Document document, Map<String, String> symbolNameMap) {
    if (symbolNameMap.isEmpty()) {
      String title = document.title();
      log.error("Stooq에서 심볼을 추출하지 못함. 페이지 제목: {}", title);
      throw new IllegalStateException("Stooq NASDAQ 100 파싱 실패: 추출된 심볼이 없음");
    }
  }

  private void validateNasdaq100Content(Map<String, String> symbolNameMap) {
    Set<String> extractedSymbols = symbolNameMap.keySet();
    Set<String> foundMajorSymbols = new HashSet<>(EXPECTED_NASDAQ100);
    foundMajorSymbols.retainAll(extractedSymbols);

    log.info("나스닥100 주요 종목 확인: {}/{} 발견", foundMajorSymbols.size(), EXPECTED_NASDAQ100.size());
    log.info("발견된 주요 종목: {}", foundMajorSymbols);

    if (foundMajorSymbols.size() < 10) {
      log.warn("나스닥100 주요 종목이 {}개만 발견됨. URL이나 파싱 로직 확인 필요", foundMajorSymbols.size());
      log.warn("추출된 첫 20개 심볼: {}",
          extractedSymbols.stream().limit(20).sorted().toList());
    }
  }
}