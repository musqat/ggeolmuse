package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator.Payments;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CapitalGainsTaxPayer 다음 해 5월 양도세 납부")
class CapitalGainsTaxPayerTest {

  private static final BigDecimal FX = new BigDecimal("1300");
  private static final LocalDate DUE = LocalDate.of(2025, 5, 2);

  // 새 돈(KRW)으로 산 10주
  private static StrategyTransaction buy(LocalDate date) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(BigDecimal.TEN).price(new BigDecimal("100"))
      .amount(new BigDecimal("1300000")).fxRate(FX).build();
  }

  // 10주를 판 거래(수수료 뺀 판 돈 USD, 1300원)
  private static StrategyTransaction sell(LocalDate date, String proceeds) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(BigDecimal.TEN.negate())
      .price(BigDecimal.ONE).amount(BigDecimal.ZERO).proceeds(new BigDecimal(proceeds)).fxRate(FX).build();
  }

  // 130만원어치 10주를 4100달러에 팔아 403만원 이익, 공제 뒤 22% 33만 6,600원
  private static List<StrategyTransaction> taxedSale() {
    return List.of(buy(LocalDate.of(2024, 1, 15)), sell(LocalDate.of(2024, 3, 15), "4100"));
  }

  @Test
  @DisplayName("판 다음 해 5월 1일 전에는 내지 않고, 그 뒤 첫 거래일에 남은 세금을 그 날짜 환율 달러(센트 반올림)로 낸다")
  void paysOnFirstTradingDayFromMay() {
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(true);

    assertThat(payer.pay(LocalDate.of(2025, 4, 30), taxedSale(), new BigDecimal("4100"), FX, false)).isNull();
    StrategyTransaction payment = payer.pay(DUE, taxedSale(), new BigDecimal("4100"), FX, false);

    assertThat(payment.getDate()).isEqualTo(DUE);
    assertThat(payment.getActualDate()).isEqualTo(DUE);
    assertThat(payment.getTrigger()).isEqualTo("양도세 납부");
    assertThat(payment.getShares()).isEqualByComparingTo("0");
    assertThat(payment.getPrice()).isEqualByComparingTo("0");
    assertThat(payment.getAmount()).isEqualByComparingTo("0");
    assertThat(payment.getFee()).isEqualByComparingTo("0");
    assertThat(payment.getFxRate()).isEqualByComparingTo("1300");
    // 33만 6,600원 ÷ 1300원
    assertThat(payment.getTaxPaid()).isEqualByComparingTo("258.92");
    assertThat(payment.getProceeds()).isNull();
    assertThat(payment.getDefaultFxDate()).isNull();
    assertThat(payment.isTaxPayment()).isTrue();
    assertThat(payment.cashChange()).isEqualByComparingTo("-258.92");
    assertThat(payer.payments()).isEqualTo(new Payments(Map.of(2024, DUE), Map.of(2024, new BigDecimal("336600"))));
    assertThat(payer.pay(DUE.plusDays(3), taxedSale(), new BigDecimal("3841.08"), FX, false)).isNull();
  }

  @Test
  @DisplayName("현금이 모자라면 있는 현금을 다 내고 낸 원화는 원 미만을 버리며, 남은 몫은 현금이 생긴 거래일에 낸다")
  void paysRestWhenCashComes() {
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(true);

    // 200.0055달러 × 1300원 = 26만 7.15원
    StrategyTransaction partial = payer.pay(DUE, taxedSale(), new BigDecimal("200.0055"), FX, false);
    assertThat(payer.pay(DUE.plusDays(3), taxedSale(), BigDecimal.ZERO, FX, false)).isNull();
    StrategyTransaction rest = payer.pay(LocalDate.of(2025, 6, 2), taxedSale(), new BigDecimal("1000"),
      new BigDecimal("1350"), true);

    assertThat(partial.getTaxPaid()).isEqualByComparingTo("200.0055");
    // 남은 7만 6,593원 ÷ 1350원
    assertThat(rest.getTaxPaid()).isEqualByComparingTo("56.74");
    assertThat(rest.getFxRate()).isEqualByComparingTo("1350");
    assertThat(rest.getDefaultFxDate()).isEqualTo(LocalDate.of(2025, 6, 2));
    assertThat(payer.payments()).isEqualTo(new Payments(Map.of(2024, DUE), Map.of(2024, new BigDecimal("336600"))));
  }

  @Test
  @DisplayName("밀린 해가 여럿이면 낸 돈을 오래된 해부터 채운다")
  void oldestYearFirst() {
    // 2024년 · 2025년 모두 33만 6,600원
    List<StrategyTransaction> transactions = List.of(buy(LocalDate.of(2024, 1, 15)), sell(LocalDate.of(2024, 3, 15), "4100"),
      buy(LocalDate.of(2024, 6, 14)), sell(LocalDate.of(2025, 3, 14), "4100"));
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(true);
    LocalDate nextDue = LocalDate.of(2026, 5, 4);

    assertThat(payer.pay(DUE, transactions, BigDecimal.ZERO, FX, false)).isNull();
    // 300달러 × 1300원 = 39만원
    StrategyTransaction payment = payer.pay(nextDue, transactions, new BigDecimal("300"), FX, false);

    assertThat(payment.getTaxPaid()).isEqualByComparingTo("300");
    assertThat(payer.payments()).isEqualTo(new Payments(Map.of(2024, DUE, 2025, nextDue),
      Map.of(2024, new BigDecimal("336600"), 2025, new BigDecimal("53400"))));
  }

  @Test
  @DisplayName("세금이 0 인 해는 내지 않고 납부일만 남긴다")
  void zeroTaxYear() {
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(true);
    List<StrategyTransaction> transactions = List.of(buy(LocalDate.of(2024, 1, 15)), sell(LocalDate.of(2024, 3, 15), "1100"));

    assertThat(payer.pay(DUE, transactions, new BigDecimal("1100"), FX, false)).isNull();
    assertThat(payer.payments()).isEqualTo(new Payments(Map.of(2024, DUE), Map.of()));
  }

  @Test
  @DisplayName("현금이 없거나 원 단위로 낼 만큼 없으면 내지 않는다")
  void noCash() {
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(true);

    assertThat(payer.pay(DUE, taxedSale(), null, FX, false)).isNull();
    assertThat(payer.pay(DUE.plusDays(3), taxedSale(), new BigDecimal("0.0001"), FX, false)).isNull();
    assertThat(payer.payments()).isEqualTo(new Payments(Map.of(2024, DUE), Map.of()));
  }

  @Test
  @DisplayName("양도세를 끄면 내지 않고 납부일도 남기지 않는다")
  void disabled() {
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(false);

    assertThat(payer.pay(DUE, taxedSale(), new BigDecimal("4100"), FX, false)).isNull();
    assertThat(payer.payments()).isEqualTo(Payments.NONE);
  }
}
