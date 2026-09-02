package com.muscat.marketdata.datasource.common;


import com.muscat.marketdata.domain.entity.Asset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 상장 종목 목록을 한 곳에서 가져온다.
 *
 * 시세 프로바이더(yahoo / alphavantage)와 무관하게 목록은 여기서만 받는다.
 * 프로바이더는 캔들·배당·환율을 담당하고, "무슨 종목이 있는가" 는 이 클래스가 답한다.
 *
 * 출처는 AlphaVantage LISTING_STATUS 다. 무료로 전체 목록을 CSV 한 번에 준다.
 *
 * NASDAQ 스크리너를 쓰다 옮겼다. 그쪽은 이런 문제가 있었다.
 *   - ETF 는 엔드포인트가 따로이고 페이지당 50개라 106번을 불러야 했다
 *   - 페이징 중 목록이 재정렬돼 26% 가 중복이고 실제 커버리지는 75% 였다
 *   - SPY 가 stocks 에도 etf 에도 없었다
 *   - assetType 을 이름으로 추측해야 했다
 *   - 상장폐지 판정을 따로 해야 했다
 *
 * LISTING_STATUS 는 호출 한 번에 14,000건을 주고 assetType 과 status 를 필드로 준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolCatalog {

  private final ListingStatusSource listingSource;

  // 보통주가 아닌 것을 이름으로 거른다. 백테스트는 주식을 전제하므로
  // 워런트·유닛·신주인수권·우선주·채권이 섞이면 결과가 뒤틀린다.
  //
  // 티커 접미사(W, WS)로 거르면 안 된다. LOW(Lowe's), GLW(Corning) 처럼
  // 정상 종목을 잡는다. 이름 기준이 안전하다.
  //
  // " units" 앞의 공백은 United 를 피하기 위한 것이다. 지우지 말 것.
  @Value("${marketdata.symbol-loader.exclude-name-patterns:warrant, units,rights,preferred,depositary,notes due}")
  private String excludeNamePatterns;

  /**
   * 상장 종목 전체를 받아온다. 보통주가 아닌 것과 중복은 걸러서 돌려준다.
   *
   * 실패하면 빈 목록을 돌려준다. 부분 목록으로 판정하면 받아오지 못한 종목이
   * 상장폐지된 것처럼 보일 수 있다.
   */
  public List<Asset> fetchAll() {
    List<Asset> fetched;

    try {
      fetched = listingSource.fetch();
    } catch (Exception e) {
      log.error("[종목목록] 조회 실패", e);
      return List.of();
    }

    if (fetched == null || fetched.isEmpty()) {
      log.warn("[종목목록] 목록이 비어 있다. 출처나 API 키를 확인할 것");
      return List.of();
    }

    List<Asset> deduped = dedupeBySymbol(fetched);
    List<Asset> kept = excludeNonCommonStock(deduped);

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

  /**
   * 이름으로 보통주가 아닌 것을 걸러낸다.
   *
   * 워런트는 만료일이 있어 그 구간을 지나면 데이터가 끊긴다. 백테스트에 넣으면
   * 중간에 값이 사라져 수익률이 의미를 잃는다. 우선주와 채권은 배당 구조가 달라
   * 주식 전제로 계산하면 틀린다.
   *
   * ETF 는 거르지 않는다. 만료되지 않고 주식처럼 거래되므로 백테스트에 유효하다.
   */
  List<Asset> excludeNonCommonStock(List<Asset> assets) {
    List<String> patterns = java.util.Arrays.stream(excludeNamePatterns.split(","))
        .map(String::toLowerCase)
        .filter(p -> !p.isBlank())
        .toList();

    if (patterns.isEmpty()) {
      return assets;
    }

    List<Asset> kept = assets.stream()
        .filter(a -> {
          String name = a.getName();
          if (name == null) {
            return true;   // 이름이 없으면 판단할 근거가 없어 남긴다
          }
          String lower = name.toLowerCase();
          return patterns.stream().noneMatch(lower::contains);
        })
        .toList();

    int removed = assets.size() - kept.size();
    if (removed > 0) {
      log.info("[종목목록] 보통주가 아닌 종목 제외: {}개", removed);
    }

    return kept;
  }
}
