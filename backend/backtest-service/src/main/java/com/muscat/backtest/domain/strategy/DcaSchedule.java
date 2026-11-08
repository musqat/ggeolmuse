package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

// 적립식 회차: 주기마다 계획일과 그 회차에 사는 거래일 시세
final class DcaSchedule {

  // 계획일 · 사는 날 시세(쓸 거래일이 없으면 null)
  record Round(LocalDate planned, OHLCPriceDto trade) {}

  private DcaSchedule() {}

  // 첫 매수일 ~ 종료일의 회차, 투자일 · 투자 주기는 매달에만 씀
  static List<Round> rounds(Map<LocalDate, OHLCPriceDto> priceMap, DcaFrequency frequency, LocalDate first,
      LocalDate end, int purchaseDay, int interval) {
    NavigableMap<LocalDate, OHLCPriceDto> days = new TreeMap<>(priceMap);
    return switch (frequency) {
      case DAILY -> daily(days, first, end);
      case WEEKLY -> weekly(days, first, end);
      case MONTHLY -> monthly(days, first, end, purchaseDay, interval);
    };
  }

  // 시세가 있는 날마다
  private static List<Round> daily(NavigableMap<LocalDate, OHLCPriceDto> days, LocalDate first, LocalDate end) {
    if (first.isAfter(end)) {
      return List.of();
    }
    return days.subMap(first, true, end, true).entrySet().stream()
        .map(day -> new Round(day.getKey(), day.getValue()))
        .toList();
  }

  // 첫 매수일부터 7일마다, 그 날짜부터 6일 안 첫 거래일
  // 종료일에 걸려 거래일이 없는 마지막 주는 회차가 아님
  private static List<Round> weekly(NavigableMap<LocalDate, OHLCPriceDto> days, LocalDate first, LocalDate end) {
    List<Round> rounds = new ArrayList<>();
    for (LocalDate planned = first; !planned.isAfter(end); planned = planned.plusWeeks(1)) {
      LocalDate weekEnd = planned.plusDays(6);
      OHLCPriceDto trade = firstIn(days, planned, earlier(weekEnd, end));
      if (trade != null || !weekEnd.isAfter(end)) {
        rounds.add(new Round(planned, trade));
      }
    }
    return rounds;
  }

  // 첫 매수일 뒤 첫 투자일부터 투자 주기마다 한 달에 한 번: 투자일(없는 날은 말일)이나 그 달 안 다음 거래일, 없으면 그 앞 마지막 거래일
  // 첫 매수일 · 종료일에 걸려 거래일이 없는 달은 회차가 아님
  private static List<Round> monthly(NavigableMap<LocalDate, OHLCPriceDto> days, LocalDate first, LocalDate end,
      int purchaseDay, int interval) {
    List<Round> rounds = new ArrayList<>();
    YearMonth month = YearMonth.from(first);
    if (dayIn(month, purchaseDay).isBefore(first)) {
      month = month.plusMonths(1);
    }
    for (LocalDate planned = dayIn(month, purchaseDay); !planned.isAfter(end);
        month = month.plusMonths(interval), planned = dayIn(month, purchaseDay)) {
      LocalDate from = later(month.atDay(1), first);
      LocalDate to = earlier(month.atEndOfMonth(), end);
      OHLCPriceDto trade = firstIn(days, planned, to);
      if (trade == null) {
        trade = lastIn(days, from, planned.minusDays(1));
      }
      boolean wholeMonth = from.equals(month.atDay(1)) && to.equals(month.atEndOfMonth());
      if (trade != null || wholeMonth) {
        rounds.add(new Round(planned, trade));
      }
    }
    return rounds;
  }

  // 그 달의 투자일, 그 달에 없으면 말일
  private static LocalDate dayIn(YearMonth month, int day) {
    return month.atDay(Math.min(day, month.lengthOfMonth()));
  }

  // from ~ to 의 첫 시세
  private static OHLCPriceDto firstIn(NavigableMap<LocalDate, OHLCPriceDto> days, LocalDate from, LocalDate to) {
    Map.Entry<LocalDate, OHLCPriceDto> day = days.ceilingEntry(from);
    return day != null && !day.getKey().isAfter(to) ? day.getValue() : null;
  }

  // from ~ to 의 마지막 시세
  private static OHLCPriceDto lastIn(NavigableMap<LocalDate, OHLCPriceDto> days, LocalDate from, LocalDate to) {
    Map.Entry<LocalDate, OHLCPriceDto> day = days.floorEntry(to);
    return day != null && !day.getKey().isBefore(from) ? day.getValue() : null;
  }

  private static LocalDate earlier(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }

  private static LocalDate later(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }
}
