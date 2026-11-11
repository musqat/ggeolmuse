package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrailingStop 기준가 · 팔 몫")
class TrailingStopTest {

  private static BigDecimal number(String value) {
    return new BigDecimal(value);
  }

  @Test
  @DisplayName("하락 비율을 비우면 크게 내려도 팔지 않는다")
  void disabledWithoutStopRate() {
    TrailingStop stop = TrailingStop.of(null, null);
    stop.open(number("100"), number("100"));

    assertThat(stop.check(number("10"), number("10"), number("1000"))).isNull();
  }

  @Test
  @DisplayName("기준가는 매수가와 산 날 종가 가운데 큰 값에서 시작해 종가 고점으로 오르고, 종가가 기준가 × (1 - 하락 비율) 이하면 팔 비율을 비웠을 때 전량 판다")
  void sellsAllBelowRisingReference() {
    TrailingStop stop = TrailingStop.of(number("0.1"), null);
    stop.open(number("100"), number("110"));

    assertThat(stop.check(number("99.01"), number("10"), number("1000"))).isNull();
    assertThat(stop.check(number("120"), number("10"), number("1000"))).isNull();
    assertThat(stop.check(number("108.01"), number("10"), number("1000"))).isNull();
    assertThat(stop.check(number("108"), number("10"), number("1000")))
      .isEqualTo(new SaleShare(number("10"), number("1000"), true, "고점 대비 하락 매도 10.0%"));
  }

  @Test
  @DisplayName("팔 비율을 주면 보유의 그만큼과 그 몫의 원가를 팔고, 판 값을 새 기준가로 다시 본다")
  void partialSaleResetsReference() {
    TrailingStop stop = TrailingStop.of(number("0.1"), number("0.5"));
    stop.open(number("100"), number("100"));

    SaleShare first = stop.check(number("90"), number("10"), number("1000"));
    stop.sold(number("90"));

    assertThat(first).isEqualTo(new SaleShare(number("5.00000000"), number("500.00000000"), false,
      "고점 대비 하락 매도 10.0%"));
    assertThat(stop.check(number("81.01"), number("5"), number("500"))).isNull();
    assertThat(stop.check(number("81"), number("5"), number("500")))
      .isEqualTo(new SaleShare(number("2.50000000"), number("250.00000000"), false, "고점 대비 하락 매도 10.0%"));
  }

  @Test
  @DisplayName("팔 수량은 보유 × 팔 비율을 소수 여덟째에서 반올림하고, 반올림해 0 주가 되면 팔지 않는다")
  void roundsSharesToEightDecimals() {
    TrailingStop stop = TrailingStop.of(number("0.1"), number("0.123456789"));
    stop.open(number("100"), number("100"));

    assertThat(stop.check(number("90"), number("1"), number("100")))
      .isEqualTo(new SaleShare(number("0.12345679"), number("12.34567900"), false, "고점 대비 하락 매도 10.0%"));
    assertThat(stop.check(number("90"), number("0.00000001"), number("1"))).isNull();
  }

  @Test
  @DisplayName("팔 비율 1 이면 전량이고, 거래 이유는 기준가 대비 하락을 소수 첫째까지 적는다")
  void fullRateAndDropText() {
    TrailingStop stop = TrailingStop.of(number("0.1"), BigDecimal.ONE);
    stop.open(number("100"), number("100"));

    assertThat(stop.check(number("89.66"), number("3.5"), number("350")))
      .isEqualTo(new SaleShare(number("3.5"), number("350"), true, "고점 대비 하락 매도 10.3%"));
  }
}
