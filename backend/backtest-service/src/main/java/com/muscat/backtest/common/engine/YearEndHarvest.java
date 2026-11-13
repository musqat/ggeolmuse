package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.constants.BacktestConstants.CapitalGains;
import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

// 연말 정리: 결제가 그해 안에 끝나는 마지막 거래일에 그해 실현 이익을 연 공제에 맞추도록 이익 난 보유분(공제 채우기)이나 손실 난 보유분(손실 상계)을 팔고 판 돈 전부로 같은 값에 다시 삼
public final class YearEndHarvest {

  // 수수료를 센트로 반올림해 판 돈이 늘어도 공제를 넘지 않게 목표에서 빼는 달러
  private static final BigDecimal FEE_ROUNDING_USD = new BigDecimal("0.01");

  // 결제가 T+1 인 첫 거래일(그 앞은 T+2)
  private static final LocalDate T_PLUS_ONE_FROM = LocalDate.of(2024, 5, 28);

  /** 연말 정리로 팔 종목(종목이 없으면 빈 이름) · 주식 수 · 공제 채우기인지 */
  public record Sale(String symbol, BigDecimal shares, boolean gain) {}

  /** 판 거래와 판 돈 전부로 다시 산 거래 */
  public record Trade(StrategyTransaction sale, StrategyTransaction rebuy) {

    // 판 주식과 다시 산 주식을 더한 보유 변화
    public BigDecimal sharesChange() {
      return sale.getShares().add(rebuy.getShares());
    }

    // 판 몫의 원가를 빼고 다시 산 원가를 더한 보유 원가 변화(USD)
    public BigDecimal costChange() {
      return rebuy.costUsd().subtract(sale.getProceeds().subtract(sale.getRealizedProfit()));
    }
  }

  // 종목 하나의 그 날짜 값 · 보유 · 원화 원가 · 수수료 뺀 평가 손익(원화)
  private record Holding(String symbol, BigDecimal price, BigDecimal shares, BigDecimal costKrw, BigDecimal gainKrw) {}

  private YearEndHarvest() {}

  // 양도세와 연말 정리를 둘 다 켰는지
  public static boolean enabled(Boolean capitalGainsTax, Boolean yearEndHarvest) {
    return Boolean.TRUE.equals(capitalGainsTax) && Boolean.TRUE.equals(yearEndHarvest);
  }

  // 쓸 수 있는 일봉 날짜로 본 연말 정리 날
  public static NavigableSet<LocalDate> dates(Map<LocalDate, OHLCPriceDto> priceMap, LocalDate start,
    LocalDate valuationDate) {
    return dates(priceMap.values().stream().filter(PriceLookup::isUsable).map(OHLCPriceDto::date).toList(), start,
      valuationDate);
  }

  // 그해 12월 31일이 평가일 이하인 해마다 결제(일봉 날로 센 T+2 · T+1)가 그해 안에 끝나는 마지막 12월 일봉 날, 시작일 앞이면 뺌
  public static NavigableSet<LocalDate> dates(Collection<LocalDate> candleDates, LocalDate start,
    LocalDate valuationDate) {
    Map<Integer, List<LocalDate>> byYear = candleDates.stream().distinct().sorted()
      .collect(Collectors.groupingBy(LocalDate::getYear, TreeMap::new, Collectors.toList()));
    NavigableSet<LocalDate> dates = new TreeSet<>();
    for (Map.Entry<Integer, List<LocalDate>> year : byYear.entrySet()) {
      List<LocalDate> days = year.getValue();
      if (LocalDate.of(year.getKey(), Month.DECEMBER, 31).isAfter(valuationDate)) {
        continue;
      }
      for (int i = days.size() - 1; i >= 0 && days.get(i).getMonth() == Month.DECEMBER; i--) {
        if (days.size() - 1 - i >= settlementDays(days.get(i))) {
          if (!days.get(i).isBefore(start)) {
            dates.add(days.get(i));
          }
          break;
        }
      }
    }
    return dates;
  }

  // 그 날짜에 판 거래가 결제되기까지 거래일 수
  private static int settlementDays(LocalDate tradeDate) {
    return tradeDate.isBefore(T_PLUS_ONE_FROM) ? 2 : 1;
  }

  // 그 날짜까지 거래의 그해 실현 이익을 공제(수수료가 있으면 1센트 × 환율만큼 아래)에 맞추도록 그 날짜 값이 있는 종목마다 팔 주식 수
  // 공제 채우기는 평가 이익 큰 종목부터, 손실 상계는 평가 손실 큰 종목부터, 판 수는 소수 여덟째에서 내림 · 보유 상한, 남은 목표가 1달러 × 환율 아래면 팔지 않음
  public static List<Sale> plan(LocalDate date, List<StrategyTransaction> transactions, Map<String, BigDecimal> prices,
    BigDecimal feeRate, BigDecimal fxRate) {
    BigDecimal realized = CapitalGainsTaxCalculator.realizedByYear(CapitalGainsTaxCalculator.realizedGains(transactions))
      .getOrDefault(date.getYear(), BigDecimal.ZERO);
    BigDecimal margin = charged(feeRate) ? FEE_ROUNDING_USD.multiply(fxRate) : BigDecimal.ZERO;
    BigDecimal room = CapitalGains.ANNUAL_DEDUCTION_KRW.subtract(margin).subtract(realized);
    if (room.abs().compareTo(fxRate) < 0) {
      return List.of();
    }
    boolean gain = room.signum() > 0;
    List<Holding> candidates = holdings(transactions, prices, feeRate, fxRate).stream()
      .filter(holding -> holding.gainKrw().signum() == room.signum())
      .sorted(Comparator.comparing(Holding::gainKrw, gain ? Comparator.reverseOrder() : Comparator.naturalOrder()))
      .toList();
    List<Sale> sales = new ArrayList<>();
    BigDecimal left = room;
    for (Holding holding : candidates) {
      if (left.signum() != room.signum() || left.abs().compareTo(fxRate) < 0) {
        break;
      }
      BigDecimal shares = left.multiply(holding.shares())
        .divide(holding.gainKrw(), Money.SHARES_SCALE, RoundingMode.DOWN)
        .min(holding.shares());
      if (shares.signum() <= 0) {
        continue;
      }
      sales.add(new Sale(holding.symbol(), shares, gain));
      left = left.subtract(realizedKrw(holding, shares, feeRate, fxRate));
    }
    return sales;
  }

  // 그 날짜 값이 있고 주식이 있는 종목마다 보유 · 원화 원가와 수수료를 뺀 판 돈 × 환율 - 원가
  private static List<Holding> holdings(List<StrategyTransaction> transactions, Map<String, BigDecimal> prices,
    BigDecimal feeRate, BigDecimal fxRate) {
    Map<String, CapitalGainsTaxCalculator.HoldingKrw> held = CapitalGainsTaxCalculator.holdingsKrw(transactions);
    BigDecimal kept = charged(feeRate) ? BigDecimal.ONE.subtract(feeRate) : BigDecimal.ONE;
    List<Holding> holdings = new ArrayList<>();
    prices.forEach((symbol, price) -> {
      CapitalGainsTaxCalculator.HoldingKrw holding = held.get(symbol);
      if (holding != null && holding.shares().signum() > 0) {
        BigDecimal gainKrw = holding.shares().multiply(price).multiply(kept).multiply(fxRate).subtract(holding.costKrw());
        holdings.add(new Holding(symbol, price, holding.shares(), holding.costKrw(), gainKrw));
      }
    });
    return holdings;
  }

  // 정한 주식을 판 거래의 원화 실현 이익(판 돈 × 환율 - 판 몫 원화 원가, 양도세 계산과 같은 반올림)
  private static BigDecimal realizedKrw(Holding holding, BigDecimal shares, BigDecimal feeRate, BigDecimal fxRate) {
    BigDecimal gross = shares.multiply(holding.price()).setScale(8, RoundingMode.HALF_UP);
    BigDecimal proceeds = gross.subtract(BacktestCalculationUtils.calculateTradingFee(gross, feeRate));
    BigDecimal soldCost = holding.costKrw().multiply(shares).divide(holding.shares(), 8, RoundingMode.HALF_UP);
    return proceeds.multiply(fxRate).subtract(soldCost);
  }

  // 종목 하나를 그 날짜 값에 연말 정리한 거래(기본 환율 날이면 그 날짜를 남김, 팔 것이 없으면 null)
  public static Trade single(LocalDate date, List<StrategyTransaction> transactions, BigDecimal price,
    BigDecimal heldShares, BigDecimal heldCostUsd, BigDecimal feeRate, BigDecimal fxRate, boolean defaultFx) {
    List<Sale> sales = plan(date, transactions, Map.of("", price), feeRate, fxRate);
    if (sales.isEmpty()) {
      return null;
    }
    Trade trade = trade(date, sales.getFirst(), price, heldShares, heldCostUsd, feeRate, fxRate);
    if (defaultFx) {
      trade.sale().setDefaultFxDate(date);
      trade.rebuy().setDefaultFxDate(date);
    }
    return trade;
  }

  // 그 날짜 값에 정한 주식을 팔고(매도 수수료 뺌, 판 몫 원가 = 보유 원가 × 판 수 ÷ 보유 수, 전량이면 원가 전부) 판 돈 전부로 다시 산 거래(매수 수수료 뺌)
  public static Trade trade(LocalDate date, Sale sale, BigDecimal price, BigDecimal heldShares, BigDecimal heldCostUsd,
    BigDecimal feeRate, BigDecimal fxRate) {
    boolean all = sale.shares().compareTo(heldShares) >= 0;
    BigDecimal shares = all ? heldShares : sale.shares();
    BigDecimal soldCost = all
      ? heldCostUsd
      : heldCostUsd.multiply(shares).divide(heldShares, Money.SHARES_SCALE, RoundingMode.HALF_UP);
    BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
    BigDecimal sellFee = BacktestCalculationUtils.calculateTradingFee(gross, feeRate);
    BigDecimal proceeds = gross.subtract(sellFee);
    BigDecimal buyFee = BacktestCalculationUtils.calculateTradingFee(proceeds, feeRate);
    StrategyTransaction saleTx = base(date, price, fxRate)
      .shares(shares.negate())
      .fee(sellFee)
      .proceeds(proceeds)
      .realizedProfit(proceeds.subtract(soldCost))
      .trigger(sale.gain() ? TransactionTrigger.HARVEST_GAIN_SELL : TransactionTrigger.HARVEST_LOSS_SELL)
      .build();
    StrategyTransaction rebuy = base(date, price, fxRate)
      .shares(proceeds.subtract(buyFee).divide(price, Money.SHARES_SCALE, RoundingMode.HALF_UP))
      .fee(buyFee)
      .cashUsed(proceeds)
      .trigger(sale.gain() ? TransactionTrigger.HARVEST_GAIN_BUY : TransactionTrigger.HARVEST_LOSS_BUY)
      .build();
    return new Trade(saleTx, rebuy);
  }

  // 그 날짜 · 값 · 환율에 새 돈 없는 거래
  private static StrategyTransaction.StrategyTransactionBuilder base(LocalDate date, BigDecimal price,
    BigDecimal fxRate) {
    return StrategyTransaction.builder()
      .date(date)
      .actualDate(date)
      .price(price)
      .amount(BigDecimal.ZERO)
      .fxRate(fxRate);
  }

  // 수수료율이 0 보다 큰지
  private static boolean charged(BigDecimal feeRate) {
    return feeRate != null && feeRate.signum() > 0;
  }
}
