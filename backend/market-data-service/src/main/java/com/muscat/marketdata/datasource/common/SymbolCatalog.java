package com.muscat.marketdata.datasource.common;


import com.muscat.marketdata.domain.entity.Asset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 상장 종목 목록의 유일한 출처 AlphaVantage LISTING_STATUS(시세 공급원과 상관없이 목록은 여기서만)
@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolCatalog {

  // 티커에 쓰는 글자(CAPTW(EXP20260807) 처럼 만기 표기가 붙은 줄은 야후에서도 404)
  private static final Pattern TICKER = Pattern.compile("[A-Za-z0-9.^/-]+");

  // 거래소 시험 종목(이름이 빈 줄이 많아 기호로 거름, ETF 인 TEST 는 앞 글자가 없어 안 걸림)
  private static final Pattern TEST_ISSUE_SYMBOL = Pattern.compile("[A-Z]TEST(-[A-Z])?|ZTST|Z[A-Z]ZZT");

  // 기호가 평범한 시험 종목(CBO · CBX · IGZ · ZVV · ZBZX)은 이름으로 거름
  private static final String TEST_ISSUE_NAMES = "listed test,test stock";

  private final ListingStatusSource listingSource;
  private final BundledSymbolSource bundledSource;

  // 목록 API 가 비어 있을 때 이미지에 든 CSV 로 대체할지(로컬 · E2E 에서만 켬)
  @Value("${marketdata.symbol-listing.csv-fallback:false}")
  private boolean csvFallback;

  // 보통주가 아닌 종목(워런트 · 유닛 · 우선주 · 채권)을 거를 이름 패턴(티커 접미사 W · WS 는 LOW · GLW 도 잡아 안 씀)
  // " units" 앞 공백은 United 제외용(지우면 United 도 걸림)
  public static final String DEFAULT_EXCLUDE_NAME_PATTERNS = "warrant, units,rights,preferred,depositary,notes due";

  @Value("${marketdata.symbol-loader.exclude-name-patterns:" + DEFAULT_EXCLUDE_NAME_PATTERNS + "}")
  private String excludeNamePatterns;

  // asset.symbol 칸 길이(넘는 심볼은 INSERT 실패)
  @Value("${marketdata.constants.database.symbol-max-length:16}")
  private int symbolMaxLength;

  // 중복 · 저장 못 할 심볼 · 시험 종목 · 보통주 아닌 것을 거름
  // 실패하면 빈 목록(일부만 받으면 나머지가 상장폐지처럼 보임)
  public List<Asset> fetchAll() {
    List<Asset> fetched;

    try {
      fetched = listingSource.fetch();
    } catch (Exception e) {
      log.error("[종목목록] 조회 실패", e);
      return List.of();
    }

    if (fetched == null || fetched.isEmpty()) {
      if (!csvFallback) {
        log.warn("[종목목록] 목록이 비어 있다. 출처나 API 키를 확인할 것");
        return List.of();
      }

      fetched = bundledSource.fetch();
      log.warn("[종목목록] 목록이 비어 번들 CSV 로 대체한다: {}개", fetched.size());

      if (fetched.isEmpty()) {
        return List.of();
      }
    }

    List<Asset> deduped = dedupeBySymbol(fetched);
    List<Asset> storable = excludeUnstorableSymbols(deduped);
    List<Asset> real = excludeTestIssues(storable);
    List<Asset> kept = excludeNonCommonStock(real);

    log.info("[종목목록] 조회 {}개 -> 중복 제거 {}개 -> 최종 {}개",
        fetched.size(), deduped.size(), kept.size());

    return kept;
  }

  private List<Asset> dedupeBySymbol(List<Asset> assets) {
    return new ArrayList<>(
        assets.stream()
            .filter(a -> a.getSymbol() != null && !a.getSymbol().isBlank())
            .collect(Collectors.toMap(
                Asset::getSymbol,
                a -> a,
                (first, dup) -> first,
                LinkedHashMap::new))
            .values());
  }

  // 칸 길이를 넘거나 티커에 안 쓰는 글자가 든 심볼을 뺌
  // 이름이 어긋난 줄도 있어(CAPTW(EXP20260807) → Abcam plc) 이름 필터와 따로 봄
  List<Asset> excludeUnstorableSymbols(List<Asset> assets) {
    List<String> dropped = new ArrayList<>();
    List<Asset> kept = assets.stream()
        .filter(a -> {
          String symbol = a.getSymbol();
          boolean storable = symbol.length() <= symbolMaxLength && TICKER.matcher(symbol).matches();
          if (!storable) {
            dropped.add(symbol);
          }
          return storable;
        })
        .toList();

    if (!dropped.isEmpty()) {
      log.info("[종목목록] 저장할 수 없는 심볼 제외: {}개 {}", dropped.size(), dropped);
    }

    return kept;
  }

  List<Asset> excludeTestIssues(List<Asset> assets) {
    List<Asset> bySymbol = assets.stream()
        .filter(a -> !TEST_ISSUE_SYMBOL.matcher(a.getSymbol()).matches())
        .toList();
    List<Asset> kept = excludeByNamePatterns(bySymbol, TEST_ISSUE_NAMES);

    int removed = assets.size() - kept.size();
    if (removed > 0) {
      log.info("[종목목록] 거래소 시험 종목 제외: {}개", removed);
    }

    return kept;
  }

  // 보통주가 아닌 종목 제외(ETF 는 남김, 만료 없이 주식처럼 거래)
  List<Asset> excludeNonCommonStock(List<Asset> assets) {
    List<Asset> kept = excludeByNamePatterns(assets, excludeNamePatterns);

    int removed = assets.size() - kept.size();
    if (removed > 0) {
      log.info("[종목목록] 보통주가 아닌 종목 제외: {}개", removed);
    }

    return kept;
  }

  // 이름에 패턴(쉼표 구분)이 든 종목 제외
  public static List<Asset> excludeByNamePatterns(List<Asset> assets, String patternsCsv) {
    if (patternsCsv == null) {
      return assets;
    }
    List<String> patterns = java.util.Arrays.stream(patternsCsv.split(","))
        .map(String::toLowerCase)
        .filter(p -> !p.isBlank())
        .toList();

    if (patterns.isEmpty()) {
      return assets;
    }

    return assets.stream()
        .filter(a -> {
          String name = a.getName();
          if (name == null) {
            return true;   // 이름이 없으면 판단 근거가 없어 남김
          }
          String lower = name.toLowerCase();
          return patterns.stream().noneMatch(lower::contains);
        })
        .toList();
  }
}
