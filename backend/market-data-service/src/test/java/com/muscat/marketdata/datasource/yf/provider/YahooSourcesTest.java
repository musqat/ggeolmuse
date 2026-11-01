package com.muscat.marketdata.datasource.yf.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.marketdata.common.exception.YahooFinanceException;
import com.muscat.marketdata.datasource.yf.client.YahooFinanceClient;
import com.muscat.marketdata.datasource.yf.client.YahooParser;
import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 야후 응답을 받아 파서로 넘기는 provider 셋(실패는 모두 빈 값 또는 null, 수집 루프는 다음 종목으로)
@DisplayName("야후 provider")
class YahooSourcesTest {

  private static final LocalDate FROM = LocalDate.of(2024, 3, 1);
  private static final LocalDate TO = LocalDate.of(2024, 3, 31);

  private final YahooFinanceClient yahooClient = mock(YahooFinanceClient.class);
  private final YahooParser yahooParser = mock(YahooParser.class);

  @Nested
  @DisplayName("일봉")
  class CandleSourceTests {

    private final YahooCandleSource source = new YahooCandleSource(yahooClient, yahooParser);

    @Test
    @DisplayName("파싱한 일봉을 캔들로 바꾸고, 수정 종가가 없으면 종가로 채운다")
    void mapsCandles() {
      given(yahooClient.getDailyChartRaw("AAPL", FROM, TO)).willReturn("{json}");
      given(yahooParser.parseDailyAdjusted("{json}", "AAPL", FROM, TO)).willReturn(List.of(CandleDto.builder()
        .symbol("AAPL").date(FROM).open(BigDecimal.ONE).high(BigDecimal.TEN).low(BigDecimal.ONE).close(new BigDecimal("5"))
        .volume(100L).build()));

      List<Candle> candles = source.fetchDailyAdjusted("AAPL", FROM, TO);

      assertThat(candles).singleElement().satisfies(candle -> {
        assertThat(candle.getDate()).isEqualTo(FROM);
        assertThat(candle.getAdjustedClose()).isEqualByComparingTo("5");
      });
    }

    @Test
    @DisplayName("일봉이 없거나 파싱이 실패하면 빈 목록")
    void emptyOrFailure() {
      given(yahooClient.getDailyChartRaw(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .willReturn("{json}");
      given(yahooParser.parseDailyAdjusted("{json}", "NEW", FROM, TO)).willReturn(List.of());
      given(yahooParser.parseDailyAdjusted("{json}", "GONE", FROM, TO)).willThrow(new YahooFinanceException("no result"));

      assertThat(source.fetchDailyAdjusted("NEW", FROM, TO)).isEmpty();
      assertThat(source.fetchDailyAdjusted("GONE", FROM, TO)).isEmpty();
    }
  }

  @Nested
  @DisplayName("배당")
  class DividendSourceTests {

    private final YahooDividendSource source = new YahooDividendSource(yahooClient, yahooParser);

    @Test
    @DisplayName("파싱한 배당을 그대로 넘기고, 실패하면 빈 목록")
    void dividends() {
      List<DividendDto> dividends = List.of(DividendDto.builder().symbol("AAPL").exDate(FROM).amount(new BigDecimal("0.24")).build());
      given(yahooClient.getDailyChartRaw(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .willReturn("{json}");
      given(yahooParser.parseDividends("{json}", "AAPL", FROM, TO)).willReturn(dividends);
      given(yahooParser.parseDividends("{json}", "GONE", FROM, TO)).willThrow(new YahooFinanceException("no result"));

      assertThat(source.fetchDividends("AAPL", FROM, TO)).isSameAs(dividends);
      assertThat(source.fetchDividends("GONE", FROM, TO)).isEmpty();
    }
  }

  @Nested
  @DisplayName("종목 목록 · 종목 정보")
  class SymbolSourceTests {

    private final YfSymbolSource source = new YfSymbolSource(yahooClient, yahooParser);

    private Asset asset(String symbol) {
      return Asset.builder().symbol(symbol).build();
    }

    @Test
    @DisplayName("종목 정보는 대문자로 최근 차트를 받아 읽고, 빈 응답 · 실패면 null")
    void assetInfo() {
      Asset apple = asset("AAPL");
      given(yahooClient.getDailyChartRaw(org.mockito.ArgumentMatchers.eq("AAPL"), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).willReturn("{json}");
      given(yahooClient.getDailyChartRaw(org.mockito.ArgumentMatchers.eq("NEW"), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).willReturn(" ");
      given(yahooClient.getDailyChartRaw(org.mockito.ArgumentMatchers.eq("BOOM"), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).willThrow(new IllegalStateException("boom"));
      given(yahooParser.parseAssetInfoFromChart("{json}", "AAPL")).willReturn(apple);

      assertThat(source.getAsset("aapl")).isSameAs(apple);
      assertThat(source.getAsset("new")).isNull();
      assertThat(source.getAsset("boom")).isNull();
    }
  }
}
