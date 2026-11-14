package com.muscat.backtest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OptimalTiming 최적 매매 시점")
class OptimalTimingTest {

  private static final LocalDate D1 = LocalDate.of(2024, 1, 2);
  private static final LocalDate D2 = LocalDate.of(2024, 1, 3);
  private static final LocalDate D3 = LocalDate.of(2024, 1, 4);
  private static final LocalDate D4 = LocalDate.of(2024, 1, 5);
  private static final Function<LocalDate, BigDecimal> FLAT_FX = date -> new BigDecimal("1000");

  private static OHLCPriceDto candle(LocalDate date, String close) {
    BigDecimal c = new BigDecimal(close);
    return new OHLCPriceDto("AAPL", date, c, c, c, c, c, 1L, "USD", true);
  }

  @Test
  @DisplayName("앞서 원화 주가가 가장 낮은 날에 사서 가장 많이 오른 날에 팔고, 매도일이 매수일보다 앞서지 않음")
  void neverSellsBeforeBuying() {
    OptimalTiming timing = OptimalTiming.best(
      List.of(candle(D1, "100"), candle(D2, "80"), candle(D3, "120"), candle(D4, "60")), FLAT_FX);

    assertThat(timing.buyDate()).isEqualTo(D2);
    assertThat(timing.sellDate()).isEqualTo(D3);
    assertThat(timing.buyPrice()).isEqualByComparingTo("80");
    assertThat(timing.sellPrice()).isEqualByComparingTo("120");
    assertThat(timing.returnPercent()).isEqualByComparingTo("50");
  }

  @Test
  @DisplayName("달러 종가가 아니라 종가 × 당일 환율로 고름")
  void krwPrice() {
    Map<LocalDate, BigDecimal> rates = Map.of(D1, new BigDecimal("1500"), D2, new BigDecimal("1200"),
      D3, new BigDecimal("1400"));

    OptimalTiming timing = OptimalTiming.best(
      List.of(candle(D1, "100"), candle(D2, "110"), candle(D3, "105")), rates::get);

    assertThat(timing.buyDate()).isEqualTo(D2);
    assertThat(timing.sellDate()).isEqualTo(D3);
    assertThat(timing.returnPercent()).isEqualByComparingTo("11.36");
  }

  @Test
  @DisplayName("내내 하락이면 매수일 = 매도일 = 첫 날, 수익률 0")
  void fallingAllTheWay() {
    OptimalTiming timing = OptimalTiming.best(
      List.of(candle(D1, "100"), candle(D2, "90"), candle(D3, "80")), FLAT_FX);

    assertThat(timing.buyDate()).isEqualTo(D1);
    assertThat(timing.sellDate()).isEqualTo(D1);
    assertThat(timing.returnPercent()).isEqualByComparingTo("0");
  }
}
