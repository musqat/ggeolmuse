package com.muscat.backtest.common.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator.Gain;
import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator.Payments;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CapitalGainsTaxCalculator 연도별 양도세 · 납부와 세후 평가")
class CapitalGainsTaxCalculatorTest {

  private static final LocalDate JAN = LocalDate.of(2024, 1, 15);
  private static final LocalDate MAR = LocalDate.of(2024, 3, 15);
  private static final LocalDate MAY = LocalDate.of(2024, 5, 15);
  private static final LocalDate JUL = LocalDate.of(2024, 7, 15);

  // 새 돈(KRW)으로 산 거래
  private static StrategyTransaction buy(LocalDate date, String shares, String price, String amount, String fx) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(new BigDecimal(shares))
      .price(new BigDecimal(price)).amount(new BigDecimal(amount)).fxRate(new BigDecimal(fx)).build();
  }

  // 판 거래(수수료 뺀 판 돈 USD)
  private static StrategyTransaction sell(LocalDate date, String shares, String proceeds, String fx) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(new BigDecimal(shares).negate())
      .price(BigDecimal.ONE).amount(BigDecimal.ZERO).proceeds(new BigDecimal(proceeds)).fxRate(new BigDecimal(fx))
      .build();
  }

  private static StrategyTransaction symbol(String symbol, StrategyTransaction tx) {
    tx.setSymbol(symbol);
    return tx;
  }

  private static List<BigDecimal> amounts(List<Gain> gains) {
    return gains.stream().map(Gain::amountKrw).toList();
  }

  @Nested
  @DisplayName("판 거래의 원화 실현 이익")
  class RealizedGains {

    @Test
    @DisplayName("판 날 환율로 바꾼 판 돈에서 산 날 넣은 원화 원가를 뺀다")
    void saleKrwMinusCost() {
      // 1300원에 130만원어치 10주, 1400원일 때 1200달러에 판매
      List<Gain> gains = CapitalGainsTaxCalculator.realizedGains(List.of(
        buy(JAN, "10", "100", "1300000", "1300"), sell(MAR, "10", "1200", "1400")));

      assertThat(gains).extracting(Gain::date).containsExactly(MAR);
      assertThat(amounts(gains)).usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("380000"));
    }

    @Test
    @DisplayName("판 돈으로 다시 산 주식은 쓴 판 돈을 그 날짜 환율로 바꿔 원가에 더한다")
    void cashRebuyCostAtBuyFx() {
      // 1300원에 1100달러 판매(이익 13만원), 1350원일 때 판 돈 1100달러 + 새 돈 13만 5천원으로 12주, 1350원에 1080달러 판매
      StrategyTransaction rebuy = StrategyTransaction.builder().date(MAY).actualDate(MAY).shares(new BigDecimal("12"))
        .price(new BigDecimal("100")).amount(new BigDecimal("135000")).cashUsed(new BigDecimal("1100"))
        .fxRate(new BigDecimal("1350")).build();

      List<Gain> gains = CapitalGainsTaxCalculator.realizedGains(List.of(
        buy(JAN, "10", "100", "1300000", "1300"), sell(MAR, "10", "1100", "1300"), rebuy, sell(JUL, "12", "1080", "1350")));

      // 145만 8천원 - (13만 5천원 + 1100달러 × 1350원)
      assertThat(amounts(gains)).usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("130000"), new BigDecimal("-162000"));
    }

    @Test
    @DisplayName("일부만 팔면 보유분 평균 원가만큼 덜어 내고 남은 주식의 원가는 다음 매도에 쓴다")
    void partialSaleAverageCost() {
      // 10주 130만원 + 10주 150만원(평균 14만원), 5주 판매 뒤 나머지 15주 판매
      List<Gain> gains = CapitalGainsTaxCalculator.realizedGains(List.of(
        buy(JAN, "10", "100", "1300000", "1300"), buy(MAR, "10", "100", "1500000", "1500"),
        sell(MAY, "5", "600", "1400"), sell(JUL, "15", "1800", "1400")));

      // 84만원 - 70만원, 252만원 - 210만원
      assertThat(amounts(gains)).usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("140000"), new BigDecimal("420000"));
    }

    @Test
    @DisplayName("배당 재투자로 산 주식은 주식 수 × 주가를 그 날짜 환율로 바꾼 값을 원가로 본다")
    void reinvestedSharesCost() {
      StrategyTransaction reinvest = StrategyTransaction.builder().date(MAR).actualDate(MAR).shares(new BigDecimal("0.5"))
        .price(new BigDecimal("100")).amount(BigDecimal.ZERO).fxRate(new BigDecimal("1400")).trigger("배당 재투자")
        .build();

      List<Gain> gains = CapitalGainsTaxCalculator.realizedGains(List.of(
        buy(JAN, "10", "100", "1300000", "1300"), reinvest, sell(JUL, "10.5", "1260", "1400")));

      // 176만 4천원 - (130만원 + 7만원)
      assertThat(amounts(gains)).usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("394000"));
    }

    @Test
    @DisplayName("거래 목록 순서가 아니라 거래일 순서로 보유를 센다")
    void ordersByTradeDate() {
      List<Gain> gains = CapitalGainsTaxCalculator.realizedGains(List.of(
        sell(MAR, "10", "1200", "1400"), buy(JAN, "10", "100", "1300000", "1300")));

      assertThat(amounts(gains)).usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("380000"));
    }

    @Test
    @DisplayName("양도세 납부 줄은 보유 주식 · 원가에 넣지 않는다")
    void skipsTaxPayments() {
      StrategyTransaction payment = StrategyTransaction.builder().date(MAR).actualDate(MAR).shares(BigDecimal.ZERO)
        .price(BigDecimal.ZERO).amount(BigDecimal.ZERO).fee(BigDecimal.ZERO).fxRate(new BigDecimal("1400"))
        .taxPaid(new BigDecimal("100")).trigger("양도세 납부").build();

      List<Gain> gains = CapitalGainsTaxCalculator.realizedGains(List.of(
        buy(JAN, "10", "100", "1300000", "1300"), payment, sell(JUL, "10", "1200", "1400")));

      assertThat(gains).extracting(Gain::date).containsExactly(JUL);
      assertThat(amounts(gains)).usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("380000"));
    }

    @Test
    @DisplayName("종목이 있는 거래는 종목마다 보유 · 평균 원가를 따로 센다")
    void costPerSymbol() {
      // AAA 10주 130만원 · BBB 10주 260만원, AAA 10주를 1400원일 때 1200달러에 판매
      List<Gain> gains = CapitalGainsTaxCalculator.realizedGains(List.of(
        symbol("AAA", buy(JAN, "10", "100", "1300000", "1300")), symbol("BBB", buy(JAN, "10", "200", "2600000", "1300")),
        symbol("AAA", sell(MAR, "10", "1200", "1400"))));

      assertThat(amounts(gains)).usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("380000"));
    }

    @Test
    @DisplayName("판 거래가 없으면 빈 목록이다")
    void noSales() {
      assertThat(CapitalGainsTaxCalculator.realizedGains(List.of(buy(JAN, "10", "100", "1300000", "1300")))).isEmpty();
    }
  }

  @Nested
  @DisplayName("연도별 공제 · 세금 · 납부와 세후 평가")
  class Yearly {

    private static final Payments NONE = Payments.NONE;

    private Gain gain(int year, int month, String amount) {
      return new Gain(LocalDate.of(year, month, 1), new BigDecimal(amount));
    }

    // 기간 안 납부가 없는 해
    private CapitalGainsTax.Year unpaid(int year, String realized, String deduction, String tax) {
      return new CapitalGainsTax.Year(year, new BigDecimal(realized), new BigDecimal(deduction), new BigDecimal(tax),
        null, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("해마다 1월 ~ 12월 이익을 합해 250만원까지 공제하고 남은 이익의 22% 를 세금으로 계산한다")
    void yearlyDeductionAndRate() {
      CapitalGainsTax tax = CapitalGainsTaxCalculator.of(List.of(
          gain(2024, 2, "5000000"), gain(2024, 11, "-1000000"), gain(2025, 6, "2000000")), NONE,
        new BigDecimal("10000000"), new BigDecimal("8000000"), new BigDecimal("8000000"));

      assertThat(tax.years()).containsExactly(unpaid(2024, "4000000", "2500000", "330000"),
        unpaid(2025, "2000000", "2000000", "0"));
      assertThat(tax.totalTaxKrw()).isEqualByComparingTo("330000");
      assertThat(tax.paidTaxKrw()).isEqualByComparingTo("0");
      assertThat(tax.unpaidTaxKrw()).isEqualByComparingTo("330000");
      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("9670000");
      // 967만원 / 800만원
      assertThat(tax.afterTaxReturnPercent()).isEqualByComparingTo("20.88");
    }

    @Test
    @DisplayName("기간 안에 낸 양도세는 해마다 납부일 · 낸 금액으로 싣고 남은 양도세만 세후 총 평가에서 뺀다")
    void paidTaxLeavesOnlyUnpaid() {
      LocalDate due = LocalDate.of(2025, 5, 2);
      Payments payments = new Payments(Map.of(2024, due), Map.of(2024, new BigDecimal("200000")));

      CapitalGainsTax tax = CapitalGainsTaxCalculator.of(List.of(
          gain(2024, 2, "4000000"), gain(2025, 6, "3000000")), payments,
        new BigDecimal("10000000"), new BigDecimal("8000000"), new BigDecimal("8000000"));

      assertThat(tax.years()).containsExactly(
        new CapitalGainsTax.Year(2024, new BigDecimal("4000000"), new BigDecimal("2500000"), new BigDecimal("330000"),
          due, new BigDecimal("200000")),
        unpaid(2025, "3000000", "2500000", "110000"));
      assertThat(tax.totalTaxKrw()).isEqualByComparingTo("440000");
      assertThat(tax.paidTaxKrw()).isEqualByComparingTo("200000");
      // 2024년에 못 낸 13만원 + 2025년 11만원
      assertThat(tax.unpaidTaxKrw()).isEqualByComparingTo("240000");
      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("9760000");
      assertThat(tax.afterTaxReturnPercent()).isEqualByComparingTo("22.00");
    }

    @Test
    @DisplayName("현금은 원 단위로 반올림한 총 자산에서 주식 평가를 뺀 값이다")
    void cashAndStock() {
      CapitalGainsTax tax = CapitalGainsTaxCalculator.of(List.of(), NONE,
        new BigDecimal("9670000.6"), new BigDecimal("8470000.4"), new BigDecimal("8000000"));

      assertThat(tax.stockKrw()).isEqualByComparingTo("8470000");
      assertThat(tax.cashKrw()).isEqualByComparingTo("1200001");
      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("9670001");
    }

    @Test
    @DisplayName("손실은 다음 해로 넘기지 않고 그해 공제 · 세금은 0 이다")
    void lossNotCarriedOver() {
      CapitalGainsTax tax = CapitalGainsTaxCalculator.of(List.of(gain(2024, 3, "-3000000"), gain(2025, 3, "3000000")),
        NONE, new BigDecimal("10000000"), BigDecimal.ZERO, new BigDecimal("10000000"));

      assertThat(tax.years()).containsExactly(unpaid(2024, "-3000000", "0", "0"),
        unpaid(2025, "3000000", "2500000", "110000"));
      assertThat(tax.totalTaxKrw()).isEqualByComparingTo("110000");
    }

    @Test
    @DisplayName("이익은 원 단위로 반올림하고 세금은 원 미만을 버린다")
    void rounding() {
      CapitalGainsTax tax = CapitalGainsTaxCalculator.of(List.of(gain(2024, 3, "2500011.6")), NONE,
        new BigDecimal("5000000.6"), BigDecimal.ZERO, new BigDecimal("5000000"));

      // 12원 × 22% = 2.64원
      assertThat(tax.years()).containsExactly(unpaid(2024, "2500012", "2500000", "2"));
      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("4999999");
    }

    @Test
    @DisplayName("판 거래가 없으면 연도 없이 세금 0 이고 세후 평가 · 수익률은 총 자산 · 총 수익률과 같다")
    void noGains() {
      CapitalGainsTax tax = CapitalGainsTaxCalculator.of(List.of(), NONE, new BigDecimal("1300000.4"),
        new BigDecimal("1300000.4"), new BigDecimal("1000000"));

      assertThat(tax.years()).isEmpty();
      assertThat(tax.totalTaxKrw()).isEqualByComparingTo("0");
      assertThat(tax.unpaidTaxKrw()).isEqualByComparingTo("0");
      assertThat(tax.cashKrw()).isEqualByComparingTo("0");
      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("1300000");
      assertThat(tax.afterTaxReturnPercent()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("기준 금액이 0 인 매수 0회 결과는 세후 수익률이 0 이다")
    void zeroBasis() {
      CapitalGainsTax tax = CapitalGainsTaxCalculator.of(List.of(), NONE, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO);

      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("0");
      assertThat(tax.afterTaxReturnPercent()).isEqualByComparingTo("0");
    }
  }
}
