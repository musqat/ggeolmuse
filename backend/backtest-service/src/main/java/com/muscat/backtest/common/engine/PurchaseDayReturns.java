package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.response.PurchaseDayReturn;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
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

// 기간의 거래일마다 그 날짜에 샀다면 평가일까지의 총 수익률(단순 매수 시뮬레이션과 같은 수수료 · 배당 · 환율 계산)
public final class PurchaseDayReturns {

  /** 매수 조건과 평가일 값(재투자 가격은 배당락일마다 미리 찾아 둔 값) */
  public record Inputs(BigDecimal investmentKrw, BigDecimal feeRate, boolean reinvest, BigDecimal taxRate,
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

  // 그 날짜 종가 · 환율로 사서 평가일까지 들고 있었을 때(수수료 · 남은 현금 · 배당 재투자 또는 세후 현금 배당)
  private static PurchaseDayReturn returnOn(LocalDate date, BigDecimal priceUsd, BigDecimal fxRate, Inputs in) {
    BigDecimal usdAmount = MoneyUtils.convertKrwToUsd(in.investmentKrw(), fxRate);
    BigDecimal shares = BacktestCalculationUtils.calculateSharesWithFee(usdAmount, priceUsd, in.feeRate());
    BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(usdAmount, in.feeRate());
    BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(shares, priceUsd, fee);
    BigDecimal remainingCash = BacktestCalculationUtils.calculateRemainingCash(usdAmount, totalCost);

    BigDecimal cashDividendUsd = BigDecimal.ZERO;
    if (in.reinvest()) {
      for (DividendHistoryDto.DividendPayment dividend : received(date, in)) {
        BigDecimal amount = dividend.getAmount().multiply(shares)
          .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
        BigDecimal exPrice = in.exDatePrices().get(dividend.getExDate());
        if (!Decimals.isPositive(amount) || exPrice == null) {
          continue;
        }
        shares = shares.add(afterTax(amount, in.taxRate(), true).divide(exPrice, 8, RoundingMode.HALF_UP));
      }
    } else {
      BigDecimal total = BacktestCalculationUtils.calculateTotalDividends(
        in.dividendHistory(), shares, date, in.valuationDate());
      if (Decimals.isPositive(total)) {
        cashDividendUsd = afterTax(total, in.taxRate(), false);
      }
    }

    BigDecimal currentValueUsd = shares.multiply(in.valuationPriceUsd());
    BigDecimal totalAssetKrw = MoneyUtils.convertUsdToKrw(currentValueUsd, in.currentFxRate())
      .add(Decimals.isPositive(remainingCash) ? MoneyUtils.convertUsdToKrw(remainingCash, in.currentFxRate()) : BigDecimal.ZERO)
      .add(Decimals.isPositive(cashDividendUsd) ? MoneyUtils.convertUsdToKrw(cashDividendUsd, in.currentFxRate()) : BigDecimal.ZERO);
    BigDecimal totalAssetUsd = currentValueUsd.add(remainingCash).add(cashDividendUsd);

    return new PurchaseDayReturn(date,
      MoneyUtils.roundUsd(MoneyUtils.calculateReturnRate(in.investmentKrw(), totalAssetKrw)),
      MoneyUtils.roundUsd(MoneyUtils.calculateReturnRate(usdAmount, totalAssetUsd)));
  }

  // 그 날짜에 산 주식이 받는 배당(배당락일이 산 날 뒤 · 평가일까지, 날짜순)
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

  // 원천징수를 뺀 배당(재투자는 세금을 배당 자릿수로 반올림해 뺌, 현금은 그대로 곱해 뺌)
  private static BigDecimal afterTax(BigDecimal amount, BigDecimal taxRate, boolean reinvest) {
    if (taxRate == null || !Decimals.isPositive(taxRate)) {
      return amount;
    }
    BigDecimal tax = amount.multiply(taxRate);
    if (reinvest) {
      tax = tax.setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
    }
    return amount.subtract(tax);
  }
}
