package com.muscat.marketdata.datasource.yf.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// NASDAQ 스크리너 응답(data.table.rows)을 종목으로 바꾼다. 신규 종목 · 시총 매칭이 이 결과를 쓴다
@DisplayName("NASDAQ 스크리너 응답 파서")
class NasdaqScreenerParserTest {

  private final NasdaqScreenerParser parser = new NasdaqScreenerParser();

  private static String rows(String rows) {
    return "{\"data\":{\"table\":{\"rows\":" + rows + "}}}";
  }

  @Test
  @DisplayName("종목 코드 · 이름 · 국가 · 시총을 옮기고 통화는 USD 로 둔다")
  void parsesRows() {
    List<Asset> assets = parser.parseStocks(rows("""
      [
        {"symbol":"AAPL","name":"Apple Inc. Common Stock","country":"United States","marketCap":"3,899,609,280,300","sector":"Technology"},
        {"symbol":"TSM","name":"Taiwan Semiconductor","country":"Taiwan","marketCap":"$1,200,000,000.75"}
      ]
      """));

    assertThat(assets).extracting(Asset::getSymbol, Asset::getName, Asset::getCountry, Asset::getCurrency, Asset::getMarketCap)
      .containsExactly(
        tuple("AAPL", "Apple Inc. Common Stock", "US", "USD", 3_899_609_280_300L),
        tuple("TSM", "Taiwan Semiconductor", "Taiwan", "USD", 1_200_000_000L));
  }

  @Test
  @DisplayName("이름에 ETF · fund · trust · exchange traded fund 가 들어 있으면 ETF, 아니면 EQUITY")
  void assetType() {
    List<Asset> assets = parser.parseStocks(rows("""
      [
        {"symbol":"SPY","name":"SPDR S&P 500 ETF"},
        {"symbol":"VFIAX","name":"Vanguard 500 Index Fund"},
        {"symbol":"GLD","name":"SPDR Gold Trust"},
        {"symbol":"XYZ","name":"Some Exchange Traded Fund"},
        {"symbol":"MSFT","name":"Microsoft Corporation"}
      ]
      """));

    assertThat(assets).extracting(Asset::getSymbol, Asset::getAssetType).containsExactly(
      tuple("SPY", "ETF"), tuple("VFIAX", "ETF"), tuple("GLD", "ETF"), tuple("XYZ", "ETF"), tuple("MSFT", "EQUITY"));
  }

  @Test
  @DisplayName("이름이 없으면 종목 코드를 이름으로, 국가가 없으면 US 로 둔다")
  void fallbacks() {
    List<Asset> assets = parser.parseStocks(rows("""
      [ {"symbol":"NEWCO","name":"","country":null} ]
      """));

    assertThat(assets).singleElement().satisfies(asset -> {
      assertThat(asset.getName()).isEqualTo("NEWCO");
      assertThat(asset.getCountry()).isEqualTo("US");
      assertThat(asset.getAssetType()).isEqualTo("EQUITY");
    });
  }

  @Test
  @DisplayName("시총이 없거나 숫자가 아니거나 0 이면 비운다")
  void marketCapUnparsable() {
    List<Asset> assets = parser.parseStocks(rows("""
      [
        {"symbol":"A","name":"A Corp"},
        {"symbol":"B","name":"B Corp","marketCap":"NA"},
        {"symbol":"C","name":"C Corp","marketCap":"0.00"},
        {"symbol":"D","name":"D Corp","marketCap":".5"},
        {"symbol":"E","name":"E Corp","marketCap":"null"}
      ]
      """));

    assertThat(assets).extracting(Asset::getMarketCap).containsOnlyNulls();
  }

  @Test
  @DisplayName("종목 코드가 없는 행은 건너뛴다")
  void skipsRowWithoutSymbol() {
    List<Asset> assets = parser.parseStocks(rows("""
      [ {"name":"No Symbol"}, {"symbol":null,"name":"Null Symbol"}, {"symbol":"OK","name":"Ok Inc"} ]
      """));

    assertThat(assets).extracting(Asset::getSymbol).containsExactly("OK");
  }

  @Test
  @DisplayName("rows 가 배열이 아니거나 JSON 이 깨지면 빈 목록")
  void invalidResponse() {
    assertThat(parser.parseStocks("{\"data\":{\"table\":{\"rows\":null}}}")).isEmpty();
    assertThat(parser.parseStocks("{}")).isEmpty();
    assertThat(parser.parseStocks("<html>blocked</html>")).isEmpty();
  }
}
