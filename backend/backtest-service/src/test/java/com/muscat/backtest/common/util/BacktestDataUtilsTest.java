package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacktestDataUtils 시세 · 환율 · 배당 조회")
class BacktestDataUtilsTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate PAST = LocalDate.of(2024, 3, 4);
  private static final BigDecimal DEFAULT_FX = new BigDecimal("1400");

  @Mock
  private MarketDataClientWrapper client;

  private static StockPriceDto current(String price, boolean available) {
    return new StockPriceDto(SYMBOL, null, new BigDecimal(price), null, null, null, null, null, null,
      null, null, null, null, null, "USD", available, null, null);
  }

  private static OHLCPriceDto ohlc(LocalDate date, String close, boolean available) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(SYMBOL, date, price, price, price, price, price, 1_000L, "USD", available);
  }

  @Nested
  @DisplayName("현재가 · 평가일 시세")
  class PriceTests {

    @Test
    @DisplayName("현재가 응답이 없거나 쓸 수 없으면 STOCK_DATA_NOT_FOUND")
    void currentPriceMissing() {
      given(client.getCurrentPrice(SYMBOL)).willReturn(null, current("100", false));

      for (int i = 0; i < 2; i++) {
        assertThatThrownBy(() -> BacktestDataUtils.getCurrentPrice(client, SYMBOL))
          .isInstanceOf(BacktestException.class)
          .extracting("code")
          .isEqualTo(BacktestResponse.STOCK_DATA_NOT_FOUND);
      }
    }

    @Test
    @DisplayName("평가일이 오늘이면 현재가를 쓴다")
    void priceAtToday() {
      given(client.getCurrentPrice(SYMBOL)).willReturn(current("230", true));

      assertThat(BacktestDataUtils.getPriceAt(client, SYMBOL, LocalDate.now()).currentPrice()).isEqualByComparingTo("230");
      verify(client, never()).getOHLCPrice(anyString(), anyString());
    }

    @Test
    @DisplayName("평가일이 과거면 평가일 종가를 현재가 자리에 넣는다")
    void priceAtPast() {
      given(client.getOHLCPrice(SYMBOL, PAST.toString())).willReturn(ohlc(PAST, "150", true));

      StockPriceDto price = BacktestDataUtils.getPriceAt(client, SYMBOL, PAST);

      assertThat(price.currentPrice()).isEqualByComparingTo("150");
      assertThat(price.date()).isEqualTo(PAST);
      assertThat(price.available()).isTrue();
    }
  }

  @Nested
  @DisplayName("환율")
  class FxTests {

    @Test
    @DisplayName("평가일이 과거면 평가일 환율, 오늘이면 최신 환율")
    void fxRateAt() {
      given(client.getFxRate(PAST.toString())).willReturn(new FxRateDto(PAST, new BigDecimal("1330")));
      given(client.getLatestFxRate()).willReturn(new FxRateDto(LocalDate.now(), new BigDecimal("1380")));

      assertThat(BacktestDataUtils.getFxRateAt(client, PAST).rate()).isEqualByComparingTo("1330");
      assertThat(BacktestDataUtils.getFxRateAt(client, LocalDate.now()).rate()).isEqualByComparingTo("1380");
    }

    @Test
    @DisplayName("과거 환율이 비면 조회한 날짜와 설정한 기본 환율")
    void historicalFallback() {
      given(client.getFxRate(PAST.toString())).willReturn(null);
      given(client.defaultFxRate()).willReturn(DEFAULT_FX);

      FxRateDto rate = BacktestDataUtils.getHistoricalFxRate(client, PAST);

      assertThat(rate.rate()).isEqualByComparingTo(DEFAULT_FX);
      assertThat(rate.date()).isEqualTo(PAST);
      assertThat(rate.defaulted()).isTrue();
    }

    @Test
    @DisplayName("최신 환율이 비면 오늘 날짜에 설정한 기본 환율")
    void currentFallback() {
      given(client.getLatestFxRate()).willReturn(null);
      given(client.defaultFxRate()).willReturn(DEFAULT_FX);

      FxRateDto rate = BacktestDataUtils.getCurrentFxRate(client);

      assertThat(rate.rate()).isEqualByComparingTo(DEFAULT_FX);
      assertThat(rate.date()).isEqualTo(LocalDate.now());
      assertThat(rate.defaulted()).isTrue();
    }

    @Test
    @DisplayName("일괄 환율은 응답에 없는 날만 기본 환율로 채운다")
    void bulkFillsMissing() {
      given(client.getBulkFxRates(List.of("2024-03-04", "2024-03-05")))
        .willReturn(Map.of("2024-03-04", new BigDecimal("1340")));
      given(client.defaultFxRate()).willReturn(DEFAULT_FX);

      FxRates rates = BacktestDataUtils.getBulkFxRates(client, List.of(PAST, PAST.plusDays(1)));

      assertThat(rates.get(PAST)).isEqualByComparingTo("1340");
      assertThat(rates.get(PAST.plusDays(1))).isEqualByComparingTo(DEFAULT_FX);
      assertThat(rates.defaulted()).containsExactly(PAST.plusDays(1));
    }

    @Test
    @DisplayName("일괄 환율 응답이 비면 모든 날이 기본 환율")
    void bulkFailure() {
      given(client.getBulkFxRates(List.of("2024-03-04", "2024-03-05"))).willReturn(Map.of());
      given(client.defaultFxRate()).willReturn(DEFAULT_FX);

      FxRates rates = BacktestDataUtils.buildDailyFxRateMap(client, PAST, PAST.plusDays(1));

      assertThat(rates.rates()).hasSize(2).allSatisfy((date, rate) -> assertThat(rate).isEqualByComparingTo(DEFAULT_FX));
      assertThat(rates.defaulted()).containsExactlyInAnyOrder(PAST, PAST.plusDays(1));
    }
  }

  @Nested
  @DisplayName("배당 · 기간 시세")
  class DividendAndRangeTests {

    @Test
    @DisplayName("배당은 권리락일 · 지급일 · 금액으로 옮긴다")
    void mapsDividends() {
      given(client.getDividendHistory(SYMBOL, "2024-01-01", "2024-12-31")).willReturn(List.of(
        new DividendDto(SYMBOL, LocalDate.of(2024, 2, 9), LocalDate.of(2024, 2, 15), null, new BigDecimal("0.24"), "USD", "yahoo")));

      DividendHistoryDto history = BacktestDataUtils.getDividendHistory(client, SYMBOL, LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 12, 31));

      assertThat(history.getSymbol()).isEqualTo(SYMBOL);
      assertThat(history.getDividends())
        .extracting(DividendHistoryDto.DividendPayment::getExDate, DividendHistoryDto.DividendPayment::getPayDate,
          DividendHistoryDto.DividendPayment::getAmount)
        .containsExactly(tuple(LocalDate.of(2024, 2, 9), LocalDate.of(2024, 2, 15), new BigDecimal("0.24")));
    }

    @Test
    @DisplayName("배당이 없거나 비면 종목만 담은 빈 이력")
    void emptyDividends() {
      given(client.getDividendHistory(anyString(), anyString(), anyString()))
        .willReturn(null).willReturn(List.of());

      for (int i = 0; i < 2; i++) {
        DividendHistoryDto history = BacktestDataUtils.getDividendHistory(client, SYMBOL, PAST, PAST.plusDays(30));
        assertThat(history.getSymbol()).isEqualTo(SYMBOL);
        assertThat(history.getDividends()).isEmpty();
      }
    }

    @Test
    @DisplayName("기간 시세는 쓸 수 없는 날을 빼고, 같은 날이 둘이면 뒤의 것을 쓴다")
    void priceMap() {
      given(client.getOHLCPriceRange(SYMBOL, "2024-03-04", "2024-03-06")).willReturn(List.of(
        ohlc(PAST, "100", true), ohlc(PAST, "101", true), ohlc(PAST.plusDays(1), "102", false), ohlc(PAST.plusDays(2), "103", true)));

      Map<LocalDate, OHLCPriceDto> map = BacktestDataUtils.buildPriceMap(client, SYMBOL, PAST, PAST.plusDays(2));

      assertThat(map).containsOnlyKeys(PAST, PAST.plusDays(2));
      assertThat(map.get(PAST).closePrice()).isEqualByComparingTo("101");
    }
  }
}
