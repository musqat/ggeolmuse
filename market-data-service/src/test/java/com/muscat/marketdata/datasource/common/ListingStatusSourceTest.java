package com.muscat.marketdata.datasource.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * LISTING_STATUS CSV 파싱.
 *
 * 본문은 실제 AlphaVantage 응답에서 그대로 잘라왔다.
 * NASDAQ 스크리너에서 옮긴 이유가 SPY 때문이라 그 행을 고정해 둔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListingStatusSource 단위 테스트")
class ListingStatusSourceTest {

  private static final String HEADER =
      "symbol,name,exchange,assetType,ipoDate,delistingDate,status";

  @Mock
  private AlphaVantageClient client;

  @InjectMocks
  private ListingStatusSource source;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(source, "onlyMajorExchanges", true);
    ReflectionTestUtils.setField(source, "maxSymbols", 0);
  }

  private void givenCsv(String... rows) {
    String csv = HEADER + "\n" + String.join("\n", rows);
    given(client.getCsv(eq("LISTING_STATUS"), any())).willReturn(csv);
  }

  @Test
  @DisplayName("SPY 가 들어온다")
  void spy() {
    // NASDAQ 스크리너에는 stocks 에도 etf 에도 SPY 가 없었다. 그래서 출처를 옮겼다
    givenCsv("SPY,SPDR S&P 500 ETF Trust,NYSE,ETF,1993-01-29,null,Active");

    List<Asset> result = source.fetch();

    assertThat(result).hasSize(1);
    Asset spy = result.get(0);
    assertThat(spy.getSymbol()).isEqualTo("SPY");
    assertThat(spy.getName()).isEqualTo("SPDR S&P 500 ETF Trust");
    assertThat(spy.getAssetType()).isEqualTo("ETF");
  }

  @Test
  @DisplayName("Stock 은 EQUITY 로 매핑한다")
  void 주식_매핑() {
    givenCsv("A,Agilent Technologies Inc,NYSE,Stock,1999-11-18,null,Active");

    assertThat(source.fetch())
        .singleElement()
        .satisfies(a -> {
          assertThat(a.getAssetType()).isEqualTo("EQUITY");
          assertThat(a.getCurrency()).isEqualTo("USD");
          assertThat(a.getCountry()).isEqualTo("US");
        });
  }

  @Test
  @DisplayName("시가총액은 응답에 없어 null 이다")
  void 시가총액_없음() {
    // LISTING_STATUS 에는 시총이 없다. 그건 따로 채운다
    givenCsv("AAPL,Apple Inc,NASDAQ,Stock,1980-12-12,null,Active");

    assertThat(source.fetch()).singleElement()
        .satisfies(a -> assertThat(a.getMarketCap()).isNull());
  }

  @Test
  @DisplayName("이름에 쉼표가 있어도 잘리지 않는다")
  void 쉼표_이름() {
    givenCsv("XYZ,Some Corp\\, Inc,NYSE,Stock,2020-01-01,null,Active"
        .replace("\\,", ","));

    assertThat(source.fetch()).singleElement()
        .satisfies(a -> assertThat(a.getName()).isEqualTo("Some Corp, Inc"));
  }

  @Test
  @DisplayName("상장폐지된 것은 목록에 넣지 않는다")
  void 상장폐지_제외() {
    givenCsv(
        "OLD,Delisted Corp,NYSE,Stock,2000-01-01,2020-05-05,Delisted",
        "NEW,Active Corp,NYSE,Stock,2020-01-01,null,Active");

    assertThat(source.fetch()).extracting(Asset::getSymbol).containsExactly("NEW");
  }

  @Test
  @DisplayName("Stock 도 ETF 도 아니면 버린다")
  void 기타_유형_제외() {
    givenCsv(
        "FUND1,Some Mutual Fund,NYSE,Mutual Fund,2010-01-01,null,Active",
        "OK,Normal Corp,NYSE,Stock,2010-01-01,null,Active");

    assertThat(source.fetch()).extracting(Asset::getSymbol).containsExactly("OK");
  }

  @Test
  @DisplayName("주요 거래소가 아니면 버린다")
  void 비주요_거래소_제외() {
    givenCsv(
        "OTC1,OTC Corp,OTC,Stock,2010-01-01,null,Active",
        "OK,Normal Corp,NASDAQ,Stock,2010-01-01,null,Active");

    assertThat(source.fetch()).extracting(Asset::getSymbol).containsExactly("OK");
  }

  @Test
  @DisplayName("only-major-exchanges 를 끄면 전부 받는다")
  void 거래소_필터_해제() {
    ReflectionTestUtils.setField(source, "onlyMajorExchanges", false);
    givenCsv("OTC1,OTC Corp,OTC,Stock,2010-01-01,null,Active");

    assertThat(source.fetch()).hasSize(1);
  }

  @Test
  @DisplayName("max-symbols 로 상한을 건다")
  void 상한() {
    ReflectionTestUtils.setField(source, "maxSymbols", 2);
    givenCsv(
        "A1,Corp One,NYSE,Stock,2010-01-01,null,Active",
        "A2,Corp Two,NYSE,Stock,2010-01-01,null,Active",
        "A3,Corp Three,NYSE,Stock,2010-01-01,null,Active");

    assertThat(source.fetch()).hasSize(2);
  }

  @Test
  @DisplayName("응답이 비면 빈 목록")
  void 빈_응답() {
    given(client.getCsv(eq("LISTING_STATUS"), any())).willReturn("");

    assertThat(source.fetch()).isEmpty();
  }

  @Test
  @DisplayName("깨진 행은 건너뛰고 나머지를 살린다")
  void 깨진_행() {
    givenCsv(
        "TOOSHORT,name,NYSE",
        "OK,Normal Corp,NYSE,Stock,2010-01-01,null,Active");

    assertThat(source.fetch()).extracting(Asset::getSymbol).containsExactly("OK");
  }
}
