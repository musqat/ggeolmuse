package com.muscat.marketdata.datasource.yf.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.marketdata.common.exceptions.YahooFinanceException;
import com.muscat.marketdata.domain.dto.CandleDto;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("YahooParser 단위 테스트")
class YahooParserTest {

  private static final String SYMBOL = "AAPL";

  // 2024-09-16 / 09-17 / 09-18 UTC 자정
  private static final long TS_0916 = 1726444800L;
  private static final long TS_0917 = 1726531200L;
  private static final long TS_0918 = 1726617600L;

  private static final LocalDate D_0916 = LocalDate.of(2024, 9, 16);
  private static final LocalDate D_0917 = LocalDate.of(2024, 9, 17);
  private static final LocalDate D_0918 = LocalDate.of(2024, 9, 18);

  private YahooParser parser;

  @BeforeEach
  void setUp() {
    parser = new YahooParser();
  }

  private static final String CHART_JSON = """
    {
      "chart": {
        "result": [{
          "meta": { "symbol": "AAPL", "currency": "USD" },
          "timestamp": [1726444800, 1726531200, 1726617600],
          "indicators": {
            "quote": [{
              "open":   [216.54, 215.75, 217.55],
              "high":   [217.22, 216.90, 222.71],
              "low":    [213.92, 214.50, 217.54],
              "close":  [216.32, 216.79, 220.69],
              "volume": [59357400, 45519300, 318679900]
            }],
            "adjclose": [{ "adjclose": [215.30, 215.77, 219.65] }]
          }
        }]
      }
    }
    """;

  @Nested
  @DisplayName("parseDailyAdjusted")
  class ParseDailyAdjusted {

    @Test
    @DisplayName("정상 응답이면 일봉 전체를 뽑는다")
    void 정상_파싱() {
      List<CandleDto> result = parser.parseDailyAdjusted(CHART_JSON, SYMBOL, null, null);

      assertThat(result).hasSize(3);

      CandleDto first = result.get(0);
      assertThat(first.getSymbol()).isEqualTo(SYMBOL);
      assertThat(first.getDate()).isEqualTo(D_0916);
      assertThat(first.getOpen()).isEqualByComparingTo("216.54");
      assertThat(first.getHigh()).isEqualByComparingTo("217.22");
      assertThat(first.getLow()).isEqualByComparingTo("213.92");
      assertThat(first.getClose()).isEqualByComparingTo("216.32");
      assertThat(first.getAdjustedClose()).isEqualByComparingTo("215.30");
      assertThat(first.getVolume()).isEqualTo(59357400L);
      assertThat(first.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("epoch 를 UTC 기준 날짜로 바꾼다")
    void 날짜_변환() {
      List<CandleDto> result = parser.parseDailyAdjusted(CHART_JSON, SYMBOL, null, null);

      assertThat(result).extracting(CandleDto::getDate)
        .containsExactly(D_0916, D_0917, D_0918);
    }

    @Test
    @DisplayName("adjclose 블록이 없으면 close 로 채운다")
    void adjclose_없음() {
      String json = """
        {
          "chart": { "result": [{
            "meta": { "symbol": "AAPL", "currency": "USD" },
            "timestamp": [1726444800],
            "indicators": { "quote": [{
              "open": [216.54], "high": [217.22], "low": [213.92],
              "close": [216.32], "volume": [59357400]
            }] }
          }] }
        }
        """;

      List<CandleDto> result = parser.parseDailyAdjusted(json, SYMBOL, null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getAdjustedClose()).isEqualByComparingTo("216.32");
    }

    @Test
    @DisplayName("배열 값이 null 인 자리는 필드도 null")
    void 값_null() {
      String json = """
        {
          "chart": { "result": [{
            "meta": { "symbol": "AAPL", "currency": "USD" },
            "timestamp": [1726444800],
            "indicators": {
              "quote": [{
                "open": [null], "high": [217.22], "low": [213.92],
                "close": [216.32], "volume": [null]
              }],
              "adjclose": [{ "adjclose": [null] }]
            }
          }] }
        }
        """;

      CandleDto dto = parser.parseDailyAdjusted(json, SYMBOL, null, null).get(0);

      assertThat(dto.getOpen()).isNull();
      assertThat(dto.getVolume()).isNull();
      assertThat(dto.getHigh()).isEqualByComparingTo("217.22");
      // adjclose 가 null 이면 close 로 대체
      assertThat(dto.getAdjustedClose()).isEqualByComparingTo("216.32");
    }

    @Test
    @DisplayName("timestamp 가 null 인 건은 통째로 건너뛴다")
    void timestamp_null_스킵() {
      String json = """
        {
          "chart": { "result": [{
            "meta": { "symbol": "AAPL", "currency": "USD" },
            "timestamp": [1726444800, null, 1726617600],
            "indicators": { "quote": [{
              "open": [1, 2, 3], "high": [1, 2, 3], "low": [1, 2, 3],
              "close": [1, 2, 3], "volume": [1, 2, 3]
            }] }
          }] }
        }
        """;

      List<CandleDto> result = parser.parseDailyAdjusted(json, SYMBOL, null, null);

      assertThat(result).extracting(CandleDto::getDate).containsExactly(D_0916, D_0918);
    }

    @Test
    @DisplayName("fromDate 이전은 뺀다")
    void from_필터() {
      List<CandleDto> result = parser.parseDailyAdjusted(CHART_JSON, SYMBOL, D_0917, null);

      assertThat(result).extracting(CandleDto::getDate).containsExactly(D_0917, D_0918);
    }

    @Test
    @DisplayName("toDate 이후는 뺀다")
    void to_필터() {
      List<CandleDto> result = parser.parseDailyAdjusted(CHART_JSON, SYMBOL, null, D_0917);

      assertThat(result).extracting(CandleDto::getDate).containsExactly(D_0916, D_0917);
    }

    @Test
    @DisplayName("경계일은 포함한다")
    void 경계_포함() {
      List<CandleDto> result = parser.parseDailyAdjusted(CHART_JSON, SYMBOL, D_0916, D_0916);

      assertThat(result).extracting(CandleDto::getDate).containsExactly(D_0916);
    }

    @Test
    @DisplayName("범위가 데이터 밖이면 빈 리스트")
    void 범위_밖() {
      List<CandleDto> result = parser.parseDailyAdjusted(
        CHART_JSON, SYMBOL, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("symbolOverride 가 meta.symbol 보다 우선")
    void symbol_override() {
      List<CandleDto> result = parser.parseDailyAdjusted(CHART_JSON, "TSLA", null, null);

      assertThat(result).extracting(CandleDto::getSymbol).containsOnly("TSLA");
    }

    @Test
    @DisplayName("symbolOverride 가 null 이면 meta.symbol 을 쓴다")
    void symbol_meta_사용() {
      List<CandleDto> result = parser.parseDailyAdjusted(CHART_JSON, null, null, null);

      assertThat(result).extracting(CandleDto::getSymbol).containsOnly("AAPL");
    }

    @Test
    @DisplayName("currency 가 없으면 USD")
    void currency_기본값() {
      String json = """
        {
          "chart": { "result": [{
            "meta": { "symbol": "AAPL" },
            "timestamp": [1726444800],
            "indicators": { "quote": [{
              "open": [1], "high": [1], "low": [1], "close": [1], "volume": [1]
            }] }
          }] }
        }
        """;

      CandleDto dto = parser.parseDailyAdjusted(json, SYMBOL, null, null).get(0);

      assertThat(dto.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("chart.result 가 비면 예외")
    void result_없음() {
      String json = """
        { "chart": { "result": [] } }
        """;

      assertThatThrownBy(() -> parser.parseDailyAdjusted(json, SYMBOL, null, null))
        .isInstanceOf(YahooFinanceException.class)
        .hasMessageContaining("차트 결과가 없습니다");
    }

    @Test
    @DisplayName("timestamp 가 없으면 예외")
    void timestamp_없음() {
      String json = """
        {
          "chart": { "result": [{
            "meta": { "symbol": "AAPL" },
            "indicators": { "quote": [{ "close": [1] }] }
          }] }
        }
        """;

      assertThatThrownBy(() -> parser.parseDailyAdjusted(json, SYMBOL, null, null))
        .isInstanceOf(YahooFinanceException.class)
        .hasMessageContaining("필수 데이터가 없습니다");
    }

    @Test
    @DisplayName("quote 가 없으면 예외")
    void quote_없음() {
      String json = """
        {
          "chart": { "result": [{
            "meta": { "symbol": "AAPL" },
            "timestamp": [1726444800],
            "indicators": {}
          }] }
        }
        """;

      assertThatThrownBy(() -> parser.parseDailyAdjusted(json, SYMBOL, null, null))
        .isInstanceOf(YahooFinanceException.class)
        .hasMessageContaining("필수 데이터가 없습니다");
    }

    @Test
    @DisplayName("JSON 이 깨졌으면 YahooFinanceException 으로 감싼다")
    void 깨진_json() {
      assertThatThrownBy(() -> parser.parseDailyAdjusted("{ not json", SYMBOL, null, null))
        .isInstanceOf(YahooFinanceException.class)
        .hasMessageContaining("파싱 실패");
    }
  }

  @Nested
  @DisplayName("parseDividends")
  class ParseDividends {

    private static final String DIVIDEND_JSON = """
      {
        "chart": { "result": [{
          "meta": { "symbol": "AAPL" },
          "events": { "dividends": {
            "1726444800": { "amount": 0.25, "date": 1726444800 },
            "1726617600": { "amount": 0.26, "date": 1726617600 }
          } }
        }] }
      }
      """;

    @Test
    @DisplayName("배당 이벤트를 전부 뽑는다")
    void 정상_파싱() {
      List<DividendDto> result = parser.parseDividends(DIVIDEND_JSON, SYMBOL, null, null);

      assertThat(result).hasSize(2);
      assertThat(result).extracting(DividendDto::getExDate)
        .containsExactlyInAnyOrder(D_0916, D_0918);
    }

    @Test
    @DisplayName("symbol, currency, source 는 고정값으로 채운다")
    void 고정_필드() {
      DividendDto dto = parser.parseDividends(DIVIDEND_JSON, SYMBOL, null, null).get(0);

      assertThat(dto.getSymbol()).isEqualTo(SYMBOL);
      assertThat(dto.getCurrency()).isEqualTo("USD");
      assertThat(dto.getSource()).isEqualTo("Yahoo");
      assertThat(dto.getAmount()).isEqualByComparingTo("0.25");
    }

    @Test
    @DisplayName("events.dividends 가 없으면 빈 리스트 (예외 아님)")
    void 배당_없음() {
      String json = """
        { "chart": { "result": [{ "meta": { "symbol": "AAPL" } }] } }
        """;

      assertThat(parser.parseDividends(json, SYMBOL, null, null)).isEmpty();
    }

    @Test
    @DisplayName("date 필드가 없으면 키를 timestamp 로 쓴다")
    void 키를_timestamp로() {
      String json = """
        {
          "chart": { "result": [{
            "events": { "dividends": { "1726444800": { "amount": 0.25 } } }
          }] }
        }
        """;

      List<DividendDto> result = parser.parseDividends(json, SYMBOL, null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getExDate()).isEqualTo(D_0916);
    }

    @Test
    @DisplayName("date 도 없고 키도 숫자가 아니면 그 건만 버린다")
    void timestamp_없음() {
      // 예전엔 삼항 언박싱 NPE 로 종목 전체가 실패했다. 한 건만 빠지는지 확인
      String json = """
        {
          "chart": { "result": [{
            "events": { "dividends": { "unknown": { "amount": 0.25 } } }
          }] }
        }
        """;

      assertThat(parser.parseDividends(json, SYMBOL, null, null)).isEmpty();
    }

    @Test
    @DisplayName("깨진 건이 섞여 있어도 나머지는 살린다")
    void 일부만_깨짐() {
      String json = """
        {
          "chart": { "result": [{
            "events": { "dividends": {
              "unknown": { "amount": 0.25 },
              "1726444800": { "amount": 0.26, "date": 1726444800 }
            } }
          }] }
        }
        """;

      List<DividendDto> result = parser.parseDividends(json, SYMBOL, null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getExDate()).isEqualTo(D_0916);
    }

    @Test
    @DisplayName("amount 가 0 이하거나 없으면 버린다")
    void 금액_없음() {
      String json = """
        {
          "chart": { "result": [{
            "events": { "dividends": {
              "1726444800": { "amount": 0, "date": 1726444800 },
              "1726531200": { "amount": -0.1, "date": 1726531200 },
              "1726617600": { "date": 1726617600 }
            } }
          }] }
        }
        """;

      assertThat(parser.parseDividends(json, SYMBOL, null, null)).isEmpty();
    }

    @Test
    @DisplayName("날짜 범위 밖은 버린다")
    void 범위_필터() {
      List<DividendDto> result = parser.parseDividends(DIVIDEND_JSON, SYMBOL, D_0917, null);

      assertThat(result).extracting(DividendDto::getExDate).containsExactly(D_0918);
    }

    @Test
    @DisplayName("chart.result 가 비면 예외")
    void result_없음() {
      assertThatThrownBy(
        () -> parser.parseDividends("{ \"chart\": { \"result\": [] } }", SYMBOL, null, null))
        .isInstanceOf(YahooFinanceException.class);
    }
  }

  @Nested
  @DisplayName("parseAssetInfoFromChart")
  class ParseAssetInfo {

    @Test
    @DisplayName("longName 을 우선 쓴다")
    void longName_우선() {
      String json = """
        {
          "chart": { "result": [{ "meta": {
            "longName": "Apple Inc.", "shortName": "Apple",
            "currency": "USD", "instrumentType": "EQUITY"
          } }] }
        }
        """;

      Asset asset = parser.parseAssetInfoFromChart(json, SYMBOL);

      assertThat(asset.getName()).isEqualTo("Apple Inc.");
      assertThat(asset.getSymbol()).isEqualTo(SYMBOL);
      assertThat(asset.getCountry()).isEqualTo("US");
      assertThat(asset.getCurrency()).isEqualTo("USD");
      assertThat(asset.getAssetType()).isEqualTo("EQUITY");
      assertThat(asset.getMarketCap()).isNull();
    }

    @Test
    @DisplayName("longName 이 없으면 shortName")
    void shortName_대체() {
      String json = """
        { "chart": { "result": [{ "meta": { "shortName": "Apple" } }] } }
        """;

      assertThat(parser.parseAssetInfoFromChart(json, SYMBOL).getName()).isEqualTo("Apple");
    }

    @Test
    @DisplayName("이름이 둘 다 없으면 symbol 을 쓴다")
    void symbol_대체() {
      String json = """
        { "chart": { "result": [{ "meta": { "currency": "USD" } }] } }
        """;

      assertThat(parser.parseAssetInfoFromChart(json, SYMBOL).getName()).isEqualTo(SYMBOL);
    }

    @Test
    @DisplayName("instrumentType 이 ETF 면 ETF")
    void etf() {
      String json = """
        { "chart": { "result": [{ "meta": { "instrumentType": "etf" } }] } }
        """;

      assertThat(parser.parseAssetInfoFromChart(json, "SPY").getAssetType()).isEqualTo("ETF");
    }

    @Test
    @DisplayName("ETF 가 아닌 타입은 전부 EQUITY")
    void 그외_equity() {
      String json = """
        { "chart": { "result": [{ "meta": { "instrumentType": "MUTUALFUND" } }] } }
        """;

      assertThat(parser.parseAssetInfoFromChart(json, SYMBOL).getAssetType()).isEqualTo("EQUITY");
    }

    @Test
    @DisplayName("instrumentType 이 없어도 EQUITY")
    void 타입_없음() {
      String json = """
        { "chart": { "result": [{ "meta": {} }] } }
        """;

      assertThat(parser.parseAssetInfoFromChart(json, SYMBOL).getAssetType()).isEqualTo("EQUITY");
    }

    @Test
    @DisplayName("currency 가 없으면 USD")
    void currency_기본값() {
      String json = """
        { "chart": { "result": [{ "meta": { "longName": "Apple Inc." } }] } }
        """;

      assertThat(parser.parseAssetInfoFromChart(json, SYMBOL).getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("입력이 null 이거나 공백이면 null")
    void 입력_없음() {
      assertThat(parser.parseAssetInfoFromChart(null, SYMBOL)).isNull();
      assertThat(parser.parseAssetInfoFromChart("   ", SYMBOL)).isNull();
    }

    @Test
    @DisplayName("chart.result 가 비면 null (여기선 예외를 던지지 않는다)")
    void result_없음() {
      assertThat(parser.parseAssetInfoFromChart("{ \"chart\": { \"result\": [] } }", SYMBOL))
        .isNull();
    }

    @Test
    @DisplayName("JSON 이 깨져도 null")
    void 깨진_json() {
      assertThat(parser.parseAssetInfoFromChart("{ not json", SYMBOL)).isNull();
    }
  }
}
