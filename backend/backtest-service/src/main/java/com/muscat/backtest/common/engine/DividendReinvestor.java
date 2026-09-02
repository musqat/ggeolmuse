package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 배당 재투자 처리 단일화. DCA/조건부 전략이 복붙하던 ~110줄 블록을 추출(동작 불변).
 * 각 배당일(ex-date)의 보유 주식수 × 주당배당 → 세후 → 그날 종가로 추가 매수하여 transactions에 기록.
 */
@Slf4j
public final class DividendReinvestor {

  private DividendReinvestor() {}

  public static BigDecimal reinvest(
      MarketDataClient marketDataClient,
      DividendHistoryDto dividendHistory,
      List<StrategyTransaction> transactions,
      String symbol,
      LocalDate firstPurchaseDate,
      boolean reinvestEnabled,
      BigDecimal dividendTaxRate,
      BigDecimal manualPurchaseFxRate,
      BigDecimal currentFxRateValue) {

    final BigDecimal[] dividendsReinvestedArray = {BigDecimal.ZERO};

    if (!(reinvestEnabled
        && dividendHistory != null
        && dividendHistory.getDividends() != null
        && !dividendHistory.getDividends().isEmpty())) {
      return dividendsReinvestedArray[0];
    }

    log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", dividendHistory.getDividends().size());

    // BULK API: 배당 날짜들의 가격 및 환율 데이터를 한 번에 조회
    List<LocalDate> dividendDates = dividendHistory.getDividends().stream()
        .filter(dividend -> dividend.getExDate() != null)
        .filter(dividend -> !dividend.getExDate().isBefore(firstPurchaseDate)
            && !dividend.getExDate().isAfter(LocalDate.now()))
        .map(d -> d.getExDate())
        .toList();

    if (dividendDates.isEmpty()) {
      log.info("배당금 재투자 완료: 총 ${} 재투자", dividendsReinvestedArray[0]);
      return dividendsReinvestedArray[0];
    }

    List<OHLCPriceDto> dividendPrices = marketDataClient.getOHLCPriceRange(
        symbol,
        dividendDates.get(0).toString(),
        dividendDates.get(dividendDates.size() - 1).toString());

    Map<LocalDate, OHLCPriceDto> dividendPriceMap = dividendPrices.stream()
        .filter(OHLCPriceDto::available)
        .collect(Collectors.toMap(OHLCPriceDto::date, p -> p, (existing, replacement) -> replacement));

    final Map<LocalDate, BigDecimal> dividendFxRateMap =
        manualPurchaseFxRate == null
            ? BacktestDataUtils.getBulkFxRates(marketDataClient, dividendDates)
            : new java.util.HashMap<>();

    log.info("배당 재투자 데이터 조회 완료: 가격 {}개, 환율 {}개",
        dividendPriceMap.size(), dividendFxRateMap.size());

    dividendHistory.getDividends().stream()
        .filter(dividend -> dividend.getExDate() != null)
        .filter(dividend -> !dividend.getExDate().isBefore(firstPurchaseDate)
            && !dividend.getExDate().isAfter(LocalDate.now()))
        .sorted((d1, d2) -> d1.getExDate().compareTo(d2.getExDate()))
        .forEach(dividend -> {
          BigDecimal sharesAtDividendDate = transactions.stream()
              .filter(tx -> !tx.getDate().isAfter(dividend.getExDate()))
              .map(StrategyTransaction::getShares)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

          if (Decimals.isPositive(sharesAtDividendDate)) {
            BigDecimal dividendAmount = dividend.getAmount().multiply(sharesAtDividendDate)
                .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

            BigDecimal taxRate = dividendTaxRate;
            BigDecimal afterTaxDividend = dividendAmount;
            if (taxRate != null && Decimals.isPositive(taxRate)) {
              BigDecimal taxAmount = dividendAmount.multiply(taxRate)
                  .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
              afterTaxDividend = dividendAmount.subtract(taxAmount);
              log.debug("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
                  dividend.getExDate(), dividendAmount, afterTaxDividend,
                  taxRate.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER));
            }

            OHLCPriceDto priceAtDividendDate = dividendPriceMap.get(dividend.getExDate());

            BigDecimal fxRateAtDividendDate;
            if (manualPurchaseFxRate != null) {
              fxRateAtDividendDate = manualPurchaseFxRate;
            } else {
              fxRateAtDividendDate = dividendFxRateMap.getOrDefault(
                  dividend.getExDate(), currentFxRateValue);
            }

            if (priceAtDividendDate != null && priceAtDividendDate.available()) {
              BigDecimal dividendDayPrice = PriceLookup.effectiveClose(priceAtDividendDate);
              BigDecimal additionalShares = afterTaxDividend.divide(
                  dividendDayPrice, BacktestConstants.Money.SHARES_SCALE,
                  BacktestConstants.Money.ROUNDING_MODE);

              StrategyTransaction dividendReinvestment = StrategyTransaction.builder()
                  .date(dividend.getExDate())
                  .actualDate(priceAtDividendDate.date())
                  .price(dividendDayPrice)
                  .shares(additionalShares)
                  .amount(BigDecimal.ZERO)
                  .fxRate(fxRateAtDividendDate)
                  .trigger(BacktestConstants.TransactionTrigger.DIVIDEND_REINVESTMENT)
                  .build();

              transactions.add(dividendReinvestment);
              dividendsReinvestedArray[0] = dividendsReinvestedArray[0].add(afterTaxDividend);

              log.debug("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
                  dividend.getExDate(), afterTaxDividend, sharesAtDividendDate,
                  additionalShares, dividendDayPrice);
            }
          }
        });

    log.info("배당금 재투자 완료: 총 ${} 재투자", dividendsReinvestedArray[0]);
    return dividendsReinvestedArray[0];
  }
}
