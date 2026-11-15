package com.muscat.backtest.domain.engine;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.domain.calculation.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.response.PurchaseDayReturn;
import com.muscat.backtest.domain.dto.market.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// 기간의 거래일마다 당일에 샀다면 평가일까지의 총 수익률(단순 매수 시뮬레이션과 같은 수수료 · 배당 · 환율 계산)
public final class PurchaseDayReturns {

  /** 매수 조건과 평가일 값(주식 수가 있으면 투자 금액 대신, 재투자 가격은 배당락일마다 미리 찾아 둔 값) */
  public record Inputs(BigDecimal investmentKrw, Integer shares, BigDecimal feeRate, boolean reinvest, BigDecimal taxRate,
                       DividendHistoryDto dividendHistory, Map<LocalDate, BigDecimal> exDatePrices,
                       BigDecimal valuationPriceUsd, BigDecimal currentFxRate, LocalDate valuationDate) {}

  private PurchaseDayReturns() {}

  // 평가일 전의 쓸 수 있는 일봉마다 한 점(산 날 환율은 purchaseFx)
  public static List<PurchaseDayReturn> of(List<OHLCPriceDto> candles, Function<LocalDate, BigDecimal> purchaseFx,
    Inputs in) {
    List<PurchaseDayReturn> points = new ArrayList<>();
    for (OHLCPriceDto candle : candles) {
      LocalDate date = candle.date();
      if (!date.isBefore(in.valuationDate()) || !PriceLookup.isUsable(candle)) {
        continue;
      }
      points.add(returnOn(date, PriceLookup.effectiveClose(candle), purchaseFx.apply(date), in));
    }
    return points;
  }

  // 받은 날짜 종가 · 환율로 같은 투자 금액이나 같은 주식 수를 사서 평가일까지 보유했을 때(수수료 · 남은 현금 · 배당 재투자 또는 세후 현금 배당)
  private static PurchaseDayReturn returnOn(LocalDate date, BigDecimal priceUsd, BigDecimal fxRate, Inputs in) {
    SimulationCalculator.Purchase purchase = SimulationCalculator.purchase(in.investmentKrw(), in.shares(), priceUsd,
      fxRate, in.feeRate());
    BigDecimal investmentKrw = purchase.investmentKrw();
    BigDecimal usdAmount = purchase.usdAmount();
    BigDecimal shares = purchase.shares();
    BigDecimal remainingCash = purchase.remainingCash();

    BigDecimal cashDividendUsd = BigDecimal.ZERO;
    if (in.reinvest()) {
      for (DividendHistoryDto.DividendPayment dividend : received(date, in)) {
        BigDecimal amount = dividend.getAmount().multiply(shares)
          .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
        BigDecimal exPrice = in.exDatePrices().get(dividend.getExDate());
        if (!Decimals.isPositive(amount) || exPrice == null) {
          continue;
        }
        shares = shares.add(BacktestCalculationUtils.afterDividendTax(amount, in.taxRate(), true)
          .divide(exPrice, 8, RoundingMode.HALF_UP));
      }
    } else {
      BigDecimal total = BacktestCalculationUtils.calculateTotalDividends(
        in.dividendHistory(), shares, date, in.valuationDate());
      if (Decimals.isPositive(total)) {
        cashDividendUsd = BacktestCalculationUtils.afterDividendTax(total, in.taxRate(), false);
      }
    }

    BigDecimal currentValueUsd = shares.multiply(in.valuationPriceUsd());
    BigDecimal totalAssetKrw = MoneyUtils.convertUsdToKrw(currentValueUsd, in.currentFxRate())
      .add(Decimals.isPositive(remainingCash) ? MoneyUtils.convertUsdToKrw(remainingCash, in.currentFxRate()) : BigDecimal.ZERO)
      .add(Decimals.isPositive(cashDividendUsd) ? MoneyUtils.convertUsdToKrw(cashDividendUsd, in.currentFxRate()) : BigDecimal.ZERO);
    BigDecimal totalAssetUsd = currentValueUsd.add(remainingCash).add(cashDividendUsd);

    return new PurchaseDayReturn(date,
      MoneyUtils.roundUsd(MoneyUtils.calculateReturnRate(investmentKrw, totalAssetKrw)),
      MoneyUtils.roundUsd(MoneyUtils.calculateReturnRate(usdAmount, totalAssetUsd)));
  }

  // 받은 날짜에 산 주식이 받는 배당(배당락일이 산 날 뒤 · 평가일까지, 날짜순)
  private static List<DividendHistoryDto.DividendPayment> received(LocalDate date, Inputs in) {
    if (in.dividendHistory() == null || in.dividendHistory().getDividends() == null) {
      return List.of();
    }
    return in.dividendHistory().getDividends().stream()
      .filter(d -> d.getExDate() != null)
      .filter(d -> BacktestCalculationUtils.receivesDividend(date, d.getExDate()) && !d.getExDate().isAfter(in.valuationDate()))
      .sorted(Comparator.comparing(DividendHistoryDto.DividendPayment::getExDate))
      .toList();
  }
}
