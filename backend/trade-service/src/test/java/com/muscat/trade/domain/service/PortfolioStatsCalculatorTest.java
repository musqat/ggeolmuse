package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Holding;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.MonthlyProfit;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Sale;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse.SymbolContribution;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.Replay;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.SellResult;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.SymbolTotals;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("계좌 통계 탭 계산")
class PortfolioStatsCalculatorTest {

  private static final LocalDate D1 = LocalDate.of(2024, 9, 2);
  private static final LocalDate D2 = LocalDate.of(2024, 9, 3);
  private static final LocalDate D3 = LocalDate.of(2024, 9, 4);
  private static final LocalDate D4 = LocalDate.of(2024, 9, 5);
  private static final LocalDate TODAY = LocalDate.of(2024, 11, 10);
  private static final LocalDate BEFORE = LocalDate.of(2024, 10, 10);

  private long ids = 1;

  private Trade trade(long accountId, TradeType type, String symbol, String quantity, String price, String fee,
    LocalDate tradeDate, TradeStatus status) {
    return Trade.builder()
      .id(ids++)
      .userId("user-1")
      .accountId(accountId)
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

  private Trade buy(String symbol, String quantity, String price, String fee, LocalDate tradeDate) {
    return trade(3L, TradeType.BUY, symbol, quantity, price, fee, tradeDate, TradeStatus.COMPLETED);
  }

  private Trade sell(String symbol, String quantity, String price, String fee, LocalDate tradeDate) {
    return trade(3L, TradeType.SELL, symbol, quantity, price, fee, tradeDate, TradeStatus.COMPLETED);
  }

  private static DividendPayout payout(String symbol, LocalDate exDate, String net) {
    return DividendPayout.of("user-1", 3L, symbol, exDate,
      new DividendAmounts(new BigDecimal("0.20"), new BigDecimal("10"), new BigDecimal(net), BigDecimal.ZERO,
        new BigDecimal(net)), LocalDateTime.of(2024, 9, 1, 9, 0));
  }

  private static SymbolTotals totals(String symbol, String quantity, String invested, String realized,
    String dividend, String fee) {
    return new SymbolTotals(symbol, new BigDecimal(quantity), new BigDecimal(invested), new BigDecimal(realized),
      new BigDecimal(dividend), new BigDecimal(fee));
  }

  private static NavigableMap<LocalDate, BigDecimal> closes(Object... dateAndClose) {
    NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
    for (int i = 0; i < dateAndClose.length; i += 2) {
      closes.put((LocalDate) dateAndClose[i], new BigDecimal((String) dateAndClose[i + 1]));
    }
    return closes;
  }

  private static void assertTotals(SymbolTotals actual, String quantity, String invested, String realized,
    String dividend, String fee) {
    assertThat(actual.quantity()).isEqualByComparingTo(quantity);
    assertThat(actual.investedUsd()).isEqualByComparingTo(invested);
    assertThat(actual.realizedUsd()).isEqualByComparingTo(realized);
    assertThat(actual.dividendUsd()).isEqualByComparingTo(dividend);
    assertThat(actual.feeUsd()).isEqualByComparingTo(fee);
  }

  @Nested
  @DisplayName("기준 날짜까지 매도 · 종목 값")
  class Replaying {

    @Test
    @DisplayName("기준 날짜까지의 완료 체결로 매도마다 실현 손익 · 줄어든 투자금액을, 종목마다 수량 · 투자금액 · 실현 손익 · 세후 배당 · 수수료 누적을 낸다")
    void replay_SellsAndSymbolTotals() {
      Trade aaplSell = sell("AAPL", "5", "130.00", "0.50", D3);
      Trade msftSell = sell("MSFT", "2", "40.00", "0.00", D3);
      List<Trade> trades = List.of(
        msftSell,
        buy("AAPL", "10", "120.00", "1.00", D2),
        trade(3L, TradeType.SELL, "AAPL", "1", "999.00", "0.00", D3, TradeStatus.PENDING),
        trade(3L, TradeType.BUY, "AAPL", "1", "1.00", "0.00", D3, TradeStatus.CANCELLED),
        buy("AAPL", "1", "200.00", "0.00", D4),
        aaplSell,
        buy("AAPL", "10", "100.00", "1.00", D1),
        buy("MSFT", "2", "50.00", "0.00", D2));

      Replay replay = PortfolioStatsCalculator.replay(trades,
        List.of(payout("AAPL", D2, "3.00"), payout("MSFT", D3, "0.50"), payout("AAPL", D4, "9.00")), D3, 2);

      assertThat(replay.sells()).extracting(SellResult::trade).containsExactly(aaplSell, msftSell);
      assertThat(replay.sells().get(0).realizedUsd()).isEqualByComparingTo("100");
      assertThat(replay.sells().get(0).costUsd()).isEqualByComparingTo("550");
      assertThat(replay.sells().get(1).realizedUsd()).isEqualByComparingTo("-20");
      assertThat(replay.sells().get(1).costUsd()).isEqualByComparingTo("100");
      assertThat(replay.symbols()).containsOnlyKeys("AAPL", "MSFT");
      assertTotals(replay.symbols().get("AAPL"), "15", "1650", "100", "3.00", "2.50");
      assertTotals(replay.symbols().get("MSFT"), "0", "0", "-20", "0.50", "0.00");
      assertThat(replay.symbols().get("AAPL").held()).isTrue();
      assertThat(replay.symbols().get("MSFT").held()).isFalse();
    }

    @Test
    @DisplayName("계좌마다의 종목 값을 종목마다 더한다")
    void merge_SumsBySymbol() {
      Map<String, SymbolTotals> merged = PortfolioStatsCalculator.merge(List.of(
        new Replay(List.of(), Map.of("AAPL", totals("AAPL", "1", "100", "0", "0", "1"),
          "MSFT", totals("MSFT", "2", "80", "5", "0", "0"))),
        new Replay(List.of(), Map.of("AAPL", totals("AAPL", "2", "250", "10", "1", "0.5")))));

      assertThat(merged).containsOnlyKeys("AAPL", "MSFT");
      assertTotals(merged.get("AAPL"), "3", "350", "10", "1", "1.5");
      assertTotals(merged.get("MSFT"), "2", "80", "5", "0", "0");
    }
  }

  @Nested
  @DisplayName("수익 자세히")
  class ProfitDetail {

    private List<ProfitPoint> points(Object... dateAndProfit) {
      List<ProfitPoint> points = new ArrayList<>();
      for (int i = 0; i < dateAndProfit.length; i += 2) {
        points.add(new ProfitPoint((LocalDate) dateAndProfit[i], new BigDecimal((String) dateAndProfit[i + 1]),
          BigDecimal.ZERO));
      }
      return points;
    }

    @Test
    @DisplayName("달마다 수익은 그 달 마지막 점 − 앞 달 마지막 점이고 첫 달은 기간 시작 전 마지막 점을 뺀다")
    void monthly_DifferenceOfMonthEnds() {
      List<ProfitPoint> points = points(LocalDate.of(2024, 8, 29), "700", LocalDate.of(2024, 8, 30), "1000",
        LocalDate.of(2024, 9, 2), "1500", LocalDate.of(2024, 9, 30), "3000", LocalDate.of(2024, 10, 1), "2500",
        LocalDate.of(2024, 10, 15), "4000");

      ProfitDetailResponse response = PortfolioStatsCalculator.profitDetail(points, LocalDate.of(2024, 9, 1),
        List.of(), List.of(), Map.of(), Map.of(), TODAY);

      assertThat(response.monthly()).extracting(MonthlyProfit::month)
        .containsExactly(YearMonth.of(2024, 9), YearMonth.of(2024, 10));
      assertThat(response.monthly()).extracting(MonthlyProfit::profitKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("2000"), new BigDecimal("1000"));
    }

    @Test
    @DisplayName("기간 첫 날의 점은 기간 안에 들고, 그 전날의 점은 같은 달이어도 시작 전 값으로 뺀다")
    void monthly_StartDayBoundary() {
      List<ProfitPoint> points = points(LocalDate.of(2024, 8, 30), "700", LocalDate.of(2024, 9, 1), "1000",
        LocalDate.of(2024, 9, 2), "1500", LocalDate.of(2024, 10, 1), "2000");

      ProfitDetailResponse response = PortfolioStatsCalculator.profitDetail(points, LocalDate.of(2024, 9, 2),
        List.of(), List.of(), Map.of(), Map.of(), TODAY);

      assertThat(response.monthly()).extracting(MonthlyProfit::month)
        .containsExactly(YearMonth.of(2024, 9), YearMonth.of(2024, 10));
      assertThat(response.monthly()).extracting(MonthlyProfit::profitKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("500"), new BigDecimal("500"));
    }

    @Test
    @DisplayName("전체 기간이면 첫 달은 0 에서 늘어난 수익이다")
    void monthly_AllPeriodFromZero() {
      List<ProfitPoint> points = points(LocalDate.of(2024, 8, 30), "-1000", LocalDate.of(2024, 9, 30), "3000");

      ProfitDetailResponse response = PortfolioStatsCalculator.profitDetail(points, null, List.of(), List.of(),
        Map.of(), Map.of(), TODAY);

      assertThat(response.monthly()).extracting(MonthlyProfit::profitKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("-1000"), new BigDecimal("4000"));
    }

    @Test
    @DisplayName("판 거래는 최근 날짜부터 같은 날짜는 체결 ID 큰 것부터, 원화는 판 날 환율로 바꾸고 맨 위에 합을 낸다")
    void profitDetail_SalesNewestFirst() {
      LocalDate tenth = LocalDate.of(2024, 9, 10);
      LocalDate eighteenth = LocalDate.of(2024, 9, 18);
      Trade older = sell("AAPL", "1", "100.00", "0.00", tenth);
      Trade aapl = sell("AAPL", "5", "130.00", "0.50", eighteenth);
      Trade msft = trade(4L, TradeType.SELL, "MSFT", "2", "40.00", "0.00", eighteenth, TradeStatus.COMPLETED);

      ProfitDetailResponse response = PortfolioStatsCalculator.profitDetail(List.of(), null, List.of(
          new SellResult(older, new BigDecimal("35.2034"), new BigDecimal("405")),
          new SellResult(aapl, new BigDecimal("100"), new BigDecimal("550")),
          new SellResult(msft, new BigDecimal("-20"), new BigDecimal("100"))),
        List.of(), Map.of(), Map.of(tenth, new BigDecimal("1300"), eighteenth, new BigDecimal("1330.50")), TODAY);

      assertThat(response.sales()).extracting(Sale::tradeId)
        .containsExactly(msft.getId(), aapl.getId(), older.getId());
      Sale first = response.sales().getFirst();
      assertThat(first.accountId()).isEqualTo(4L);
      assertThat(first.date()).isEqualTo(eighteenth);
      assertThat(first.symbol()).isEqualTo("MSFT");
      assertThat(first.quantity()).isEqualByComparingTo("2");
      assertThat(first.priceUsd()).isEqualByComparingTo("40.00");
      assertThat(first.realizedUsd()).isEqualByComparingTo("-20.00");
      assertThat(first.fxRate()).isEqualByComparingTo("1330.50");
      assertThat(first.realizedKrw()).isEqualByComparingTo("-26610");
      assertThat(first.returnPercent()).isEqualByComparingTo("-20.00");
      Sale last = response.sales().getLast();
      assertThat(last.realizedUsd()).isEqualByComparingTo("35.20");
      assertThat(last.realizedKrw()).isEqualByComparingTo("45764");
      assertThat(last.returnPercent()).isEqualByComparingTo("8.69");
      assertThat(response.sales().get(1).returnPercent()).isEqualByComparingTo("18.18");
      assertThat(response.realizedKrw()).isEqualByComparingTo("152204");
      assertThat(response.holdings()).isEmpty();
      assertThat(response.unrealizedKrw()).isEqualByComparingTo("0");
      assertThat(response.monthly()).isEmpty();
    }

    @Test
    @DisplayName("들고 있는 종목은 오늘 이하 가장 늦은 종가와 오늘 환율로 평가해 평가 손익 원화 큰 것부터, 종가가 없으면 −투자금액이다")
    void profitDetail_HoldingsByUnrealized() {
      LocalDate closeDate = LocalDate.of(2024, 11, 8);

      ProfitDetailResponse response = PortfolioStatsCalculator.profitDetail(List.of(), null, List.of(),
        List.of(totals("XYZ", "2", "100", "0", "0", "0"), totals("AAPL", "15", "1650", "100", "3", "2.5"),
          totals("MSFT", "3", "300", "0", "0", "0")),
        Map.of("AAPL", closes(LocalDate.of(2024, 11, 7), "120.004", closeDate, "125.40",
            LocalDate.of(2024, 11, 11), "999.00"),
          "MSFT", closes(closeDate, "110")),
        Map.of(TODAY, new BigDecimal("1400")), TODAY);

      assertThat(response.holdings()).extracting(Holding::symbol).containsExactly("AAPL", "MSFT", "XYZ");
      Holding aapl = response.holdings().getFirst();
      assertThat(aapl.quantity()).isEqualByComparingTo("15");
      assertThat(aapl.averagePriceUsd()).isEqualByComparingTo("110.00");
      assertThat(aapl.closeUsd()).isEqualByComparingTo("125.40");
      assertThat(aapl.closeDate()).isEqualTo(closeDate);
      assertThat(aapl.unrealizedUsd()).isEqualByComparingTo("231.00");
      assertThat(aapl.unrealizedKrw()).isEqualByComparingTo("323400");
      assertThat(aapl.returnPercent()).isEqualByComparingTo("14.00");
      Holding xyz = response.holdings().getLast();
      assertThat(xyz.averagePriceUsd()).isEqualByComparingTo("50.00");
      assertThat(xyz.closeUsd()).isNull();
      assertThat(xyz.closeDate()).isNull();
      assertThat(xyz.unrealizedUsd()).isEqualByComparingTo("-100.00");
      assertThat(xyz.unrealizedKrw()).isEqualByComparingTo("-140000");
      assertThat(xyz.returnPercent()).isEqualByComparingTo("-100.00");
      assertThat(response.unrealizedKrw()).isEqualByComparingTo("225400");
    }

    @Test
    @DisplayName("판 날 환율이 없으면 환율은 비우고 원화는 0, 줄어든 투자금액이 0 이면 수익률은 0 이다")
    void profitDetail_MissingFxAndZeroCost() {
      Trade uncovered = sell("TSLA", "2", "50.00", "0.00", D1);

      ProfitDetailResponse response = PortfolioStatsCalculator.profitDetail(List.of(), null,
        List.of(new SellResult(uncovered, BigDecimal.ZERO, BigDecimal.ZERO)), List.of(), Map.of(), Map.of(), TODAY);

      Sale sale = response.sales().getFirst();
      assertThat(sale.fxRate()).isNull();
      assertThat(sale.realizedKrw()).isEqualByComparingTo("0");
      assertThat(sale.returnPercent()).isEqualByComparingTo("0");
    }
  }

  @Nested
  @DisplayName("종목별 기여")
  class Contributions {

    @Test
    @DisplayName("칸마다 기간 끝 값 − 시작 전 값을 각자 그 날짜 환율로 내고, 모든 칸이 0 이고 들고 있지 않은 종목은 뺀다")
    void symbolContributions_EndMinusBefore() {
      Map<String, SymbolTotals> end = Map.of(
        "AAPL", totals("AAPL", "15", "1650", "100", "3", "2.5"),
        "MSFT", totals("MSFT", "0", "0", "-20", "0.5", "0"),
        "KO", totals("KO", "0", "0", "10", "0", "0"),
        "NVDA", totals("NVDA", "1", "100", "0", "0", "0"),
        "TSLA", totals("TSLA", "0", "0", "0", "0", "0"));
      Map<String, SymbolTotals> previous = Map.of(
        "AAPL", totals("AAPL", "20", "2200", "0", "3", "2"),
        "MSFT", totals("MSFT", "2", "100", "0", "0", "0"),
        "KO", totals("KO", "0", "0", "10", "0", "0"),
        "TSLA", totals("TSLA", "0", "0", "0", "0", "0"));
      Map<String, NavigableMap<LocalDate, BigDecimal>> closes = Map.of(
        "AAPL", closes(BEFORE, "115", LocalDate.of(2024, 11, 8), "125.40"),
        "MSFT", closes(LocalDate.of(2024, 10, 9), "45"),
        "NVDA", closes(LocalDate.of(2024, 11, 8), "100"));

      SymbolContributionResponse response = PortfolioStatsCalculator.symbolContributions(end, previous, closes,
        Map.of(TODAY, new BigDecimal("1400"), BEFORE, new BigDecimal("1300")), TODAY, BEFORE);

      assertThat(response.symbols()).extracting(SymbolContribution::symbol)
        .containsExactly("AAPL", "KO", "NVDA", "MSFT");
      SymbolContribution aapl = response.symbols().getFirst();
      assertThat(aapl.realizedKrw()).isEqualByComparingTo("140000");
      assertThat(aapl.unrealizedKrw()).isEqualByComparingTo("193400");
      assertThat(aapl.dividendKrw()).isEqualByComparingTo("300");
      assertThat(aapl.feeKrw()).isEqualByComparingTo("900");
      assertThat(aapl.totalKrw()).isEqualByComparingTo("332800");
      assertThat(aapl.sharePercent()).isEqualByComparingTo("95.60");
      assertThat(aapl.held()).isTrue();
      SymbolContribution msft = response.symbols().getLast();
      assertThat(msft.realizedKrw()).isEqualByComparingTo("-28000");
      assertThat(msft.unrealizedKrw()).isEqualByComparingTo("13000");
      assertThat(msft.dividendKrw()).isEqualByComparingTo("700");
      assertThat(msft.totalKrw()).isEqualByComparingTo("-14300");
      assertThat(msft.sharePercent()).isEqualByComparingTo("-4.11");
      assertThat(msft.held()).isFalse();
      assertThat(response.symbols().get(1).totalKrw()).isEqualByComparingTo("1000");
      assertThat(response.symbols().get(1).sharePercent()).isEqualByComparingTo("0.29");
      assertThat(response.symbols().get(2).totalKrw()).isEqualByComparingTo("0");
      assertThat(response.symbols().get(2).held()).isTrue();
      assertThat(response.totalKrw()).isEqualByComparingTo("319500");
    }

    @Test
    @DisplayName("시작 전날 환율이 없으면 시작 전 값도 기간 끝 환율로 바꿔 뺀다")
    void symbolContributions_MissingBeforeRateUsesEndRate() {
      Map<String, SymbolTotals> end = Map.of("AAPL", totals("AAPL", "10", "1000", "50", "2", "1"));
      Map<String, SymbolTotals> previous = Map.of("AAPL", totals("AAPL", "10", "1000", "20", "2", "1"));
      Map<String, NavigableMap<LocalDate, BigDecimal>> closes = Map.of(
        "AAPL", closes(BEFORE, "110", LocalDate.of(2024, 11, 8), "120"));

      SymbolContributionResponse response = PortfolioStatsCalculator.symbolContributions(end, previous, closes,
        Map.of(TODAY, new BigDecimal("1400")), TODAY, BEFORE);

      SymbolContribution aapl = response.symbols().getFirst();
      assertThat(aapl.realizedKrw()).isEqualByComparingTo("42000");
      assertThat(aapl.unrealizedKrw()).isEqualByComparingTo("140000");
      assertThat(aapl.dividendKrw()).isEqualByComparingTo("0");
      assertThat(aapl.feeKrw()).isEqualByComparingTo("0");
      assertThat(response.totalKrw()).isEqualByComparingTo("182000");
    }

    @Test
    @DisplayName("전체 기간이면 종목 합이 수익 흐름 마지막 점의 수익과 같다")
    void symbolContributions_AllPeriodMatchesFlow() {
      List<Trade> trades = List.of(
        buy("AAPL", "10", "100.00", "1.00", D1),
        buy("MSFT", "2", "50.00", "0.00", D2),
        sell("AAPL", "4", "110.00", "0.50", D3));
      List<DividendPayout> payouts = List.of(payout("AAPL", D4, "1.20"));
      Map<String, NavigableMap<LocalDate, BigDecimal>> closes = Map.of(
        "AAPL", closes(D1, "101.00", D2, "105.00", D3, "110.00", D4, "108.00"),
        "MSFT", closes(D2, "50.00", D4, "55.00"));
      Map<LocalDate, BigDecimal> rates = Map.of(D1, new BigDecimal("1300"), D2, new BigDecimal("1310"),
        D3, new BigDecimal("1320"), D4, new BigDecimal("1320"));
      List<DailyProfit> flow = ProfitFlowCalculator.calculate(trades, payouts, closes, rates,
        List.of(D1, D2, D3, D4), 2);

      SymbolContributionResponse response = PortfolioStatsCalculator.symbolContributions(
        PortfolioStatsCalculator.replay(trades, payouts, D4, 2).symbols(), Map.of(), closes, rates, D4, null);

      assertThat(response.totalKrw()).isEqualByComparingTo(flow.getLast().profitKrw());
      assertThat(response.symbols()).extracting(SymbolContribution::totalKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("115764"), new BigDecimal("13200"));
    }

    @Test
    @DisplayName("종목이 없으면 빈 목록 · 합 0 이고, 모든 종목 합이 0 이면 비율은 0 이다")
    void symbolContributions_EmptyAndZeroShare() {
      SymbolContributionResponse empty = PortfolioStatsCalculator.symbolContributions(Map.of(), Map.of(), Map.of(),
        Map.of(), TODAY, null);
      SymbolContributionResponse flat = PortfolioStatsCalculator.symbolContributions(
        Map.of("NVDA", totals("NVDA", "1", "100", "0", "0", "0")), Map.of(),
        Map.of("NVDA", closes(LocalDate.of(2024, 11, 8), "100")), Map.of(TODAY, new BigDecimal("1400")), TODAY, null);

      assertThat(empty.symbols()).isEmpty();
      assertThat(empty.totalKrw()).isEqualByComparingTo("0");
      assertThat(flat.symbols()).hasSize(1);
      assertThat(flat.symbols().getFirst().sharePercent()).isEqualByComparingTo("0");
    }
  }
}
