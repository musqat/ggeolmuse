package com.muscat.marketdata.datasource.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 목록을 받아 중복과 보통주가 아닌 것을 걸러내는 부분.
 *
 * 오탐이 나면 정상 종목이 목록에서 사라지므로 그쪽을 중점적으로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SymbolCatalog 단위 테스트")
class SymbolCatalogTest {

  @Mock
  private ListingStatusSource listingSource;

  @InjectMocks
  private SymbolCatalog catalog;

  @BeforeEach
  void setUp() {
    setPatterns("warrant, units,rights,preferred,depositary,notes due");
  }

  private void setPatterns(String patterns) {
    ReflectionTestUtils.setField(catalog, "excludeNamePatterns", patterns);
  }

  private static Asset asset(String symbol, String name) {
    return Asset.builder().symbol(symbol).name(name).country("US")
        .currency("USD").assetType("EQUITY").build();
  }

  @ParameterizedTest
  @DisplayName("보통주가 아닌 것은 걸러진다")
  @ValueSource(strings = {
      "EVgo Inc - Warrants (28/06/2026)",
      "Quetta Acquisition Corp - Units (1 1 Rights)",
      "Hennessy Advisors Inc Preferred Series A",
      "Some Corp Depositary Shares",
      "Acme Corp 5.5% Notes due 2030"
  })
  void 제외(String name) {
    given(listingSource.fetch()).willReturn(List.of(asset("X", name)));

    assertThat(catalog.fetchAll()).isEmpty();
  }

  @ParameterizedTest
  @DisplayName("정상 종목은 남는다")
  @ValueSource(strings = {
      "Apple Inc",
      "Lowe`s Cos. Inc",
      "Corning Inc",
      "International Seaways Inc",
      "FS Bancorp Inc",
      "SPDR S&P 500 ETF Trust",
      "Vanguard Total Stock Market ETF"
  })
  void 유지(String name) {
    given(listingSource.fetch()).willReturn(List.of(asset("X", name)));

    assertThat(catalog.fetchAll()).hasSize(1);
  }

  @Test
  @DisplayName("United 는 ' units' 에 걸리지 않는다")
  void united_오탐_없음() {
    // 패턴의 앞 공백이 이걸 막는다. 공백을 지우면 이 테스트가 깨진다
    given(listingSource.fetch()).willReturn(List.of(
        asset("UAL", "United Airlines Holdings Inc"),
        asset("UNH", "UnitedHealth Group Inc"),
        asset("UNP", "Union Pacific Corp")));

    assertThat(catalog.fetchAll()).hasSize(3);
  }

  @Test
  @DisplayName("ETF 는 거르지 않는다")
  void etf_유지() {
    // 워런트와 달리 만료되지 않고 주식처럼 거래되므로 백테스트에 유효하다
    given(listingSource.fetch()).willReturn(List.of(
        asset("SPY", "SPDR S&P 500 ETF Trust"),
        asset("QQQ", "Invesco QQQ Trust Series 1"),
        asset("HIBL", "Direxion Daily S&P 500 High Beta Bull 3X ETF")));

    assertThat(catalog.fetchAll()).hasSize(3);
  }

  @Test
  @DisplayName("보통주 SPAC 은 남기고 유닛·워런트만 거른다")
  void spac() {
    given(listingSource.fetch()).willReturn(List.of(
        asset("EVOX", "Evolution Global Acquisition Corp - Class A"),
        asset("EVOXU", "Evolution Global Acquisition Corp - Units (1 Ord Cls A & 1 Warrant)"),
        asset("EVOXW", "Evolution Global Acquisition Corp - Warrants")));

    assertThat(catalog.fetchAll()).extracting(Asset::getSymbol).containsExactly("EVOX");
  }

  @Test
  @DisplayName("같은 심볼이 여러 번 오면 첫 것만 남긴다")
  void 중복_제거() {
    given(listingSource.fetch()).willReturn(List.of(
        asset("AAPL", "Apple Inc"),
        asset("AAPL", "Apple Inc Duplicate"),
        asset("MSFT", "Microsoft Corporation")));

    List<Asset> result = catalog.fetchAll();

    assertThat(result).extracting(Asset::getSymbol).containsExactly("AAPL", "MSFT");
    assertThat(result.get(0).getName()).isEqualTo("Apple Inc");
  }

  @Test
  @DisplayName("심볼이 비었으면 버린다")
  void 빈_심볼() {
    given(listingSource.fetch()).willReturn(List.of(
        asset("", "심볼 없는 행"),
        asset("AAPL", "Apple Inc")));

    assertThat(catalog.fetchAll()).extracting(Asset::getSymbol).containsExactly("AAPL");
  }

  @Test
  @DisplayName("이름이 없으면 판단 근거가 없어 남긴다")
  void 이름_없음() {
    given(listingSource.fetch()).willReturn(List.of(asset("X", null)));

    assertThat(catalog.fetchAll()).hasSize(1);
  }

  @Test
  @DisplayName("출처가 비면 빈 목록")
  void 출처_빔() {
    given(listingSource.fetch()).willReturn(List.of());

    assertThat(catalog.fetchAll()).isEmpty();
  }

  @Test
  @DisplayName("출처가 예외를 던져도 빈 목록으로 끝난다")
  void 출처_예외() {
    // 부분 목록으로 판정하면 못 받아온 종목이 상장폐지처럼 보인다
    given(listingSource.fetch()).willThrow(new IllegalStateException("API 다운"));

    assertThat(catalog.fetchAll()).isEmpty();
  }

  @Test
  @DisplayName("패턴이 비면 아무것도 거르지 않는다")
  void 패턴_없음() {
    setPatterns("");
    given(listingSource.fetch())
        .willReturn(List.of(asset("X", "EVgo Inc - Warrants (28/06/2026)")));

    assertThat(catalog.fetchAll()).hasSize(1);
  }
}
