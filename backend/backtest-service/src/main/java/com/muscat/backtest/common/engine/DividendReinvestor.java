package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// 배당 재투자: 배당락일마다 보유 주식 수 × 주당 배당 → 세후 → 배당락일 종가로 더 사서 transactions 에 기록
@Slf4j
public final class DividendReinvestor {

  private DividendReinvestor() {}

  public static BigDecimal reinvest(
      MarketDataClientWrapper marketData,
      DividendHistoryDto dividendHistory,
      List<StrategyTransaction> transactions,
      String symbol,
      LocalDate firstPurchaseDate,
      boolean reinvestEnabled,
      BigDecimal dividendTaxRate,
      BigDecimal manualPurchaseFxRate,
      BigDecimal currentFxRateValue) {
    return reinvest(marketData, dividendHistory, transactions, symbol, firstPurchaseDate, reinvestEnabled,
        dividendTaxRate, manualPurchaseFxRate, currentFxRateValue, exDate -> { });
  }

  // 배당락일 순서로 재투자, 배당마다 먼저 배당락일을 beforeDividend 에 넘김
  public static BigDecimal reinvest(
      MarketDataClientWrapper marketData,
      DividendHistoryDto dividendHistory,
      List<StrategyTransaction> transactions,
      String symbol,
      LocalDate firstPurchaseDate,
      boolean reinvestEnabled,
      BigDecimal dividendTaxRate,
      BigDecimal manualPurchaseFxRate,
      BigDecimal currentFxRateValue,
      Consumer<LocalDate> beforeDividend) {

    BigDecimal dividendsReinvested = BigDecimal.ZERO;

    if (!(reinvestEnabled
        && dividendHistory != null
        && dividendHistory.getDividends() != null
        && !dividendHistory.getDividends().isEmpty())) {
      return dividendsReinvested;
    }

    log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", dividendHistory.getDividends().size());

    List<DividendHistoryDto.DividendPayment> dividends = receivable(dividendHistory, firstPurchaseDate);
    if (dividends.isEmpty()) {
      log.info("배당금 재투자 완료: 총 ${} 재투자", dividendsReinvested);
      return dividendsReinvested;
    }

    // 배당락일 시세 · 환율을 한 번에 조회
    List<LocalDate> dividendDates = dividends.stream().map(DividendHistoryDto.DividendPayment::getExDate).toList();
    List<OHLCPriceDto> dividendPrices = marketData.getOHLCPriceRange(
        symbol,
        dividendDates.get(0).toString(),
        dividendDates.get(dividendDates.size() - 1).toString());

    Map<LocalDate, OHLCPriceDto> dividendPriceMap = dividendPrices.stream()
        .filter(OHLCPriceDto::available)
        .collect(Collectors.toMap(OHLCPriceDto::date, p -> p, (existing, replacement) -> replacement));

    final FxRates dividendFxRateMap =
        manualPurchaseFxRate == null
            ? BacktestDataUtils.getBulkFxRates(marketData, dividendDates)
            : FxRates.EMPTY;

    log.info("배당 재투자 데이터 조회 완료: 가격 {}개, 환율 {}개",
        dividendPriceMap.size(), dividendFxRateMap.size());

    for (DividendHistoryDto.DividendPayment dividend : dividends) {
      beforeDividend.accept(dividend.getExDate());
      BigDecimal found = dividendFxRateMap.get(dividend.getExDate());
      BigDecimal fxRate = manualPurchaseFxRate != null ? manualPurchaseFxRate
          : found != null ? found : currentFxRateValue;
      dividendsReinvested = dividendsReinvested.add(reinvestOne(dividend, transactions, dividendTaxRate,
          dividendPriceMap.get(dividend.getExDate()), fxRate, dividendFxRateMap.isDefault(dividend.getExDate())));
    }

    log.info("배당금 재투자 완료: 총 ${} 재투자", dividendsReinvested);
    return dividendsReinvested;
  }

  // 첫 매수 뒤 오늘까지 배당락일이 온 배당(배당락일 순서)
  private static List<DividendHistoryDto.DividendPayment> receivable(DividendHistoryDto dividendHistory,
      LocalDate firstPurchaseDate) {
    return dividendHistory.getDividends().stream()
        .filter(dividend -> dividend.getExDate() != null)
        .filter(dividend -> BacktestCalculationUtils.receivesDividend(firstPurchaseDate, dividend.getExDate())
            && !dividend.getExDate().isAfter(LocalDate.now()))
        .sorted(Comparator.comparing(DividendHistoryDto.DividendPayment::getExDate))
        .toList();
  }

  // 배당락일 보유 주식 수 × 주당 배당의 세후 금액으로 배당락일 종가에 더 산 거래를 남긴 재투자 금액(보유가 없거나 배당락일 시세가 없으면 0)
  private static BigDecimal reinvestOne(DividendHistoryDto.DividendPayment dividend,
      List<StrategyTransaction> transactions, BigDecimal dividendTaxRate, OHLCPriceDto priceAtDividendDate,
      BigDecimal fxRateAtDividendDate, boolean defaultFx) {
    BigDecimal sharesAtDividendDate = transactions.stream()
        .filter(tx -> BacktestCalculationUtils.receivesDividend(tx.tradedOn(), dividend.getExDate()))
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (!Decimals.isPositive(sharesAtDividendDate)) {
      return BigDecimal.ZERO;
    }
    BigDecimal dividendAmount = dividend.getAmount().multiply(sharesAtDividendDate)
        .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

    BigDecimal taxRate = dividendTaxRate;
    BigDecimal afterTaxDividend = BacktestCalculationUtils.afterDividendTax(dividendAmount, taxRate, true);
    if (taxRate != null && Decimals.isPositive(taxRate)) {
      log.debug("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
          dividend.getExDate(), dividendAmount, afterTaxDividend,
          taxRate.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER));
    }
    if (priceAtDividendDate == null || !priceAtDividendDate.available()) {
      return BigDecimal.ZERO;
    }

    BigDecimal dividendDayPrice = PriceLookup.effectiveClose(priceAtDividendDate);
    BigDecimal additionalShares = afterTaxDividend.divide(
        dividendDayPrice, BacktestConstants.Money.SHARES_SCALE,
        BacktestConstants.Money.ROUNDING_MODE);

    transactions.add(StrategyTransaction.reinvestment(new StrategyTransaction.Order(null, null, dividend.getExDate(),
        priceAtDividendDate.date(), dividendDayPrice, fxRateAtDividendDate, defaultFx ? dividend.getExDate() : null,
        BacktestConstants.TransactionTrigger.DIVIDEND_REINVESTMENT), additionalShares, null));

    log.debug("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
        dividend.getExDate(), afterTaxDividend, sharesAtDividendDate,
        additionalShares, dividendDayPrice);
    return afterTaxDividend;
  }
}
