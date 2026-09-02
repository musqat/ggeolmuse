package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
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
  @DisplayName("effectiveClose: adjustedClose 있으면 그 값(분할/배당 반영)")
  void effectiveClose_usesAdjusted() {
    // 분할 전 raw 종가 100, 분할 반영 조정종가 25
    OHLCPriceDto p = new OHLCPriceDto("AAPL", d("2025-06-05"),
        BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(100),
        BigDecimal.valueOf(100), BigDecimal.valueOf(25), 1L, "USD", true);
    assertThat(PriceLookup.effectiveClose(p)).isEqualByComparingTo("25");
  }

  @Test
  @DisplayName("effectiveClose: adjustedClose null이면 closePrice fallback")
  void effectiveClose_fallback() {
    OHLCPriceDto p = new OHLCPriceDto("AAPL", d("2025-06-05"),
        BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(100),
        BigDecimal.valueOf(100), null, 1L, "USD", true);
    assertThat(PriceLookup.effectiveClose(p)).isEqualByComparingTo("100");
  }
}
