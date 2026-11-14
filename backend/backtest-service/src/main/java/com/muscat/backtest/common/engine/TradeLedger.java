package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.enums.type.TaxSale;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 모의 계좌: 현금(USD) · 거래 기록과 종목마다 보유 주식 · 원가(USD, 수수료 포함)를 두고 수수료 · 환율을 적용해 사고팔고 양도세를 냄
public final class TradeLedger {

  // 종목 하나의 보유 주식 · 원가(USD, 수수료 포함)와 거래에 싣는 종목
  public static final class Holding {
    private final String symbol;
    private BigDecimal shares = BigDecimal.ZERO;
    private BigDecimal cost = BigDecimal.ZERO;

    // 거래에 종목을 싣지 않는 단일 종목
    public Holding() {
      this(null);
    }

    public Holding(String symbol) {
      this.symbol = symbol;
    }

    public BigDecimal shares() {
      return shares;
    }

    public BigDecimal cost() {
      return cost;
    }

    public boolean held() {
      return shares.signum() > 0;
    }
  }

  private final List<StrategyTransaction> transactions = new ArrayList<>();
  private final BigDecimal feeRate;
  private final BigDecimal manualFxRate;
  private final FxRates fxRates;
  private final boolean capitalGainsTax;
  private BigDecimal cash = BigDecimal.ZERO;
  private StrategyTransaction lastPayment;

  // 수수료율 · 수동 환율(없으면 날마다 환율) · 양도세를 켰는지
  public TradeLedger(BigDecimal feeRate, BigDecimal manualFxRate, FxRates fxRates, boolean capitalGainsTax) {
    this.feeRate = feeRate;
    this.manualFxRate = manualFxRate;
    this.fxRates = fxRates;
    this.capitalGainsTax = capitalGainsTax;
  }

  // 읽기만 하는 거래 기록(마무리에서 더하고 정렬할 목록은 사본으로)
  public List<StrategyTransaction> transactions() {
    return Collections.unmodifiableList(transactions);
  }

  public BigDecimal cash() {
    return cash;
  }

  // 받은 날짜의 환율(수동 환율이 있으면 수동 환율)
  public BigDecimal fxOn(LocalDate date) {
    return manualFxRate != null ? manualFxRate : fxRates.get(date);
  }

  // 기본 환율로 채운 날이면 받은 날짜(아니면 null)
  public LocalDate defaultFxDate(LocalDate date) {
    return fxRates.isDefault(date) ? date : null;
  }

  // 판 날의 기본 환율 날(양도세를 켰을 때만)
  public LocalDate saleDefaultFxDate(LocalDate date) {
    return capitalGainsTax ? defaultFxDate(date) : null;
  }

  // 거래 달러 × 수수료율(USD 두 자리, 수수료율이 없으면 0)
  public BigDecimal fee(BigDecimal usd) {
    return BacktestCalculationUtils.calculateTradingFee(usd, feeRate);
  }

  // 원화를 새로 넣어 받은 날짜 환율 · 가격으로 산 거래(수수료를 뺀 달러로 소수 주)
  public StrategyTransaction buyWithDeposit(Holding holding, LocalDate planned, LocalDate date, BigDecimal price,
    BigDecimal krw, String trigger, TradeReason reason) {
    RoundPurchase purchase = RoundPurchase.byAmount(krw, price, fxOn(date), feeRate);
    return buy(holding, base(holding, planned, date, price, trigger, reason)
      .shares(purchase.shares())
      .fee(purchase.feeUsd())
      .amount(purchase.amountKrw())
      .build());
  }

  // 현금 달러로 받은 가격에 산 거래(수수료는 쓴 달러에서 뺌, 새 돈 없음)
  public StrategyTransaction buyWithCash(Holding holding, LocalDate planned, LocalDate date, BigDecimal price,
    BigDecimal usd, String trigger, TradeReason reason) {
    BigDecimal fee = fee(usd);
    return buy(holding, base(holding, planned, date, price, trigger, reason)
      .shares(usd.subtract(fee).divide(price, 8, RoundingMode.HALF_UP))
      .fee(fee)
      .amount(BigDecimal.ZERO)
      .cashUsed(usd)
      .build());
  }

  // 산 거래를 남기고 쓴 현금을 빼고 보유 · 원가에 더함
  public StrategyTransaction buy(Holding holding, StrategyTransaction buy) {
    transactions.add(buy);
    if (buy.getCashUsed() != null) {
      cash = cash.subtract(buy.getCashUsed());
    }
    holding.shares = holding.shares.add(buy.getShares());
    holding.cost = holding.cost.add(buy.costUsd());
    return buy;
  }

  // 받은 날짜 · 가격으로 정한 주식을 팔아 매도 수수료를 뺀 판 돈을 현금에 넣고 판 몫 원가 대비 실현 손익을 남긴 거래
  public StrategyTransaction sell(Holding holding, LocalDate planned, LocalDate date, BigDecimal price,
    BigDecimal shares, BigDecimal soldCost, String trigger, TradeReason reason) {
    BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
    BigDecimal fee = fee(gross);
    BigDecimal proceeds = gross.subtract(fee);
    StrategyTransaction sale = StrategyTransaction.builder()
      .symbol(holding.symbol)
      .reason(reason)
      .date(planned)
      .actualDate(date)
      .price(price)
      .shares(shares.negate())
      .fee(fee)
      .amount(BigDecimal.ZERO)
      .proceeds(proceeds)
      .realizedProfit(proceeds.subtract(soldCost))
      .fxRate(fxOn(date))
      .defaultFxDate(saleDefaultFxDate(date))
      .trigger(trigger)
      .build();
    transactions.add(sale);
    cash = cash.add(proceeds);
    holding.shares = holding.shares.subtract(shares);
    holding.cost = holding.cost.subtract(soldCost);
    return sale;
  }

  // 받은 배당(USD)을 현금에 넣음
  public void receiveDividend(BigDecimal usd) {
    cash = cash.add(usd);
  }

  // 받은 날짜까지 납부일이 온 양도세를 현금에서 낸 거래(낼 것이 없으면 null), 같은 날 또 내면 같은 날 납부 줄에 더함
  public StrategyTransaction payTax(CapitalGainsTaxPayer taxPayer, LocalDate date, TradeReason reason) {
    StrategyTransaction payment = taxPayer.pay(date, transactions, cash, fxOn(date), fxRates.isDefault(date));
    if (payment == null) {
      return null;
    }
    if (lastPayment != null && date.equals(lastPayment.getDate())) {
      lastPayment.setTaxPaid(lastPayment.getTaxPaid().add(payment.getTaxPaid()));
    } else {
      payment.setReason(reason);
      transactions.add(payment);
      lastPayment = payment;
    }
    cash = cash.subtract(payment.getTaxPaid());
    return payment;
  }

  // 받은 날짜까지 납부일이 온 양도세를 냄(taxSale 이 팔기면 모자란 현금만큼 먼저 받은 가격에 판 거래를 돌려줌, 안 팔면 null)
  public StrategyTransaction sellAndPayTax(CapitalGainsTaxPayer taxPayer, TaxSale taxSale, Holding holding,
    LocalDate date, BigDecimal price) {
    StrategyTransaction sale = TaxSale.sells(taxSale) ? sellForTax(taxPayer, holding, date, price) : null;
    payTax(taxPayer, date, null);
    return sale;
  }

  // 양도세에 모자란 달러만큼 받은 가격에 판 거래(보유 상한, 판 몫 원가는 보유 원가 × 판 수 ÷ 보유 수, 모자라지 않거나 보유가 없으면 null)
  private StrategyTransaction sellForTax(CapitalGainsTaxPayer taxPayer, Holding holding, LocalDate date,
    BigDecimal price) {
    BigDecimal shortfall = taxShortfall(taxPayer, date);
    if (shortfall.signum() <= 0 || !holding.held()) {
      return null;
    }
    BigDecimal shares = TaxSale.shares(shortfall, price, feeRate, holding.shares);
    boolean all = shares.compareTo(holding.shares) >= 0;
    BigDecimal sold = all ? holding.shares : shares;
    BigDecimal soldCost = all
      ? holding.cost
      : holding.cost.multiply(shares).divide(holding.shares, Money.SHARES_SCALE, RoundingMode.HALF_UP);
    return sell(holding, date, date, price, sold, soldCost, TransactionTrigger.TAX_SALE, null);
  }

  // 받은 날짜까지 납부일이 온 양도세에 모자란 현금(USD)
  public BigDecimal taxShortfall(CapitalGainsTaxPayer taxPayer, LocalDate date) {
    return taxPayer.shortfallUsd(date, transactions, cash, fxOn(date));
  }

  // 연말 정리로 판 거래 · 판 돈 전부로 다시 산 거래를 남기고 보유 · 원가를 바꿈(현금은 그대로)
  public void harvest(Holding holding, YearEndHarvest.Trade trade) {
    transactions.add(trade.sale());
    transactions.add(trade.rebuy());
    holding.shares = holding.shares.add(trade.sharesChange());
    holding.cost = holding.cost.add(trade.costChange());
  }

  // 단일 종목 연말 정리(모의 계좌 수수료율 · 받은 날짜 환율로 정한 정리 매도 · 다시 산 거래, 보유가 없거나 정리할 것이 없으면 그대로)
  public void harvestSingle(Holding holding, LocalDate date, BigDecimal price) {
    YearEndHarvest.Trade trade = holding.held()
      ? YearEndHarvest.single(date, transactions, price, holding.shares, holding.cost, feeRate, fxOn(date),
        fxRates.isDefault(date))
      : null;
    if (trade != null) {
      harvest(holding, trade);
    }
  }

  // 받은 날짜 환율 · 기본 환율 날을 채운 거래
  private StrategyTransaction.StrategyTransactionBuilder base(Holding holding, LocalDate planned, LocalDate date,
    BigDecimal price, String trigger, TradeReason reason) {
    return StrategyTransaction.builder()
      .symbol(holding.symbol)
      .reason(reason)
      .date(planned)
      .actualDate(date)
      .price(price)
      .fxRate(fxOn(date))
      .defaultFxDate(defaultFxDate(date))
      .trigger(trigger);
  }
}
