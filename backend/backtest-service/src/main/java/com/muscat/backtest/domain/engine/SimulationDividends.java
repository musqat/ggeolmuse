package com.muscat.backtest.domain.engine;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.domain.calculation.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.domain.model.SimulationContext;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.domain.dto.market.DividendHistoryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

// 단일 매수 배당: 배당락일 순서로 배당락일 주식 수만큼 받아 재투자하거나 현금으로 쌓음(배당마다 먼저 배당락일 전 연말 정리)
@Slf4j
final class SimulationDividends {

  // 배당을 받은 뒤 주식 수 · 현금(USD) · 받은 세전 배당 합 · 재투자한 세후 배당 합 · 재투자 날짜
  record Result(BigDecimal shares, BigDecimal cash, BigDecimal gross, BigDecimal reinvested,
                List<LocalDate> reinvestDates) {}

  private SimulationDividends() {}

  // 세후 배당(세금은 두 자리 반올림)을 배당락일 종가(closeOn)로 재투자
  static Result reinvest(SimulationContext context, HoldingTrail trail, BigDecimal shares, BigDecimal cash,
    Function<LocalDate, BigDecimal> closeOn) {
    BigDecimal dividendsReinvested = BigDecimal.ZERO;
    BigDecimal grossDividends = BigDecimal.ZERO;
    List<LocalDate> reinvestDates = new ArrayList<>();
    if (context.dividendHistory() == null
      || context.dividendHistory().getDividends() == null
      || context.dividendHistory().getDividends().isEmpty()) {
      return new Result(shares, cash, grossDividends, dividendsReinvested, reinvestDates);
    }

    log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", context.dividendHistory().getDividends().size());

    LocalDate purchaseDate = context.purchaseData().date();

    // 배당락일이 매수 뒤 · 평가일까지인 배당을 배당락일 순서로
    var dividends = context.dividendHistory().getDividends().stream()
      .filter(d -> d.getExDate() != null)
      .filter(d -> BacktestCalculationUtils.receivesDividend(purchaseDate, d.getExDate())
        && !d.getExDate().isAfter(context.valuationDate()))
      .sorted(Comparator.comparing(DividendHistoryDto.DividendPayment::getExDate))
      .toList();

    BigDecimal taxRate = context.request().getDividendTaxRate();
    BigDecimal held = shares;
    for (var dividend : dividends) {
      held = trail.harvestBefore(dividend.getExDate(), held, cash);
      // 배당락일 보유 주식 수 × 주당 배당(세전)
      BigDecimal dividendAmount = dividend.getAmount().multiply(held)
        .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
      if (!Decimals.isPositive(dividendAmount)) {
        continue;
      }
      grossDividends = grossDividends.add(dividendAmount);

      BigDecimal afterTaxDividend = BacktestCalculationUtils.afterDividendTax(dividendAmount, taxRate, true);
      if (taxRate != null && Decimals.isPositive(taxRate)) {
        log.info("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
          dividend.getExDate(), dividendAmount, afterTaxDividend,
          taxRate.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER));
      }

      BigDecimal dividendDayPrice = closeOn.apply(dividend.getExDate());

      // 세후 배당으로 더 산 주식 수
      BigDecimal additionalShares = afterTaxDividend.divide(dividendDayPrice, 8, RoundingMode.HALF_UP);

      held = held.add(additionalShares);
      dividendsReinvested = dividendsReinvested.add(afterTaxDividend);
      reinvestDates.add(dividend.getExDate());
      trail.step(dividend.getExDate(), held, cash);
      // 재투자 주식의 원가는 세후 배당 × 배당락일 환율
      trail.taxTrade(StrategyTransaction.reinvestment(new StrategyTransaction.Order(null, null, dividend.getExDate(),
        dividend.getExDate(), dividendDayPrice, trail.fxOn(dividend.getExDate()), null, null), additionalShares,
        afterTaxDividend));

      log.info("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
        dividend.getExDate(), afterTaxDividend, held, additionalShares, dividendDayPrice);
    }

    log.info("배당금 재투자 완료: 총 ${} 재투자, 최종 보유 {}주", dividendsReinvested, held);
    return new Result(held, cash, grossDividends, dividendsReinvested, reinvestDates);
  }

  // 세후 배당(반올림 없음)을 현금으로 쌓고 배당락일마다 보유 단계를 남김
  static Result keepAsCash(SimulationContext context, HoldingTrail trail, BigDecimal shares, BigDecimal cash) {
    BigDecimal taxRate = context.request().getDividendTaxRate();
    BigDecimal held = shares;
    BigDecimal kept = cash;
    BigDecimal grossDividends = BigDecimal.ZERO;
    for (DividendHistoryDto.DividendPayment dividend : cashDividends(context)) {
      held = trail.harvestBefore(dividend.getExDate(), held, kept);
      BigDecimal gross = dividend.getAmount().multiply(held);
      grossDividends = grossDividends.add(gross);
      kept = kept.add(BacktestCalculationUtils.afterDividendTax(gross, taxRate, false));
      trail.step(dividend.getExDate(), held, kept);
    }
    return new Result(held, kept, grossDividends, BigDecimal.ZERO, new ArrayList<>());
  }

  // 매수일 뒤 평가일까지 배당락일이 온 금액 있는 배당(배당락일 순서)
  private static List<DividendHistoryDto.DividendPayment> cashDividends(SimulationContext context) {
    if (context.dividendHistory() == null || context.dividendHistory().getDividends() == null) {
      return List.of();
    }
    LocalDate purchaseDate = context.purchaseData().date();
    return context.dividendHistory().getDividends().stream()
      .filter(d -> d.getExDate() != null && d.getAmount() != null)
      .filter(d -> BacktestCalculationUtils.receivesDividend(purchaseDate, d.getExDate())
        && !d.getExDate().isAfter(context.valuationDate()))
      .sorted(Comparator.comparing(DividendHistoryDto.DividendPayment::getExDate))
      .toList();
  }
}
