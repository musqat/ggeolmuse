package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

// 완료 체결 · 배당 몫 · 종가 · 환율로 거래일마다 계좌 수익 계산(USD 로 더한 뒤 그 날짜 환율로 원화)
public final class ProfitFlowCalculator {

  // 평가손익 · 투자 원금(그 날짜에 들고 있던 종목 매수 금액)은 그 날짜 값, 실현 손익 · 배당(세후) · 수수료는 그 날짜까지 누적
  public record DailyProfit(LocalDate date, BigDecimal unrealizedUsd, BigDecimal realizedUsd,
    BigDecimal dividendUsd, BigDecimal feeUsd, BigDecimal fxRate, BigDecimal profitKrw,
    BigDecimal investedUsd, BigDecimal investedKrw) {
  }

  static final Comparator<Trade> TRADE_ORDER = Comparator.comparing(Trade::getTradeDate)
    .thenComparing(Trade::getExecutedAt, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
    .thenComparing(Trade::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

  private ProfitFlowCalculator() {
  }

  // days 는 오름차순, 그 날짜 이하 가장 늦은 종가로 평가, 환율이 없는 날은 건너뜀
  public static List<DailyProfit> calculate(List<Trade> trades, List<DividendPayout> payouts,
    Map<String, NavigableMap<LocalDate, BigDecimal>> closes, Map<LocalDate, BigDecimal> fxRates,
    List<LocalDate> days, int pricePrecision) {
    List<Trade> completed = trades.stream()
      .filter(trade -> trade.getStatus() == TradeStatus.COMPLETED)
      .sorted(TRADE_ORDER)
      .toList();
    List<DividendPayout> sortedPayouts = payouts.stream()
      .sorted(Comparator.comparing(DividendPayout::getExDate))
      .toList();

    Map<String, Holdings> positions = new HashMap<>();
    BigDecimal realized = BigDecimal.ZERO;
    BigDecimal fees = BigDecimal.ZERO;
    BigDecimal dividends = BigDecimal.ZERO;
    int nextTrade = 0;
    int nextPayout = 0;
    List<DailyProfit> result = new ArrayList<>();
    for (LocalDate day : days) {
      while (nextTrade < completed.size() && !completed.get(nextTrade).getTradeDate().isAfter(day)) {
        Trade trade = completed.get(nextTrade++);
        realized = realized.add(apply(positions, trade, pricePrecision));
        fees = fees.add(trade.getFee());
      }
      while (nextPayout < sortedPayouts.size() && !sortedPayouts.get(nextPayout).getExDate().isAfter(day)) {
        dividends = dividends.add(sortedPayouts.get(nextPayout++).getNetAmount());
      }
      BigDecimal fxRate = fxRates.get(day);
      if (fxRate == null) {
        continue;
      }
      BigDecimal unrealized = unrealized(positions, closes, day);
      BigDecimal profitUsd = unrealized.add(realized).add(dividends).subtract(fees);
      BigDecimal invested = positions.values().stream()
        .map(Holdings::getTotalInvestedAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
      result.add(new DailyProfit(day, usd(unrealized), usd(realized), usd(dividends), usd(fees), fxRate,
        krw(profitUsd, fxRate), usd(invested), krw(invested, fxRate)));
    }
    return result;
  }

  // 보유에 반영하고 매도면 실현 손익(체결 금액 − 줄어든 투자금액, 보유보다 많은 매도는 들고 있던 수량만)
  static BigDecimal apply(Map<String, Holdings> positions, Trade trade, int pricePrecision) {
    Holdings before = positions.get(trade.getSymbol());
    BigDecimal heldQuantity = before == null ? BigDecimal.ZERO : before.getTotalQuantity();
    BigDecimal investedBefore = before == null ? BigDecimal.ZERO : before.getTotalInvestedAmount();

    Holdings after = HoldingsCalculator.apply(before, trade, pricePrecision).holdings();
    if (after == null) {
      positions.remove(trade.getSymbol());
    } else {
      positions.put(trade.getSymbol(), after);
    }
    if (trade.getTradeType() == TradeType.BUY) {
      return BigDecimal.ZERO;
    }
    BigDecimal investedAfter = after == null ? BigDecimal.ZERO : after.getTotalInvestedAmount();
    BigDecimal soldQuantity = after == null ? heldQuantity : trade.getQuantity();
    return soldQuantity.multiply(trade.getPrice()).subtract(investedBefore.subtract(investedAfter));
  }

  // 종가를 못 받은 종목은 0 으로 평가
  private static BigDecimal unrealized(Map<String, Holdings> positions,
    Map<String, NavigableMap<LocalDate, BigDecimal>> closes, LocalDate day) {
    BigDecimal total = BigDecimal.ZERO;
    for (Holdings position : positions.values()) {
      NavigableMap<LocalDate, BigDecimal> symbolCloses = closes.get(position.getSymbol());
      Map.Entry<LocalDate, BigDecimal> close = symbolCloses == null ? null : symbolCloses.floorEntry(day);
      BigDecimal value = close == null ? BigDecimal.ZERO : position.getTotalQuantity().multiply(close.getValue());
      total = total.add(value.subtract(position.getTotalInvestedAmount()));
    }
    return total;
  }

  private static BigDecimal usd(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal krw(BigDecimal usd, BigDecimal fxRate) {
    return usd.multiply(fxRate).setScale(0, RoundingMode.HALF_UP);
  }

  // 기간 안 수익 점의 최고 · 최저 · 현재 수익(같은 값이면 먼저 나온 날)
  public static ProfitFlowResponse summarize(List<ProfitPoint> points) {
    if (points.isEmpty()) {
      return new ProfitFlowResponse(List.of(), null, null, BigDecimal.ZERO);
    }
    ProfitPoint best = points.getFirst();
    ProfitPoint worst = points.getFirst();
    for (ProfitPoint point : points) {
      if (point.profitKrw().compareTo(best.profitKrw()) > 0) {
        best = point;
      }
      if (point.profitKrw().compareTo(worst.profitKrw()) < 0) {
        worst = point;
      }
    }
    return new ProfitFlowResponse(points, best, worst, points.getLast().profitKrw());
  }
}
