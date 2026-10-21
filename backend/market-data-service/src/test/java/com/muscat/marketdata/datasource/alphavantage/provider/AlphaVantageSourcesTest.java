package com.muscat.marketdata.datasource.alphavantage.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.common.exception.AlphaVantageException;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// provider=alphavantage 일 때 쓰는 수집 소스. 응답 JSON 을 손으로 만들어 넣는다(AlphaVantage 는 최신 날짜가 먼저 온다)
@DisplayName("AlphaVantage provider")
class AlphaVantageSourcesTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final AlphaVantageClient client = mock(AlphaVantageClient.class);
  private final AlphaVantageRateLimiter limiter = mock(AlphaVantageRateLimiter.class);

  private static JsonNode json(String text) {
    try {
      return JSON.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static String day(String close, String dividend) {
    return "{\"1. open\":\"" + close + "\",\"2. high\":\"" + close + "\",\"3. low\":\"" + close + "\",\"4. close\":\"" + close
      + "\",\"5. adjusted close\":\"" + close + "\",\"6. volume\":\"1000\",\"7. dividend amount\":\"" + dividend
      + "\",\"8. split coefficient\":\"1.0\"}";
  }

  private static final String DAILY = "{\"Time Series (Daily)\":{"
    + "\"2024-03-06\":" + day("106", "0.0000") + ","
    + "\"2024-03-05\":" + day("105", "0.2400") + ","
    + "\"2024-03-04\":" + day("bad", "0.0000") + ","
    + "\"2024-03-01\":" + day("101", "0.0000") + ","
    + "\"2024-02-29\":" + day("99", "0.2400") + ","
    + "\"2024-02-28\":" + day("98", "0.0000") + "}}";

  @Nested
  @DisplayName("일봉")
  class CandleTests {

    private final AlphaVantageCandleSource source = new AlphaVantageCandleSource(client, limiter);

    @Test
    @DisplayName("100일 미만은 compact 로 받아 기간 안만 쓰고, 기간보다 이른 날을 만나면 멈춘다")
    void compactFiltersRange() {
      given(client.get("TIME_SERIES_DAILY_ADJUSTED", Map.of("symbol", "AAPL", "outputsize", "compact"))).willReturn(json(DAILY));

      List<Candle> candles = source.fetchDailyAdjusted("AAPL", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 5));

      // 3/6 은 기간 뒤, 3/4 는 숫자가 깨져 빠진다
      assertThat(candles).extracting(Candle::getDate).containsExactly(LocalDate.of(2024, 3, 5), LocalDate.of(2024, 3, 1));
      verify(limiter).waitIfNeeded();
    }

    @Test
    @DisplayName("100일 이상은 full 로 받아 날짜로 거르지 않는다")
    void fullKeepsAll() {
      given(client.get("TIME_SERIES_DAILY_ADJUSTED", Map.of("symbol", "AAPL", "outputsize", "full"))).willReturn(json(DAILY));

      List<Candle> candles = source.fetchDailyAdjusted("AAPL", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 3, 5));

      assertThat(candles).hasSize(5);
    }

    @Test
    @DisplayName("일봉 묶음이 없으면 빈 목록")
    void noSeries() {
      given(client.get(anyString(), anyMap())).willReturn(json("{\"Information\":\"none\"}"));

      assertThat(source.fetchDailyAdjusted("AAPL", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 5))).isEmpty();
    }
  }

  @Nested
  @DisplayName("배당")
  class DividendTests {

    private final AlphaVantageDividendSource source = new AlphaVantageDividendSource(client, limiter);

    @Test
    @DisplayName("기간 안에서 배당이 0 보다 큰 날만 배당으로 뽑는다")
    void dividendsInRange() {
      given(client.get("TIME_SERIES_DAILY_ADJUSTED", Map.of("symbol", "AAPL", "outputsize", "full"))).willReturn(json(DAILY));

      List<DividendDto> dividends = source.fetchDividends("AAPL", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 6));

      assertThat(dividends).extracting(DividendDto::getExDate, DividendDto::getAmount)
        .containsExactly(tuple(LocalDate.of(2024, 3, 5), new BigDecimal("0.2400")));
    }

    @Test
    @DisplayName("배당 칸이 없거나 숫자가 아니면 건너뛰고, 묶음이 없거나 호출이 실패하면 빈 목록")
    void missingOrFailure() {
      given(client.get(anyString(), anyMap()))
        .willReturn(json("{\"Time Series (Daily)\":{\"2024-03-05\":{\"4. close\":\"1\"},\"2024-03-04\":{\"7. dividend amount\":\"x\"}}}"))
        .willReturn(json("{}"))
        .willThrow(new AlphaVantageException("API rate limit exceeded"));

      for (int i = 0; i < 3; i++) {
        assertThat(source.fetchDividends("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))).isEmpty();
      }
    }
  }

  @Nested
  @DisplayName("환율")
  class FxTests {

    private final AlphaVantageFxSource source = new AlphaVantageFxSource(client, limiter);

    private static final String FX = "{\"Time Series FX (Daily)\":{\"2024-03-05\":{\"4. close\":\"1331.5\"},"
      + "\"2024-03-04\":{\"1. open\":\"1330\"},\"2024-03-01\":{\"4. close\":\"13.315\"}}}";

    @Test
    @DisplayName("그날 종가를 환율로 주고, 그날이 없거나 정상 범위 밖이거나 묶음이 없으면 비운다")
    void singleDay() {
      given(client.get("FX_DAILY", Map.of("from_symbol", "USD", "to_symbol", "KRW", "outputsize", "full")))
        .willReturn(json(FX), json(FX), json(FX), json("{}"));

      assertThat(source.fetchFx(LocalDate.of(2024, 3, 5))).hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("1331.5"));
      assertThat(source.fetchFx(LocalDate.of(2024, 3, 6))).isEmpty();
      assertThat(source.fetchFx(LocalDate.of(2024, 3, 1))).isEmpty();
      assertThat(source.fetchFx(LocalDate.of(2024, 3, 5))).isEmpty();
    }

    @Test
    @DisplayName("전체 환율은 날짜별로 모으고 종가가 없거나 정상 범위 밖인 날은 건너뛰며, 묶음이 없으면 빈 맵")
    void allDays() {
      given(client.get(anyString(), anyMap())).willReturn(json(FX), json("{}"));

      assertThat(source.fetchAllFxRates()).containsOnlyKeys(LocalDate.of(2024, 3, 5));
      assertThat(source.fetchAllFxRates()).isEmpty();
    }
  }

  @Nested
  @DisplayName("종목 목록 · 종목 정보")
  class SymbolTests {

    private final AlphaVantageSymbolSource source = new AlphaVantageSymbolSource(client, limiter);

    private static final String LISTING = """
      symbol,name,exchange,assetType,ipoDate,delistingDate,status
      AAPL,Apple Inc,NASDAQ,Stock,1980-12-12,null,Active
      SPY,SPDR S&P 500 ETF Trust,NYSE ARCA,ETF,1993-01-22,null,Active
      OTCX,Otc Co,OTC,Stock,2010-01-01,null,Active
      GONE,Gone Corp,NYSE,Stock,2001-01-01,2020-01-01,Delisted
      WARR,Some Warrant,NYSE,Warrant,2021-01-01,null,Active
      SHORT,row

      MSFT,Microsoft Corp,NASDAQ,Stock,1986-03-13,null,Active
      """;

    private void configure(boolean onlyMajor, int max) {
      ReflectionTestUtils.setField(source, "onlyMajorExchanges", onlyMajor);
      ReflectionTestUtils.setField(source, "maxSymbols", max);
    }

    @Test
    @DisplayName("상장 중인 주식 · ETF 중 주요 거래소만 종목으로 바꾼다")
    void filtersMajorListings() {
      configure(true, 0);
      given(client.getCsv("LISTING_STATUS", Map.of())).willReturn(LISTING);

      assertThat(source.fetchSymbols()).extracting(Asset::getSymbol, Asset::getAssetType)
        .containsExactly(tuple("AAPL", "EQUITY"), tuple("SPY", "ETF"), tuple("MSFT", "EQUITY"));
    }

    @Test
    @DisplayName("주요 거래소 조건을 끄면 다른 거래소도 넣고, 최대 수를 주면 그만큼만 쓴다")
    void allExchangesWithLimit() {
      configure(false, 3);
      given(client.getCsv("LISTING_STATUS", Map.of())).willReturn(LISTING);

      assertThat(source.fetchSymbols()).extracting(Asset::getSymbol).containsExactly("AAPL", "SPY", "OTCX");
    }

    @Test
    @DisplayName("목록 호출이 실패하면 빈 목록")
    void listingFailure() {
      configure(true, 0);
      given(client.getCsv("LISTING_STATUS", Map.of())).willThrow(new AlphaVantageException("API rate limit exceeded"));

      assertThat(source.fetchSymbols()).isEmpty();
    }

    @Test
    @DisplayName("종목 정보는 국가를 두 글자로 · 자산 유형을 EQUITY/ETF 로 바꾸고, 시총이 숫자가 아니면 비운다")
    void overview() {
      given(client.get("OVERVIEW", Map.of("symbol", "AAPL"))).willReturn(json(
        "{\"Symbol\":\"AAPL\",\"Name\":\"Apple Inc\",\"Country\":\"United States\",\"Currency\":\"USD\","
          + "\"AssetType\":\"Common Stock\",\"MarketCapitalization\":\"3000000000000\"}"));
      given(client.get("OVERVIEW", Map.of("symbol", "SSNLF"))).willReturn(json(
        "{\"Symbol\":\"SSNLF\",\"Country\":\"South Korea\",\"AssetType\":\"ETF\",\"MarketCapitalization\":\"None\"}"));
      given(client.get("OVERVIEW", Map.of("symbol", "SHOP"))).willReturn(json(
        "{\"Symbol\":\"SHOP\",\"Country\":\"Canada\",\"AssetType\":\"Unknown\"}"));
      given(client.get("OVERVIEW", Map.of("symbol", "NONE"))).willReturn(json("{}"));

      assertThat(source.getAsset("AAPL")).extracting(Asset::getCountry, Asset::getAssetType, Asset::getMarketCap)
        .containsExactly("US", "EQUITY", 3_000_000_000_000L);
      assertThat(source.getAsset("SSNLF")).extracting(Asset::getCountry, Asset::getAssetType, Asset::getMarketCap)
        .containsExactly("KR", "ETF", null);
      assertThat(source.getAsset("SHOP")).extracting(Asset::getCountry, Asset::getAssetType).containsExactly("CA", "EQUITY");
      assertThat(source.getAsset("NONE")).isNull();
    }
  }
}
