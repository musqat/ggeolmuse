package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.commonlib.dto.OHLCPriceDto;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PriceLookup 단위 테스트")
class PriceLookupTest {

  private static LocalDate d(String s) { return LocalDate.parse(s); }

  private static OHLCPriceDto price(String date, double close, boolean available) {
    BigDecimal c = BigDecimal.valueOf(close);
    return new OHLCPriceDto("AAPL", d(date), c, c, c, c, c, 1L, "USD", available);
  }

  @Test
  @DisplayName("withFallback: 당일 available 데이터를 반환한다")
  void withFallback_exactDay() {
    var r = PriceLookup.withFallback((sym, dt) -> price(dt, 50, true), "AAPL", d("2025-06-05"), 5);
    assertThat(r.closePrice()).isEqualByComparingTo("50");
  }

  @Test
  @DisplayName("withFallback: 휴장일이면 과거로 거슬러 찾는다")
  void withFallback_fallsBack() {
    var r = PriceLookup.withFallback(
        (sym, dt) -> d(dt).equals(d("2025-05-30")) ? price(dt, 48, true) : null,
        "AAPL", d("2025-06-02"), 5); // 월요일 → 직전 금요일(5-30)
    assertThat(r.closePrice()).isEqualByComparingTo("48");
  }

  @Test
  @DisplayName("withFallback: available=false는 건너뛴다")
  void withFallback_skipsUnavailable() {
    var r = PriceLookup.withFallback(
        (sym, dt) -> d(dt).equals(d("2025-06-05")) ? price(dt, 99, false)
            : d(dt).equals(d("2025-06-04")) ? price(dt, 47, true) : null,
        "AAPL", d("2025-06-05"), 5);
    assertThat(r.closePrice()).isEqualByComparingTo("47");
  }

  @Test
  @DisplayName("withFallback: maxDays 내 못 찾으면 STOCK_DATA_NOT_FOUND")
  void withFallback_throws() {
    assertThatThrownBy(() -> PriceLookup.withFallback((sym, dt) -> null, "AAPL", d("2025-06-05"), 5))
        .isInstanceOf(BacktestException.class)
        .hasMessageContaining("주가 데이터를 찾을 수 없습니다 (5일 검색)");
  }

  @Test
  @DisplayName("withFallback: 매번 조회가 실패하면 데이터 없음이 아니라 시세 서비스 사용 불가로 올리고 마지막 원인을 붙인다")
  void withFallback_allFailed_unavailable() {
    RuntimeException down = new RuntimeException("down");

    assertThatThrownBy(() -> PriceLookup.withFallback((sym, dt) -> { throw down; }, "AAPL", d("2025-06-05"), 5))
        .isInstanceOf(BacktestException.class)
        .hasCause(down)
        .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
  }

  @Test
  @DisplayName("withFallback: 한 번이라도 답을 받았거나 4xx 면 데이터 없음으로 본다")
  void withFallback_answered_notFound() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal/market/ohlc", Map.of(), null,
        StandardCharsets.UTF_8, null);
    FeignException.BadRequest badRequest = new FeignException.BadRequest("Bad Request", request, null, Map.of());

    assertThatThrownBy(() -> PriceLookup.withFallback((sym, dt) -> { throw badRequest; }, "AAPL", d("2025-06-05"), 5))
        .extracting("code").isEqualTo(BacktestResponse.STOCK_DATA_NOT_FOUND);
    assertThatThrownBy(() -> PriceLookup.withFallback(
        (sym, dt) -> {
          if (d(dt).equals(d("2025-06-05"))) {
            return price(dt, 1, false);
          }
          throw new RuntimeException("down");
        }, "AAPL", d("2025-06-05"), 5))
        .extracting("code").isEqualTo(BacktestResponse.STOCK_DATA_NOT_FOUND);
  }

  @Test
  @DisplayName("fromMap: 첫 non-null 반환")
  void fromMap_found() {
    Map<LocalDate, OHLCPriceDto> map = new HashMap<>();
    map.put(d("2025-06-05"), price("2025-06-05", 50, true));
    assertThat(PriceLookup.fromMap(map, d("2025-06-05"), 5).closePrice()).isEqualByComparingTo("50");
  }

  @Test
  @DisplayName("fromMap: 과거로 거슬러 첫 non-null")
  void fromMap_fallsBack() {
    Map<LocalDate, OHLCPriceDto> map = new HashMap<>();
    map.put(d("2025-05-30"), price("2025-05-30", 48, true));
    assertThat(PriceLookup.fromMap(map, d("2025-06-02"), 5).closePrice()).isEqualByComparingTo("48");
  }

  @Test
  @DisplayName("fromMap: maxDays 내 없으면 null(예외 없음)")
  void fromMap_null() {
    assertThat(PriceLookup.fromMap(new HashMap<>(), d("2025-06-05"), 5)).isNull();
  }

  @Test
  @DisplayName("effectiveClose: closePrice 를 쓴다. 배당 조정된 adjustedClose 가 아니다")
  void effectiveClose_usesClose() {
    // closePrice 는 분할만 조정된 값, adjustedClose 는 배당까지 조정된 값
    OHLCPriceDto p = new OHLCPriceDto("AAPL", d("2025-06-05"),
        BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(100),
        BigDecimal.valueOf(100), BigDecimal.valueOf(90), 1L, "USD", true);
    assertThat(PriceLookup.effectiveClose(p)).isEqualByComparingTo("100");
  }

  @Test
  @DisplayName("effectiveClose: closePrice null이면 adjustedClose fallback")
  void effectiveClose_fallback() {
    OHLCPriceDto p = new OHLCPriceDto("AAPL", d("2025-06-05"),
        BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(100),
        null, BigDecimal.valueOf(90), 1L, "USD", true);
    assertThat(PriceLookup.effectiveClose(p)).isEqualByComparingTo("90");
  }
}
