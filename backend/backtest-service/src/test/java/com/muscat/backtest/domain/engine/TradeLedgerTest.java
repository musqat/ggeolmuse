package com.muscat.backtest.domain.engine;

import com.muscat.backtest.common.enums.type.TransactionKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.enums.type.TaxSale;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TradeLedger 현금 · 보유 · 거래 기록")
class TradeLedgerTest {

  private static final LocalDate MARKET = LocalDate.of(2024, 3, 4);
  private static final LocalDate DEFAULTED = LocalDate.of(2024, 3, 5);
  private static final FxRates FX_RATES = new FxRates(
    Map.of(MARKET, new BigDecimal("1300"), DEFAULTED, new BigDecimal("1350")), Set.of(DEFAULTED));
  private static final BigDecimal FEE_RATE = new BigDecimal("0.001");

  // 130만원어치 10주(원가 1000달러)
  private static StrategyTransaction deposit(LocalDate date, int shares, String price) {
    return StrategyTransaction.builder().kind((BigDecimal.valueOf(shares)).signum() < 0 ? TransactionKind.SELL : TransactionKind.BUY).date(date).actualDate(date).price(new BigDecimal(price))
      .shares(BigDecimal.valueOf(shares)).amount(new BigDecimal("1300000")).fxRate(new BigDecimal("1300")).build();
  }

  @Test
  @DisplayName("환율은 거래일 환율, 수동 환율이 있으면 수동 환율이고 기본 환율로 채운 날만 날짜를 남김")
  void fxRates() {
    TradeLedger market = new TradeLedger(FEE_RATE, null, FX_RATES, false);
    TradeLedger manual = new TradeLedger(FEE_RATE, new BigDecimal("1250"), FxRates.EMPTY, false);

    assertThat(market.fxOn(MARKET)).isEqualByComparingTo("1300");
    assertThat(market.defaultFxDate(MARKET)).isNull();
    assertThat(market.defaultFxDate(DEFAULTED)).isEqualTo(DEFAULTED);
    assertThat(manual.fxOn(DEFAULTED)).isEqualByComparingTo("1250");
    assertThat(manual.defaultFxDate(DEFAULTED)).isNull();
  }

  @Test
  @DisplayName("거래 기록은 읽기만 하는 목록이라 받은 쪽이 더하거나 정렬할 수 없음")
  void transactionsReadOnly() {
    TradeLedger ledger = new TradeLedger(FEE_RATE, null, FX_RATES, false);
    ledger.buy(new TradeLedger.Holding(), deposit(MARKET, 10, "100"));

    assertThatThrownBy(() -> ledger.transactions().add(deposit(MARKET, 1, "100")))
      .isInstanceOf(UnsupportedOperationException.class);
    assertThat(ledger.transactions()).hasSize(1);
  }

  @Test
  @DisplayName("수수료는 거래 달러 × 수수료율을 센트로 반올림, 수수료율이 없으면 0")
  void fee() {
    assertThat(new TradeLedger(new BigDecimal("0.0025"), null, FxRates.EMPTY, false).fee(new BigDecimal("1234.56")))
      .isEqualTo(new BigDecimal("3.09"));
    assertThat(new TradeLedger(null, null, FxRates.EMPTY, false).fee(new BigDecimal("1234.56")))
      .isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("원화로 산 거래는 수수료를 뺀 달러로 소수 주를 사고 현금은 그대로, 보유 · 원가만 늘림")
  void buyWithDeposit() {
    TradeLedger ledger = new TradeLedger(FEE_RATE, null, FX_RATES, false);
    TradeLedger.Holding holding = new TradeLedger.Holding();

    StrategyTransaction buy = ledger.buyWithDeposit(holding, MARKET.minusDays(2), MARKET, new BigDecimal("100"),
      new BigDecimal("1300000"), "월정액", null);

    assertThat(buy.getDate()).isEqualTo(MARKET.minusDays(2));
    assertThat(buy.getActualDate()).isEqualTo(MARKET);
    assertThat(buy.getShares()).isEqualByComparingTo("9.99");
    assertThat(buy.getFee()).isEqualByComparingTo("1.00");
    assertThat(buy.getAmount()).isEqualByComparingTo("1300000");
    assertThat(buy.getCashUsed()).isNull();
    assertThat(buy.getFxRate()).isEqualByComparingTo("1300");
    assertThat(buy.getTrigger()).isEqualTo("월정액");
    assertThat(ledger.transactions()).containsExactly(buy);
    assertThat(ledger.cash()).isEqualByComparingTo("0");
    assertThat(holding.shares()).isEqualByComparingTo("9.99");
    assertThat(holding.cost()).isEqualByComparingTo("1000");
    assertThat(holding.held()).isTrue();
  }

  @Test
  @DisplayName("판 거래는 매도 수수료를 뺀 판 돈을 현금에 넣고 판 몫 원가 대비 실현 손익을 남기며 보유 · 원가를 판 몫만큼 줄임")
  void sell() {
    TradeLedger ledger = new TradeLedger(FEE_RATE, null, FX_RATES, false);
    TradeLedger.Holding holding = new TradeLedger.Holding();
    ledger.buy(holding, deposit(MARKET, 10, "100"));

    StrategyTransaction sale = ledger.sell(holding, MARKET, DEFAULTED, new BigDecimal("120"), new BigDecimal("4"),
      new BigDecimal("400"), "익절", null);

    assertThat(sale.getDate()).isEqualTo(MARKET);
    assertThat(sale.getActualDate()).isEqualTo(DEFAULTED);
    assertThat(sale.getShares()).isEqualByComparingTo("-4");
    // 480달러 × 0.1%
    assertThat(sale.getFee()).isEqualByComparingTo("0.48");
    assertThat(sale.getProceeds()).isEqualByComparingTo("479.52");
    assertThat(sale.getRealizedProfit()).isEqualByComparingTo("79.52");
    assertThat(sale.getAmount()).isEqualByComparingTo("0");
    assertThat(sale.getFxRate()).isEqualByComparingTo("1350");
    assertThat(sale.getDefaultFxDate()).isNull();
    assertThat(ledger.saleDefaultFxDate(DEFAULTED)).isNull();
    assertThat(sale.getSymbol()).isNull();
    assertThat(ledger.cash()).isEqualByComparingTo("479.52");
    assertThat(holding.shares()).isEqualByComparingTo("6");
    assertThat(holding.cost()).isEqualByComparingTo("600");
  }

  @Test
  @DisplayName("양도세를 켜면 기본 환율 날에 판 거래에 날짜를 남기고 종목 줄은 종목 · 이유를 실음")
  void sellOnDefaultFxDay() {
    TradeLedger ledger = new TradeLedger(FEE_RATE, null, FX_RATES, true);
    TradeLedger.Holding holding = new TradeLedger.Holding("SPY");
    ledger.buy(holding, deposit(MARKET, 10, "100"));

    StrategyTransaction sale = ledger.sell(holding, DEFAULTED, DEFAULTED, new BigDecimal("120"), BigDecimal.TEN,
      new BigDecimal("1000"), "10.0%익절", TradeReason.TAKE_PROFIT);

    assertThat(sale.getDefaultFxDate()).isEqualTo(DEFAULTED);
    assertThat(ledger.saleDefaultFxDate(DEFAULTED)).isEqualTo(DEFAULTED);
    assertThat(sale.getSymbol()).isEqualTo("SPY");
    assertThat(sale.getReason()).isEqualTo(TradeReason.TAKE_PROFIT);
    assertThat(holding.held()).isFalse();
    assertThat(holding.cost()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("현금으로 산 거래는 쓴 달러에서 수수료를 빼고 사며 쓴 달러만큼 현금을 줄이고 원가에 더함")
  void buyWithCash() {
    TradeLedger ledger = new TradeLedger(FEE_RATE, null, FX_RATES, false);
    TradeLedger.Holding holding = new TradeLedger.Holding();
    ledger.buy(holding, deposit(MARKET, 10, "100"));
    ledger.sell(holding, MARKET, MARKET, new BigDecimal("120"), new BigDecimal("4"), new BigDecimal("400"), "익절",
      null);

    StrategyTransaction buy = ledger.buyWithCash(holding, MARKET, MARKET, new BigDecimal("100"), ledger.cash(),
      "골든 크로스", null);

    assertThat(buy.getFee()).isEqualByComparingTo("0.48");
    assertThat(buy.getShares()).isEqualByComparingTo("4.7904");
    assertThat(buy.getAmount()).isEqualByComparingTo("0");
    assertThat(buy.getCashUsed()).isEqualByComparingTo("479.52");
    assertThat(ledger.cash()).isEqualByComparingTo("0");
    assertThat(holding.shares()).isEqualByComparingTo("10.7904");
    assertThat(holding.cost()).isEqualByComparingTo("1079.52");
  }

  @Test
  @DisplayName("양도세는 현금에서 내고 이번에 낸 달러를 돌려주며 같은 날 또 내면 같은 날 납부 줄에 더함")
  void payTax() {
    TradeLedger ledger = new TradeLedger(null, new BigDecimal("1300"), FxRates.EMPTY, true);
    TradeLedger.Holding holding = new TradeLedger.Holding();
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(true);
    ledger.buy(holding, deposit(LocalDate.of(2024, 1, 15), 10, "100"));
    // 130만원어치 10주를 4100달러에 팔아 403만원 이익, 공제 뒤 22% 33만 6,600원(258.92달러)
    ledger.sell(holding, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 15), new BigDecimal("410"), BigDecimal.TEN,
      new BigDecimal("1000"), "익절", null);
    ledger.buy(holding, StrategyTransaction.builder().kind(TransactionKind.BUY).date(LocalDate.of(2024, 6, 3)).actualDate(LocalDate.of(2024, 6, 3))
      .price(new BigDecimal("100")).shares(new BigDecimal("40")).amount(BigDecimal.ZERO).cashUsed(new BigDecimal("4000"))
      .fxRate(new BigDecimal("1300")).build());
    LocalDate due = LocalDate.of(2025, 5, 2);

    assertThat(ledger.taxShortfall(payer, due)).isEqualByComparingTo("158.92");
    assertThat(ledger.payTax(payer, due, TradeReason.TAX_PAYMENT)).isEqualByComparingTo("100");
    ledger.sell(holding, due, due, new BigDecimal("100"), BigDecimal.TWO, new BigDecimal("200"), "양도세 납부용 매도", null);
    assertThat(ledger.payTax(payer, due, TradeReason.TAX_PAYMENT)).isEqualByComparingTo("158.92");

    assertThat(ledger.transactions()).filteredOn(StrategyTransaction::isTaxPayment).singleElement()
      .satisfies(payment -> {
        assertThat(payment.getTaxPaid()).isEqualByComparingTo("258.92");
        assertThat(payment.getReason()).isEqualTo(TradeReason.TAX_PAYMENT);
      });
    assertThat(ledger.transactions().get(3).isTaxPayment()).isTrue();
    assertThat(ledger.cash()).isEqualByComparingTo("41.08");
    assertThat(ledger.payTax(payer, due.plusDays(1), TradeReason.TAX_PAYMENT)).isEqualByComparingTo("0");
  }

  // 403만원 이익을 낸 뒤 40주 · 현금 100달러가 남은 모의 계좌(2025-05-02 납부일에 양도세 258.92달러)
  private static TradeLedger taxDue(TradeLedger.Holding holding) {
    TradeLedger ledger = new TradeLedger(null, new BigDecimal("1300"), FxRates.EMPTY, true);
    ledger.buy(holding, deposit(LocalDate.of(2024, 1, 15), 10, "100"));
    ledger.sell(holding, LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 15), new BigDecimal("410"), BigDecimal.TEN,
      new BigDecimal("1000"), "익절", null);
    ledger.buyWithCash(holding, LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 3), new BigDecimal("100"),
      new BigDecimal("4000"), "매수", null);
    return ledger;
  }

  @Test
  @DisplayName("양도세 팔기면 모자란 현금만큼 받은 가격에 팔아 내고, 팔기가 아니면 있는 현금만 냄")
  void sellAndPayTax() {
    LocalDate due = LocalDate.of(2025, 5, 2);
    TradeLedger.Holding selling = new TradeLedger.Holding();
    TradeLedger ledger = taxDue(selling);

    StrategyTransaction sale = ledger.sellAndPayTax(new CapitalGainsTaxPayer(true), TaxSale.SELL, selling, due,
      new BigDecimal("100"));

    assertThat(sale.getTrigger()).isEqualTo(TransactionTrigger.TAX_SALE);
    assertThat(selling.shares()).isEqualByComparingTo("38.4108");
    assertThat(selling.cost()).isEqualByComparingTo("3841.08");
    assertThat(ledger.cash()).isEqualByComparingTo("0");

    TradeLedger.Holding keeping = new TradeLedger.Holding();
    TradeLedger cashOnly = taxDue(keeping);
    CapitalGainsTaxPayer payer = new CapitalGainsTaxPayer(true);

    assertThat(cashOnly.sellAndPayTax(payer, TaxSale.NONE, keeping, due, new BigDecimal("100"))).isNull();
    assertThat(keeping.shares()).isEqualByComparingTo("40");
    assertThat(cashOnly.taxShortfall(payer, due)).isEqualByComparingTo("158.92");
  }

  @Test
  @DisplayName("단일 종목 연말 정리는 모의 계좌 수수료율 · 환율로 정한 정리 매도 · 다시 산 거래를 남기고 보유가 없으면 그대로")
  void harvestSingle() {
    TradeLedger ledger = new TradeLedger(FEE_RATE, null, FX_RATES, true);
    TradeLedger.Holding holding = new TradeLedger.Holding();
    ledger.harvestSingle(holding, DEFAULTED, new BigDecimal("150"));
    assertThat(ledger.transactions()).isEmpty();

    ledger.buy(holding, deposit(MARKET, 10, "100"));
    YearEndHarvest.Trade expected = YearEndHarvest.single(DEFAULTED, ledger.transactions(), new BigDecimal("150"),
      holding.shares(), holding.cost(), FEE_RATE, new BigDecimal("1350"), true);

    ledger.harvestSingle(holding, DEFAULTED, new BigDecimal("150"));

    assertThat(ledger.transactions()).hasSize(3).endsWith(expected.sale(), expected.rebuy());
  }

  @Test
  @DisplayName("받은 배당은 현금에 넣고 보유 · 거래 기록은 그대로")
  void receiveDividend() {
    TradeLedger ledger = new TradeLedger(null, null, FX_RATES, false);

    ledger.receiveDividend(new BigDecimal("12.34"));

    assertThat(ledger.cash()).isEqualByComparingTo("12.34");
    assertThat(ledger.transactions()).isEmpty();
  }

  @Test
  @DisplayName("연말 정리는 판 거래와 다시 산 거래를 남기고 보유 · 원가를 바꾸며 현금은 그대로")
  void harvest() {
    TradeLedger ledger = new TradeLedger(null, null, FX_RATES, true);
    TradeLedger.Holding holding = new TradeLedger.Holding();
    ledger.buy(holding, deposit(MARKET, 10, "100"));
    YearEndHarvest.Trade trade = YearEndHarvest.trade(MARKET, new YearEndHarvest.Sale("", new BigDecimal("4"), true),
      new BigDecimal("150"), holding.shares(), holding.cost(), null, new BigDecimal("1300"));

    ledger.harvest(holding, trade);

    assertThat(ledger.transactions()).containsSequence(trade.sale(), trade.rebuy());
    assertThat(holding.shares()).isEqualByComparingTo("10");
    assertThat(holding.cost()).isEqualByComparingTo("1200");
    assertThat(ledger.cash()).isEqualByComparingTo("0");
  }
}
