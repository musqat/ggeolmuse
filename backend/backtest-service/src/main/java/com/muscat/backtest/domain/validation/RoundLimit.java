package com.muscat.backtest.domain.validation;

import com.muscat.backtest.common.constants.BacktestConstants.RequestLimit;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 적립 · 매수 회차 상한 검사(실행 전에 달력으로 센 회차 × 기간 평일 수, 휴장일도 평일로 세고 주말만 뺌)
 */
public final class RoundLimit {

  private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

  private RoundLimit() {}

  /** 첫날 ~ 끝날 평일 수(종가가 없는 오늘부터는 뺌) */
  public static long weekdays(LocalDate from, LocalDate to, LocalDate today) {
    LocalDate end = closed(to, today);
    if (from.isAfter(end)) {
      return 0;
    }
    long days = ChronoUnit.DAYS.between(from, end) + 1;
    long count = days / 7 * 5;
    for (LocalDate day = from.plusDays(days / 7 * 7); !day.isAfter(end); day = day.plusDays(1)) {
      count += isWeekend(day) ? 0 : 1;
    }
    return count;
  }

  /** 첫 매수일 ~ 끝날 적립식 회차 수(매주는 주말 첫 매수일을 월요일로, 매달은 없는 투자일을 말일로, 끝날이 말일 전인 달은 투자일 ~ 끝날에 평일이 있을 때만) */
  public static long dcaRounds(DcaFrequency frequency, int interval, LocalDate first, LocalDate end,
                               Integer purchaseDay, LocalDate today) {
    LocalDate last = closed(end, today);
    return switch (frequency) {
      case DAILY -> weekdays(first, last, today);
      case WEEKLY -> {
        LocalDate start = toWeekday(first);
        yield start.isAfter(last) ? 0 : ChronoUnit.DAYS.between(start, last) / 7 + 1;
      }
      case MONTHLY -> monthlyRounds(Math.max(1, interval), first, last,
        purchaseDay != null ? purchaseDay : first.getDayOfMonth());
    };
  }

  private static long monthlyRounds(int step, LocalDate first, LocalDate end, int day) {
    YearMonth month = YearMonth.from(first);
    long count = 0;
    while (!dayIn(month.plusMonths(count * step), day).isAfter(end)) {
      count++;
    }
    if (count == 0) {
      return 0;
    }
    LocalDate last = dayIn(month.plusMonths((count - 1) * step), day);
    boolean endMonth = YearMonth.from(last).equals(YearMonth.from(end));
    boolean monthEnd = end.equals(end.withDayOfMonth(end.lengthOfMonth()));
    return endMonth && !monthEnd && toWeekday(last).isAfter(end) ? count - 1 : count;
  }

  /** 회차 일정이 있는 요청(적립식 · 조건부)의 회차 × 기간 평일 수, 나머지 전략은 0 */
  public static long load(StrategyRequest request, LocalDate today) {
    if (request instanceof DcaStrategyRequest dca) {
      return dcaLoad(dca, today);
    }
    if (request instanceof ConditionalStrategyRequest conditional) {
      return conditionalLoad(conditional, today);
    }
    return 0;
  }

  /** 적립식 회차 × 기간 평일 수가 상한을 넘으면 TOO_MANY_ROUNDS */
  public static void requireDca(DcaStrategyRequest request, LocalDate today) {
    if (dcaLoad(request, today) > RequestLimit.MAX_ROUND_LOAD) {
      throw new BacktestException(BacktestResponse.TOO_MANY_ROUNDS);
    }
  }

  /** 조건부 매수 회차 × 기간 평일 수가 상한을 넘으면 TOO_MANY_ROUNDS */
  public static void requireConditional(ConditionalStrategyRequest request, LocalDate today) {
    if (conditionalLoad(request, today) > RequestLimit.MAX_ROUND_LOAD) {
      throw new BacktestException(BacktestResponse.TOO_MANY_ROUNDS);
    }
  }

  /** 포트폴리오 적립식 줄(줄마다 같은 일정)의 회차 × 기간 평일 수가 상한을 넘으면 TOO_MANY_ROUNDS */
  public static void requirePortfolio(PortfolioRequest request, LocalDate today) {
    if (request.getAssets().stream().noneMatch(asset -> request.strategyOf(asset) == PortfolioStrategyType.DCA)) {
      return;
    }
    LocalDate first = request.getFirstPurchaseDate() != null ? request.getFirstPurchaseDate() : request.getStartDate();
    long rounds = dcaRounds(orMonthly(request.getFrequency()), orOne(request.getInvestmentInterval()), first,
      request.getEndDate(), request.getPurchaseDay(), today);
    if (rounds * weekdays(first, request.getEndDate(), today) > RequestLimit.MAX_ROUND_LOAD) {
      throw new BacktestException(BacktestResponse.TOO_MANY_ROUNDS);
    }
  }

  /** 전략 비교 칩마다 상한, 칩 합은 비교 상한을 넘으면 TOO_MANY_COMPARISON_ROUNDS */
  public static void requireComparison(List<StrategyRequest> chips, LocalDate today) {
    long total = 0;
    for (StrategyRequest chip : chips) {
      if (chip instanceof DcaStrategyRequest dca) {
        requireDca(dca, today);
      } else if (chip instanceof ConditionalStrategyRequest conditional) {
        requireConditional(conditional, today);
      }
      total += load(chip, today);
    }
    if (total > RequestLimit.MAX_COMPARISON_ROUND_LOAD) {
      throw new BacktestException(BacktestResponse.TOO_MANY_COMPARISON_ROUNDS);
    }
  }

  // 회당 금액이면 예산으로 살 수 있는 회차까지
  private static long dcaLoad(DcaStrategyRequest request, LocalDate today) {
    LocalDate first = request.getFirstPurchaseDate() != null ? request.getFirstPurchaseDate() : request.getStartDate();
    long rounds = Math.min(
      dcaRounds(orMonthly(request.getFrequency()), orOne(request.getInvestmentInterval()), first, request.getEndDate(),
        request.getPurchaseDay(), today),
      affordable(request.getAmountPerPurchase(), request.getTotalInvestmentLimit()));
    return rounds * weekdays(first, request.getEndDate(), today);
  }

  // 평일마다 살 수 있다고 보고, 매도 규칙이 없으면 최대 횟수까지, 회당 금액이면 예산으로 살 수 있는 회차까지
  private static long conditionalLoad(ConditionalStrategyRequest request, LocalDate today) {
    long days = weekdays(request.getStartDate(), request.getEndDate(), today);
    long rounds = days;
    if (!request.sells()) {
      rounds = Math.min(rounds, request.getMaxPurchases() != null ? request.getMaxPurchases() : Long.MAX_VALUE);
      rounds = Math.min(rounds, affordable(request.getAmountPerPurchase(), request.getTotalInvestment()));
    }
    return rounds * days;
  }

  // 예산 ÷ 회당 금액(둘 중 하나가 없거나 몫이 long 범위를 넘으면 제한 없음)
  private static long affordable(BigDecimal amount, BigDecimal budget) {
    if (amount == null || budget == null || amount.signum() <= 0) {
      return Long.MAX_VALUE;
    }
    BigDecimal rounds = budget.divide(amount, 0, RoundingMode.DOWN);
    return rounds.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : rounds.longValue();
  }

  // 끝날과 어제 가운데 이른 날
  private static LocalDate closed(LocalDate end, LocalDate today) {
    LocalDate yesterday = today.minusDays(1);
    return end.isAfter(yesterday) ? yesterday : end;
  }

  // 받은 달의 투자일(없으면 말일)
  private static LocalDate dayIn(YearMonth month, int day) {
    return month.atDay(Math.max(1, Math.min(day, month.lengthOfMonth())));
  }

  // 주말이면 다음 월요일
  private static LocalDate toWeekday(LocalDate day) {
    return day.getDayOfWeek() == DayOfWeek.SATURDAY ? day.plusDays(2)
      : day.getDayOfWeek() == DayOfWeek.SUNDAY ? day.plusDays(1) : day;
  }

  private static boolean isWeekend(LocalDate day) {
    return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
  }

  private static DcaFrequency orMonthly(DcaFrequency frequency) {
    return frequency != null ? frequency : DcaFrequency.MONTHLY;
  }

  private static int orOne(Integer interval) {
    return interval != null ? interval : 1;
  }
}
