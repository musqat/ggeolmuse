package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("계좌 수익 흐름 계산")
class ProfitFlowCalculatorTest {

  private static final LocalDate D1 = LocalDate.of(2024, 1, 2);
  private static final LocalDate D2 = LocalDate.of(2024, 1, 3);
  private static final LocalDate D3 = LocalDate.of(2024, 1, 4);
  private static final LocalDate D4 = LocalDate.of(2024, 1, 5);

  private static long ids = 1;

  static Trade trade(TradeType type, String symbol, String quantity, String price, String fee,
    LocalDate tradeDate, TradeStatus status) {
    return Trade.builder()
      .id(ids++)
      .userId("user-1")
      .accountId(20L)
      .symbol(symbol)
      .tradeType(type)
      .quantity(new BigDecimal(quantity))
      .price(new BigDecimal(price))
      .fee(new BigDecimal(fee))
      .totalAmount(BigDecimal.ZERO)
      .tradeDate(tradeDate)
      .executedAt(tradeDate.atTime(10, 0))
      .status(status)
      .build();
  }

  static Trade buy(String symbol, String quantity, String price, String fee, LocalDate tradeDate) {
    return trade(TradeType.BUY, symbol, quantity, price, fee, tradeDate, TradeStatus.COMPLETED);
  }

  static Trade sell(String symbol, String quantity, String price, String fee, LocalDate tradeDate) {
    return trade(TradeType.SELL, symbol, quantity, price, fee, tradeDate, TradeStatus.COMPLETED);
  }

  static DividendPayout payout(String symbol, LocalDate exDate, String net) {
    return DividendPayout.of("user-1", 20L, symbol, exDate,
      new DividendAmounts(new BigDecimal("0.20"), new BigDecimal("6"), new BigDecimal(net), BigDecimal.ZERO,
        new BigDecimal(net)), LocalDateTime.of(2024, 1, 5, 9, 0));
  }

  private static NavigableMap<LocalDate, BigDecimal> closes(Object... dateAndClose) {
    NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
    for (int i = 0; i < dateAndClose.length; i += 2) {
      closes.put((LocalDate) dateAndClose[i], new BigDecimal((String) dateAndClose[i + 1]));
    }
    return closes;
  }

  private static Map<LocalDate, BigDecimal> fx(String rate, LocalDate... days) {
    Map<LocalDate, BigDecimal> rates = new TreeMap<>();
    for (LocalDate day : days) {
      rates.put(day, new BigDecimal(rate));
    }
    return rates;
  }

  @Test
  @DisplayName("평가손익 · 실현 손익 · 세후 배당을 더하고 수수료를 뺀 USD 를 그 날짜 환율로 원화로 바꾼다")
  void calculate_SumsProfitAndConvertsToKrw() {
    List<Trade> trades = List.of(
      buy("AAPL", "10", "100.00", "1.00", D1),
      sell("AAPL", "4", "110.00", "0.50", D3));
    Map<LocalDate, BigDecimal> rates = new TreeMap<>(fx("1300", D1));
    rates.put(D2, new BigDecimal("1310"));
    rates.putAll(fx("1320", D3, D4));

    List<DailyProfit> days = ProfitFlowCalculator.calculate(trades, List.of(payout("AAPL", D4, "1.20")),
      Map.of("AAPL", closes(D1, "101.00", D2, "105.00", D3, "110.00", D4, "108.00")), rates,
      List.of(D1, D2, D3, D4), 2);

    assertThat(days).extracting(DailyProfit::date).containsExactly(D1, D2, D3, D4);
    assertThat(days).extracting(DailyProfit::profitKrw)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("11700"), new BigDecimal("64190"), new BigDecimal("130020"),
        new BigDecimal("115764"));
    DailyProfit last = days.getLast();
    assertThat(last.unrealizedUsd()).isEqualByComparingTo("48.00");
    assertThat(last.realizedUsd()).isEqualByComparingTo("40.00");
    assertThat(last.dividendUsd()).isEqualByComparingTo("1.20");
    assertThat(last.feeUsd()).isEqualByComparingTo("1.50");
    assertThat(last.fxRate()).isEqualByComparingTo("1320");
  }

  @Test
  @DisplayName("그 날짜에 들고 있던 종목 매수 금액(달러)과 그 날짜 환율로 바꾼 원화를 함께 낸다")
  void calculate_InvestedOfHeldPositions() {
    List<Trade> trades = List.of(
      buy("AAPL", "10", "100.00", "1.00", D1),
      buy("MSFT", "2", "50.00", "0.00", D2),
      sell("AAPL", "10", "110.00", "0.50", D3));
    Map<LocalDate, BigDecimal> rates = new TreeMap<>(fx("1300", D1));
    rates.put(D2, new BigDecimal("1310"));
    rates.putAll(fx("1320", D3));

    List<DailyProfit> days = ProfitFlowCalculator.calculate(trades, List.of(),
      Map.of("AAPL", closes(D1, "101.00"), "MSFT", closes(D2, "50.00")), rates, List.of(D1, D2, D3), 2);

    assertThat(days).extracting(DailyProfit::investedUsd).usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("1000.00"), new BigDecimal("1100.00"), new BigDecimal("100.00"));
    assertThat(days).extracting(DailyProfit::investedKrw).usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("1300000"), new BigDecimal("1441000"), new BigDecimal("132000"));
  }

  @Test
  @DisplayName("종가가 없는 날은 그 앞 가장 늦은 종가로 평가하고, 환율이 없는 날은 점을 만들지 않는다")
  void calculate_UsesEarlierCloseAndSkipsDayWithoutFx() {
    List<DailyProfit> days = ProfitFlowCalculator.calculate(List.of(buy("AAPL", "10", "100.00", "0.00", D1)),
      List.of(), Map.of("AAPL", closes(D1, "101.00")), fx("1000", D1, D2), List.of(D1, D2, D3), 2);

    assertThat(days).extracting(DailyProfit::date).containsExactly(D1, D2);
    assertThat(days.getLast().unrealizedUsd()).isEqualByComparingTo("10.00");
    assertThat(days.getLast().profitKrw()).isEqualByComparingTo("10000");
  }

  @Test
  @DisplayName("완료 체결만 거래일 순으로 넣고 대기 · 취소 체결은 빼며, 계산 첫 날 전 체결과 배당도 누적에 넣는다")
  void calculate_CompletedOnlyInTradeDateOrder() {
    List<Trade> trades = List.of(
      sell("AAPL", "5", "120.00", "0.00", D2),
      trade(TradeType.BUY, "AAPL", "100", "1.00", "0.00", D1, TradeStatus.PENDING),
      trade(TradeType.SELL, "AAPL", "5", "999.00", "0.00", D2, TradeStatus.CANCELLED),
      buy("AAPL", "10", "100.00", "0.00", LocalDate.of(2023, 12, 29)));

    List<DailyProfit> days = ProfitFlowCalculator.calculate(trades,
      List.of(payout("AAPL", LocalDate.of(2023, 12, 29), "2.00")),
      Map.of("AAPL", closes(D1, "100.00", D2, "120.00")), fx("1", D1, D2), List.of(D1, D2), 2);

    assertThat(days.getFirst().unrealizedUsd()).isEqualByComparingTo("0.00");
    assertThat(days.getFirst().dividendUsd()).isEqualByComparingTo("2.00");
    assertThat(days.getLast().realizedUsd()).isEqualByComparingTo("100.00");
    assertThat(days.getLast().unrealizedUsd()).isEqualByComparingTo("100.00");
  }

  @Test
  @DisplayName("전부 팔면 판 금액 − 투자금액이 실현 손익이고, 보유보다 많은 매도는 들고 있던 수량만 판 것으로 친다")
  void calculate_FullAndUncoveredSells() {
    List<Trade> trades = List.of(
      buy("AAPL", "3", "100.00", "0.00", D1),
      sell("AAPL", "3", "110.00", "0.00", D2),
      buy("MSFT", "5", "10.00", "0.00", D1),
      sell("MSFT", "8", "12.00", "0.00", D2),
      sell("TSLA", "2", "50.00", "0.00", D2));

    List<DailyProfit> days = ProfitFlowCalculator.calculate(trades, List.of(),
      Map.of("AAPL", closes(D1, "100.00"), "MSFT", closes(D1, "10.00")), fx("1", D1, D2), List.of(D1, D2), 2);

    assertThat(days.getLast().realizedUsd()).isEqualByComparingTo("40.00");
    assertThat(days.getLast().unrealizedUsd()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("종가를 하나도 못 받은 종목은 0 으로 평가한다")
  void calculate_NoCloseValuedAtZero() {
    List<DailyProfit> days = ProfitFlowCalculator.calculate(List.of(buy("AAPL", "2", "50.00", "0.00", D1)),
      List.of(), Map.of(), fx("1", D1), List.of(D1), 2);

    assertThat(days.getFirst().unrealizedUsd()).isEqualByComparingTo("-100.00");
  }

  @Nested
  @DisplayName("수익 흐름 요약(최고 · 최저 · 현재 수익)")
  class Summary {
    private static final LocalDate START = LocalDate.of(2024, 1, 1);

    private static List<ProfitPoint> points(long... profits) {
      List<ProfitPoint> points = new ArrayList<>();
      for (int i = 0; i < profits.length; i++) {
        points.add(new ProfitPoint(START.plusDays(i), BigDecimal.valueOf(profits[i]), BigDecimal.ZERO));
      }
      return points;
    }

    @Test
    @DisplayName("가장 큰 · 작은 점과 마지막 점을 낸다")
    void of_BestWorstCurrent() {
      ProfitFlowResponse response = ProfitFlowCalculator.summarize(points(100, 300, 50, 200, -100, 150));

      assertThat(response.points()).hasSize(6);
      assertThat(response.best())
        .isEqualTo(new ProfitPoint(START.plusDays(1), BigDecimal.valueOf(300), BigDecimal.ZERO));
      assertThat(response.worst())
        .isEqualTo(new ProfitPoint(START.plusDays(4), BigDecimal.valueOf(-100), BigDecimal.ZERO));
      assertThat(response.currentProfitKrw()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("최고 · 최저가 같은 값이면 먼저 나온 날이다")
    void of_EarliestTie() {
      ProfitFlowResponse response = ProfitFlowCalculator.summarize(points(0, 100, 0, 200, 120, 200));

      assertThat(response.best().date()).isEqualTo(START.plusDays(3));
      assertThat(response.worst().date()).isEqualTo(START);
    }

    @Test
    @DisplayName("점이 없으면 최고 · 최저는 비우고 현재 수익은 0 이다")
    void of_Empty() {
      ProfitFlowResponse response = ProfitFlowCalculator.summarize(List.of());

      assertThat(response.points()).isEmpty();
      assertThat(response.best()).isNull();
      assertThat(response.worst()).isNull();
      assertThat(response.currentProfitKrw()).isEqualByComparingTo("0");
    }
  }
}
