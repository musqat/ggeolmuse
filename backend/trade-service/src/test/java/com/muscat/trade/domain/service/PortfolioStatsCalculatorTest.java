package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.DividendStatsResponse;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.MonthlyDividend;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.Payout;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.SymbolDividend;
import com.muscat.trade.domain.dto.response.FxStatsResponse;
import com.muscat.trade.domain.dto.response.FxStatsResponse.Direction;
import com.muscat.trade.domain.dto.response.FxStatsResponse.Exchange;
import com.muscat.trade.domain.dto.response.MarketComparisonResponse;
import com.muscat.trade.domain.dto.response.MarketComparisonResponse.MarketPoint;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Holding;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.MonthlyProfit;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Sale;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse.SymbolContribution;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse.SymbolTrades;
import com.muscat.trade.domain.dto.response.TradingHabitsResponse.WeekdayTrades;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.AccountFlow;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.Replay;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.SellResult;
import com.muscat.trade.domain.service.PortfolioStatsCalculator.SymbolTotals;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import com.muscat.trade.infra.client.dto.ExchangeRecordDto;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;
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

      ProfitDetailResponse response = PortfolioStatsCalculator.profitDetail(points, LocalDate.of(2024, 8, 30),
        List.of(), List.of(), Map.of(), Map.of(), TODAY);

      assertThat(response.monthly()).extracting(MonthlyProfit::month)
        .containsExactly(YearMonth.of(2024, 8), YearMonth.of(2024, 9), YearMonth.of(2024, 10));
      assertThat(response.monthly()).extracting(MonthlyProfit::profitKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("300"), new BigDecimal("2000"), new BigDecimal("1000"));
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
    @DisplayName("들고 있는 종목은 기간 끝 이하 가장 늦은 종가와 기간 끝 환율로 평가해 평가 손익 원화 큰 것부터, 종가가 없으면 −투자금액이다")
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

  @Nested
  @DisplayName("배당")
  class Dividends {

    private static final LocalDate START = BEFORE.plusDays(1);

    private static DividendPayout paid(long accountId, String symbol, LocalDate exDate, String perShare,
      String shares, String gross, String tax, String net) {
      return DividendPayout.of("user-1", accountId, symbol, exDate,
        new DividendAmounts(new BigDecimal(perShare), new BigDecimal(shares), new BigDecimal(gross),
          new BigDecimal(tax), new BigDecimal(net)), LocalDateTime.of(2024, 9, 1, 9, 0));
    }

    @Test
    @DisplayName("배당락일이 기간 첫 날부터 오늘까지인 배당만 남기고 수량이 0 인 몫은 뺀다")
    void paidInPeriod_ExDateInPeriodAndShares() {
      DividendPayout first = paid(3L, "KO", START, "0.485", "10", "4.85", "0.73", "4.12");
      DividendPayout last = paid(3L, "KO", TODAY, "0.485", "10", "4.85", "0.73", "4.12");
      DividendPayout earlier = paid(3L, "KO", BEFORE, "0.485", "10", "4.85", "0.73", "4.12");
      List<DividendPayout> payouts = List.of(earlier, first,
        paid(3L, "AAPL", LocalDate.of(2024, 10, 20), "0.25", "0", "0.00", "0.00", "0.00"), last,
        paid(3L, "KO", TODAY.plusDays(1), "0.485", "10", "4.85", "0.73", "4.12"));

      assertThat(PortfolioStatsCalculator.paidInPeriod(payouts, START, TODAY)).containsExactly(first, last);
      assertThat(PortfolioStatsCalculator.paidInPeriod(payouts, null, TODAY)).containsExactly(earlier, first, last);
    }

    @Test
    @DisplayName("원화는 배당락일 환율로 바꾸고 달마다 · 종목마다 합과 맨 위 합을 줄 값의 합으로 낸다")
    void dividends_MonthlySymbolsAndPayouts() {
      LocalDate koFirst = LocalDate.of(2024, 9, 13);
      LocalDate aaplDay = LocalDate.of(2024, 9, 20);
      LocalDate koSecond = LocalDate.of(2024, 10, 15);
      LocalDate msftDay = LocalDate.of(2024, 11, 1);
      List<DividendPayout> paid = List.of(
        paid(3L, "KO", koFirst, "0.485", "10", "4.85", "0.73", "4.12"),
        paid(3L, "AAPL", aaplDay, "0.25", "4", "1.00", "0.15", "0.85"),
        paid(4L, "KO", koSecond, "0.485", "20", "9.70", "1.46", "8.24"),
        paid(3L, "KO", koSecond, "0.485", "2", "0.97", "0.15", "0.82"),
        paid(3L, "MSFT", msftDay, "0.83", "1", "0.83", "0.12", "0.71"));

      DividendStatsResponse response = PortfolioStatsCalculator.dividends(paid, Map.of(
        koFirst, new BigDecimal("1330.5"), aaplDay, new BigDecimal("1320"), koSecond, new BigDecimal("1350")));

      assertThat(response.netKrw()).isEqualByComparingTo("18835");
      assertThat(response.netUsd()).isEqualByComparingTo("14.74");
      assertThat(response.grossUsd()).isEqualByComparingTo("17.35");
      assertThat(response.taxUsd()).isEqualByComparingTo("2.61");
      assertThat(response.payoutCount()).isEqualTo(5);
      assertThat(response.monthly()).extracting(MonthlyDividend::month)
        .containsExactly(YearMonth.of(2024, 9), YearMonth.of(2024, 10), YearMonth.of(2024, 11));
      assertThat(response.monthly()).extracting(MonthlyDividend::netUsd)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("4.97"), new BigDecimal("9.06"), new BigDecimal("0.71"));
      assertThat(response.monthly()).extracting(MonthlyDividend::netKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("6604"), new BigDecimal("12231"), new BigDecimal("0"));
      assertThat(response.symbols()).extracting(SymbolDividend::symbol).containsExactly("KO", "AAPL", "MSFT");
      SymbolDividend ko = response.symbols().getFirst();
      assertThat(ko.netUsd()).isEqualByComparingTo("13.18");
      assertThat(ko.netKrw()).isEqualByComparingTo("17713");
      assertThat(ko.payoutCount()).isEqualTo(3);
      assertThat(ko.sharePercent()).isEqualByComparingTo("94.04");
      assertThat(response.symbols().get(1).sharePercent()).isEqualByComparingTo("5.96");
      assertThat(response.symbols().getLast().sharePercent()).isEqualByComparingTo("0");
      assertThat(response.payouts()).extracting(Payout::exDate)
        .containsExactly(msftDay, koSecond, koSecond, aaplDay, koFirst);
      assertThat(response.payouts()).extracting(Payout::accountId).containsExactly(3L, 3L, 4L, 3L, 3L);
      Payout msft = response.payouts().getFirst();
      assertThat(msft.fxRate()).isNull();
      assertThat(msft.netKrw()).isEqualByComparingTo("0");
      Payout first = response.payouts().getLast();
      assertThat(first.symbol()).isEqualTo("KO");
      assertThat(first.shares()).isEqualByComparingTo("10").hasScaleOf(6);
      assertThat(first.dividendPerShareUsd()).isEqualByComparingTo("0.485").hasScaleOf(6);
      assertThat(first.grossUsd()).isEqualByComparingTo("4.85");
      assertThat(first.taxUsd()).isEqualByComparingTo("0.73");
      assertThat(first.netUsd()).isEqualByComparingTo("4.12");
      assertThat(first.fxRate()).isEqualByComparingTo("1330.50").hasScaleOf(2);
      assertThat(first.netKrw()).isEqualByComparingTo("5482");
    }

    @Test
    @DisplayName("배당이 없으면 빈 목록 · 합 0 이고, 세후 원화 합이 0 이면 비율은 0 이다")
    void dividends_EmptyAndZeroShare() {
      DividendStatsResponse empty = PortfolioStatsCalculator.dividends(List.of(), Map.of());
      DividendStatsResponse noRate = PortfolioStatsCalculator.dividends(
        List.of(paid(3L, "MSFT", TODAY, "0.83", "1", "0.83", "0.12", "0.71")), Map.of());

      assertThat(empty.netKrw()).isEqualByComparingTo("0");
      assertThat(empty.netUsd()).isEqualByComparingTo("0");
      assertThat(empty.payoutCount()).isZero();
      assertThat(empty.monthly()).isEmpty();
      assertThat(empty.symbols()).isEmpty();
      assertThat(empty.payouts()).isEmpty();
      assertThat(noRate.symbols().getFirst().sharePercent()).isEqualByComparingTo("0");
    }
  }

  @Nested
  @DisplayName("환율 효과")
  class FxEffect {

    private static final LocalDate START = BEFORE.plusDays(1);
    private static final Map<LocalDate, BigDecimal> RATES = Map.of(TODAY, new BigDecimal("1385.2"),
      BEFORE, new BigDecimal("1340"));

    private static ExchangeRecordDto krwToUsd(long accountId, LocalDateTime at, String krw, String usd,
      String rate) {
      return new ExchangeRecordDto(accountId, at, "KRW", "USD", new BigDecimal(krw), new BigDecimal(usd),
        new BigDecimal(rate));
    }

    private static ExchangeRecordDto usdToKrw(long accountId, LocalDateTime at, String usd, String krw,
      String rate) {
      return new ExchangeRecordDto(accountId, at, "USD", "KRW", new BigDecimal(usd), new BigDecimal(krw),
        new BigDecimal(rate));
    }

    private static List<ExchangeRecordDto> exchanges() {
      return List.of(
        krwToUsd(3L, LocalDateTime.of(2024, 9, 2, 10, 15, 30), "1300000.00", "1000.00", "1300.000000"),
        krwToUsd(3L, LocalDateTime.of(2024, 10, 20, 9, 0), "1350000.00", "1000.00", "1350.000000"),
        usdToKrw(4L, LocalDateTime.of(2024, 11, 1, 14, 0), "300.00", "414000.00", "1380.000000"));
    }

    @Test
    @DisplayName("효과는 기간 끝 효과 − 시작 전날 효과이고 기간 안 환전만 최근부터 지금 환율 기준 효과와 함께 낸다")
    void fx_EndMinusDayBeforePeriod() {
      FxStatsResponse response = PortfolioStatsCalculator.fx(exchanges(), START, TODAY, RATES);

      assertThat(response.fxEffectKrw()).isEqualByComparingTo("78840");
      assertThat(response.currentRate()).isEqualByComparingTo("1385.20").hasScaleOf(2);
      assertThat(response.averageRate()).isEqualByComparingTo("1315.29");
      assertThat(response.heldUsd()).isEqualByComparingTo("1700.00");
      assertThat(response.krwToUsdKrw()).isEqualByComparingTo("1350000");
      assertThat(response.usdToKrwUsd()).isEqualByComparingTo("300.00");
      assertThat(response.exchangeCount()).isEqualTo(2);
      assertThat(response.exchanges()).extracting(Exchange::date)
        .containsExactly(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 10, 20));
      Exchange sold = response.exchanges().getFirst();
      assertThat(sold.accountId()).isEqualTo(4L);
      assertThat(sold.direction()).isEqualTo(Direction.USD_TO_KRW);
      assertThat(sold.krwAmount()).isEqualByComparingTo("414000").hasScaleOf(0);
      assertThat(sold.usdAmount()).isEqualByComparingTo("300.00");
      assertThat(sold.rate()).isEqualByComparingTo("1380.00").hasScaleOf(2);
      assertThat(sold.effectKrw()).isEqualByComparingTo("-1560");
      Exchange bought = response.exchanges().getLast();
      assertThat(bought.direction()).isEqualTo(Direction.KRW_TO_USD);
      assertThat(bought.krwAmount()).isEqualByComparingTo("1350000");
      assertThat(bought.usdAmount()).isEqualByComparingTo("1000.00");
      assertThat(bought.effectKrw()).isEqualByComparingTo("35200");
    }

    @Test
    @DisplayName("기간 첫 날 환전은 기간 안 환전으로, 시작 전날 환전은 시작 전 효과로 센다")
    void fx_PeriodStartBoundary() {
      List<ExchangeRecordDto> exchanges = List.of(
        krwToUsd(3L, BEFORE.atTime(23, 59, 59), "1300000.00", "1000.00", "1300.000000"),
        krwToUsd(3L, START.atStartOfDay(), "1350000.00", "1000.00", "1350.000000"));

      FxStatsResponse response = PortfolioStatsCalculator.fx(exchanges, START, TODAY, RATES);

      assertThat(response.exchanges()).extracting(Exchange::date).containsExactly(START);
      assertThat(response.exchanges().getFirst().effectKrw()).isEqualByComparingTo("35200");
      assertThat(response.fxEffectKrw()).isEqualByComparingTo("80400");
      assertThat(response.exchangeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 기간이면 효과는 모든 환전의 지금 환율 기준 효과 합이다")
    void fx_AllPeriodSumsRows() {
      FxStatsResponse response = PortfolioStatsCalculator.fx(exchanges(), null, TODAY, RATES);

      assertThat(response.fxEffectKrw()).isEqualByComparingTo("118840");
      assertThat(response.exchanges()).extracting(Exchange::effectKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("-1560"), new BigDecimal("35200"), new BigDecimal("85200"));
      assertThat(response.krwToUsdKrw()).isEqualByComparingTo("2650000");
      assertThat(response.exchangeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 시각 환전은 뒤 기록이 앞에 오고, 효과는 원 단위로 반올림한 환전마다 효과의 합이다")
    void fx_SameTimeOrderAndRounding() {
      LocalDateTime at = LocalDateTime.of(2024, 10, 20, 9, 0);
      ExchangeRecordDto first = krwToUsd(3L, at, "1000000.40", "740.33", "1351.270000");
      ExchangeRecordDto second = krwToUsd(5L, at, "500000.00", "370.01", "1351.270000");

      FxStatsResponse response = PortfolioStatsCalculator.fx(List.of(first, second), START, TODAY, RATES);

      assertThat(response.exchanges()).extracting(Exchange::accountId).containsExactly(5L, 3L);
      assertThat(response.exchanges().getLast().krwAmount()).isEqualByComparingTo("1000000");
      assertThat(response.exchanges().getLast().effectKrw()).isEqualByComparingTo("25119");
      assertThat(response.exchanges().getFirst().effectKrw()).isEqualByComparingTo("12554");
      assertThat(response.fxEffectKrw()).isEqualByComparingTo("37673");
    }

    @Test
    @DisplayName("판 달러가 받은 달러보다 많으면 들고 있는 달러는 0 · 평균 환율은 비우고, 기록이 없으면 효과 0 · 빈 목록이다")
    void fx_SoldMoreThanBoughtAndEmpty() {
      FxStatsResponse oversold = PortfolioStatsCalculator.fx(List.of(
          krwToUsd(3L, LocalDateTime.of(2024, 9, 2, 10, 0), "130000.00", "100.00", "1300.000000"),
          usdToKrw(3L, LocalDateTime.of(2024, 11, 1, 10, 0), "300.00", "414000.00", "1380.000000")),
        null, TODAY, RATES);
      FxStatsResponse empty = PortfolioStatsCalculator.fx(List.of(), START, TODAY, RATES);

      assertThat(oversold.heldUsd()).isEqualByComparingTo("0");
      assertThat(oversold.averageRate()).isNull();
      assertThat(oversold.fxEffectKrw()).isEqualByComparingTo("6960");
      assertThat(empty.fxEffectKrw()).isEqualByComparingTo("0");
      assertThat(empty.heldUsd()).isEqualByComparingTo("0");
      assertThat(empty.averageRate()).isNull();
      assertThat(empty.currentRate()).isEqualByComparingTo("1385.20");
      assertThat(empty.exchanges()).isEmpty();
      assertThat(empty.exchangeCount()).isZero();
      assertThat(empty.krwToUsdKrw()).isEqualByComparingTo("0");
      assertThat(empty.usdToKrwUsd()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("시작 전날 환율이 없으면 시작 전 효과는 0, 오늘 환율이 없으면 지금 환율은 비우고 효과는 모두 0 이다")
    void fx_MissingRates() {
      List<ExchangeRecordDto> before = List.of(
        krwToUsd(3L, LocalDateTime.of(2024, 9, 2, 10, 0), "1300000.00", "1000.00", "1300.000000"));

      FxStatsResponse noBeforeRate = PortfolioStatsCalculator.fx(before, START, TODAY,
        Map.of(TODAY, new BigDecimal("1385.2")));
      FxStatsResponse noTodayRate = PortfolioStatsCalculator.fx(exchanges(), START, TODAY,
        Map.of(BEFORE, new BigDecimal("1340")));

      assertThat(noBeforeRate.fxEffectKrw()).isEqualByComparingTo("85200");
      assertThat(noBeforeRate.exchanges()).isEmpty();
      assertThat(noTodayRate.currentRate()).isNull();
      assertThat(noTodayRate.fxEffectKrw()).isEqualByComparingTo("0");
      assertThat(noTodayRate.exchanges()).extracting(Exchange::effectKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }

  @Nested
  @DisplayName("거래 패턴")
  class Habits {

    private static final LocalDate START = BEFORE.plusDays(1);

    private TradingHabitsResponse habits(List<Trade> trades) {
      return PortfolioStatsCalculator.habits(List.of(trades), START, TODAY, 2);
    }

    private Trade otherAccount(TradeType type, String symbol, String quantity, String price, LocalDate tradeDate) {
      return trade(4L, type, symbol, quantity, price, "0.00", tradeDate, TradeStatus.COMPLETED);
    }

    @Test
    @DisplayName("기간 첫 날부터 오늘까지 거래일의 완료 체결만 매수 · 매도 수 · 거래한 날 · 요일마다 체결 수로 세고 요일은 월요일부터 일요일까지 7개다")
    void habits_CountsTradesInPeriod() {
      TradingHabitsResponse response = habits(List.of(
        buy("AAPL", "10", "100.00", "0.00", BEFORE),
        buy("AAPL", "5", "110.00", "0.00", START),
        buy("MSFT", "2", "50.00", "0.00", START),
        sell("AAPL", "3", "120.00", "0.00", LocalDate.of(2024, 10, 14)),
        trade(3L, TradeType.SELL, "AAPL", "1", "120.00", "0.00", LocalDate.of(2024, 10, 15), TradeStatus.PENDING),
        trade(3L, TradeType.BUY, "AAPL", "1", "120.00", "0.00", LocalDate.of(2024, 10, 16), TradeStatus.CANCELLED),
        buy("AAPL", "1", "130.00", "0.00", LocalDate.of(2024, 10, 19)),
        buy("MSFT", "1", "55.00", "0.00", TODAY)));

      assertThat(response.buyCount()).isEqualTo(4);
      assertThat(response.sellCount()).isEqualTo(1);
      assertThat(response.tradingDays()).isEqualTo(4);
      assertThat(response.weekdays()).extracting(WeekdayTrades::weekday).containsExactly(DayOfWeek.values());
      assertThat(response.weekdays()).extracting(WeekdayTrades::count).containsExactly(1, 0, 0, 0, 2, 1, 1);
    }

    @Test
    @DisplayName("평균 보유 기간은 기간 안 매도마다 판 수량을 계좌 · 종목마다 먼저 산 매수부터 짝짓고 기간 앞 매도가 쓴 수량은 빼며 짝지은 수량으로 가중 평균해 소수 첫째에서 반올림한다")
    void habits_AverageHoldingDaysFirstInFirstOut() {
      List<Trade> account = List.of(
        buy("AAPL", "4", "100.00", "0.00", LocalDate.of(2024, 9, 2)),
        buy("AAPL", "6", "100.00", "0.00", LocalDate.of(2024, 10, 2)),
        sell("AAPL", "2", "100.00", "0.00", LocalDate.of(2024, 10, 5)),
        sell("AAPL", "5", "100.00", "0.00", LocalDate.of(2024, 10, 21)),
        buy("AAPL", "3", "100.00", "0.00", LocalDate.of(2024, 10, 25)),
        sell("AAPL", "4", "100.00", "0.00", LocalDate.of(2024, 11, 4)),
        buy("MSFT", "2", "50.00", "0.00", LocalDate.of(2024, 9, 2)));
      List<Trade> other = List.of(
        otherAccount(TradeType.BUY, "MSFT", "2", "50.00", LocalDate.of(2024, 10, 14)),
        otherAccount(TradeType.SELL, "MSFT", "2", "50.00", LocalDate.of(2024, 10, 21)));

      TradingHabitsResponse response = PortfolioStatsCalculator.habits(List.of(account, other), START, TODAY, 2);

      assertThat(response.averageHoldingDays()).isEqualByComparingTo("25.3").hasScaleOf(1);
    }

    @Test
    @DisplayName("보유보다 많이 판 매도는 들고 있던 수량만 짝짓고, 들고 있던 것이 없는 매도뿐이면 평균 보유 기간은 비우고 패로 센다")
    void habits_OversoldAndUncoveredSells() {
      TradingHabitsResponse oversold = habits(List.of(
        buy("AAPL", "2", "100.00", "0.00", LocalDate.of(2024, 10, 14)),
        sell("AAPL", "5", "110.00", "0.00", LocalDate.of(2024, 10, 21)),
        buy("AAPL", "1", "100.00", "0.00", LocalDate.of(2024, 10, 22)),
        sell("AAPL", "1", "100.00", "0.00", LocalDate.of(2024, 11, 1))));
      TradingHabitsResponse uncovered = habits(List.of(
        sell("AAPL", "1", "110.00", "0.00", LocalDate.of(2024, 10, 21))));

      assertThat(oversold.averageHoldingDays()).isEqualByComparingTo("8.0");
      assertThat(oversold.winCount()).isEqualTo(1);
      assertThat(uncovered.averageHoldingDays()).isNull();
      assertThat(uncovered.sellCount()).isEqualTo(1);
      assertThat(uncovered.winCount()).isZero();
      assertThat(uncovered.lossCount()).isEqualTo(1);
      assertThat(uncovered.winRatePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("수익 낸 매도(승)는 소수 둘째에서 반올림한 실현 손익이 0 보다 큰 기간 안 매도이고 수수료는 실현 손익에 넣지 않으며 승률은 승 수 ÷ 매도 수 × 100 이다")
    void habits_WinsAndLosses() {
      TradingHabitsResponse response = habits(List.of(
        buy("AAPL", "2", "100.00", "0.00", LocalDate.of(2024, 10, 1)),
        sell("AAPL", "1", "200.00", "0.00", BEFORE),
        sell("AAPL", "1", "110.00", "25.00", LocalDate.of(2024, 10, 15)),
        buy("MSFT", "3", "100.00", "0.00", LocalDate.of(2024, 10, 1)),
        sell("MSFT", "1", "100.00", "0.00", LocalDate.of(2024, 10, 16)),
        buy("KO", "1", "50.00", "0.00", LocalDate.of(2024, 10, 1)),
        sell("KO", "1", "40.00", "0.00", LocalDate.of(2024, 10, 17))));

      assertThat(response.sellCount()).isEqualTo(3);
      assertThat(response.winCount()).isEqualTo(1);
      assertThat(response.lossCount()).isEqualTo(2);
      assertThat(response.winRatePercent()).isEqualByComparingTo("33.33").hasScaleOf(2);
    }

    @Test
    @DisplayName("많이 거래한 종목은 기간 안 체결 수 많은 순 5개이고 같으면 체결 금액 큰 순, 그다음 종목 이름 순이며 체결 금액은 수수료를 넣지 않은 수량 × 체결가 합이다")
    void habits_TopSymbols() {
      TradingHabitsResponse response = habits(List.of(
        buy("AMZN", "1", "10.00", "0.00", BEFORE),
        buy("AMZN", "1", "10.00", "0.00", BEFORE),
        buy("AMZN", "1", "10.00", "0.00", BEFORE),
        buy("AMZN", "1", "150.00", "0.00", START),
        buy("AAPL", "2", "100.00", "1.00", START),
        buy("AAPL", "1", "110.00", "1.00", START),
        sell("AAPL", "1", "120.00", "1.00", LocalDate.of(2024, 10, 14)),
        buy("MSFT", "1", "300.00", "0.00", START),
        sell("MSFT", "1", "310.00", "0.00", LocalDate.of(2024, 10, 14)),
        buy("NVDA", "1", "100.00", "0.00", START),
        buy("NVDA", "1", "81.50", "0.00", START),
        buy("KO", "2", "60.00", "0.00", START),
        buy("KO", "0.5", "123.00", "0.00", START),
        buy("TSLA", "0.333333", "600.00", "0.00", START)));

      assertThat(response.topSymbols()).extracting(SymbolTrades::symbol)
        .containsExactly("AAPL", "MSFT", "KO", "NVDA", "TSLA");
      assertThat(response.topSymbols()).extracting(SymbolTrades::amountUsd)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("430.00"), new BigDecimal("610.00"), new BigDecimal("181.50"),
          new BigDecimal("181.50"), new BigDecimal("200.00"));
      SymbolTrades aapl = response.topSymbols().getFirst();
      assertThat(aapl.tradeCount()).isEqualTo(3);
      assertThat(aapl.buyCount()).isEqualTo(2);
      assertThat(aapl.sellCount()).isEqualTo(1);
      assertThat(response.topSymbols().getLast().amountUsd()).hasScaleOf(2);
    }

    @Test
    @DisplayName("기간 안 체결이 없으면 수는 0 · 평균 보유 기간과 승률은 비우고 요일 7개는 모두 0 · 많이 거래한 종목은 빈 목록이다")
    void habits_NoTradesInPeriod() {
      TradingHabitsResponse empty = new TradingHabitsResponse(0, 0, 0, null, 0, 0, null,
        Stream.of(DayOfWeek.values()).map(weekday -> new WeekdayTrades(weekday, 0)).toList(), List.of());

      assertThat(PortfolioStatsCalculator.habits(List.of(), START, TODAY, 2)).isEqualTo(empty);
      assertThat(habits(List.of(
        buy("AAPL", "1", "100.00", "0.00", D1),
        sell("AAPL", "1", "110.00", "0.00", BEFORE)))).isEqualTo(empty);
    }
  }

  @Nested
  @DisplayName("시장 비교")
  class Market {

    private final Map<LocalDate, BigDecimal> rates = Map.of(D1, new BigDecimal("1300"), D2, new BigDecimal("1310"),
      D3, new BigDecimal("1320"), D4, new BigDecimal("1330"));

    private static List<ProfitPoint> flowPoints(LocalDate... dates) {
      return Stream.of(dates).map(date -> new ProfitPoint(date, BigDecimal.ZERO, BigDecimal.ZERO)).toList();
    }

    private static MarketComparisonResponse market(List<AccountFlow> accounts,
      NavigableMap<LocalDate, BigDecimal> closes, Map<LocalDate, BigDecimal> rates, LocalDate start) {
      return PortfolioStatsCalculator.market(accounts, closes, rates, start, 2);
    }

    private static void assertPoints(MarketComparisonResponse response, Object... dateAndProfit) {
      List<MarketPoint> expected = new ArrayList<>();
      for (int i = 0; i < dateAndProfit.length; i += 2) {
        expected.add(new MarketPoint((LocalDate) dateAndProfit[i], new BigDecimal((String) dateAndProfit[i + 1])));
      }
      assertThat(response.symbol()).isEqualTo("SPY");
      assertThat(response.points()).extracting(MarketPoint::date)
        .containsExactlyElementsOf(expected.stream().map(MarketPoint::date).toList());
      assertThat(response.points()).extracting(MarketPoint::profitKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactlyElementsOf(expected.stream().map(MarketPoint::profitKrw).toList());
      assertThat(response.currentProfitKrw()).isEqualByComparingTo(expected.getLast().profitKrw());
    }

    @Test
    @DisplayName("매수마다 그 거래일 SPY 종가로 체결 금액 + 수수료만큼 사고 매도마다 줄어든 투자금액 ÷ 매도 직전 계좌 투자금액 비율만큼 팔아 점 날짜마다 (수량 × 종가 − 원가 + 실현 손익) × 그 날짜 환율을 낸다")
    void market_BuysSameDollarsAndSellsInvestedRatio() {
      List<Trade> trades = List.of(
        sell("AAPL", "5", "120.00", "0.50", D3),
        trade(3L, TradeType.SELL, "AAPL", "5", "999.00", "0.00", D3, TradeStatus.PENDING),
        trade(3L, TradeType.BUY, "MSFT", "1", "1.00", "0.00", D2, TradeStatus.CANCELLED),
        buy("MSFT", "4", "62.50", "0.00", D2),
        buy("AAPL", "10", "100.00", "1.00", D1));

      MarketComparisonResponse response = market(List.of(new AccountFlow(trades, flowPoints(D1, D2, D3, D4))),
        closes(D1, "500.50", D2, "400.00", D3, "450.00", D4, "480.00"), rates, null);

      assertPoints(response, D1, "0", D2, "-263310", D3, "-92070", D4, "-29925");
    }

    @Test
    @DisplayName("SPY 만 종가에 사고판 계좌는 수익 흐름 계산과 같은 값이다")
    void market_SpyOnlyMatchesFlow() {
      List<Trade> trades = List.of(
        buy("SPY", "4", "500.00", "0.00", D1),
        sell("SPY", "1", "520.00", "0.00", D3));
      NavigableMap<LocalDate, BigDecimal> spy = closes(D1, "500.00", D2, "510.00", D3, "520.00", D4, "515.00");
      List<DailyProfit> flow = ProfitFlowCalculator.calculate(trades, List.of(), Map.of("SPY", spy), rates,
        List.of(D1, D2, D3, D4), 2);

      MarketComparisonResponse response = market(List.of(new AccountFlow(trades, flowPoints(D1, D2, D3, D4))), spy,
        rates, null);

      assertThat(response.points()).extracting(MarketPoint::profitKrw)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactlyElementsOf(flow.stream().map(DailyProfit::profitKrw).toList());
      assertPoints(response, D1, "0", D2, "52400", D3, "105600", D4, "86450");
    }

    @Test
    @DisplayName("보유보다 많이 판 매도는 그 종목 투자금액 전부의 비율만큼 팔고, 매도 직전 계좌 투자금액이 0 이면 SPY 를 팔지 않는다")
    void market_UncoveredSells() {
      List<Trade> trades = List.of(
        buy("AAPL", "2", "100.00", "0.00", D1),
        buy("MSFT", "2", "100.00", "0.00", D1),
        sell("AAPL", "3", "110.00", "0.00", D2),
        sell("MSFT", "2", "100.00", "0.00", D3),
        sell("AAPL", "1", "100.00", "0.00", D4));
      Map<LocalDate, BigDecimal> flat = Map.of(D1, new BigDecimal("1300"), D2, new BigDecimal("1300"),
        D3, new BigDecimal("1300"), D4, new BigDecimal("1300"));

      MarketComparisonResponse response = market(List.of(new AccountFlow(trades, flowPoints(D1, D2, D3, D4))),
        closes(D1, "100.00", D2, "120.00", D3, "130.00", D4, "140.00"), flat, null);

      assertPoints(response, D1, "0", D2, "104000", D3, "130000", D4, "130000");
    }

    @Test
    @DisplayName("전체 계좌는 계좌마다 셈한 점을 날짜마다 더하고 점이 없는 날은 그 계좌의 직전 점을 쓰며 기간 첫 날 앞 점은 뺀다")
    void market_SumsAccountsByDate() {
      Trade otherBuy = trade(4L, TradeType.BUY, "MSFT", "2", "50.00", "0.00", D3, TradeStatus.COMPLETED);

      MarketComparisonResponse response = market(List.of(
          new AccountFlow(List.of(buy("AAPL", "1", "100.00", "0.00", D1)),
            PortfolioStatsCalculator.periodPoints(flowPoints(D1, D2, D4), D3)),
          new AccountFlow(List.of(otherBuy), PortfolioStatsCalculator.periodPoints(flowPoints(D3, D4), D3))),
        closes(D1, "100.00", D2, "110.00", D3, "125.00", D4, "130.00"), rates, D3);

      assertPoints(response, D3, "13100", D4, "45220");
    }

    @Test
    @DisplayName("기간 점은 기간 첫 날부터의 점과 그 앞 마지막 점이고 전체 기간이면 모든 점이다")
    void periodPoints_KeepsLastPointBeforeStart() {
      assertThat(PortfolioStatsCalculator.periodPoints(flowPoints(D1, D2, D4), D3)).extracting(ProfitPoint::date)
        .containsExactly(D2, D4);
      assertThat(PortfolioStatsCalculator.periodPoints(flowPoints(D3, D4), D3)).extracting(ProfitPoint::date)
        .containsExactly(D3, D4);
      assertThat(PortfolioStatsCalculator.periodPoints(flowPoints(D1, D2), D3)).extracting(ProfitPoint::date)
        .containsExactly(D2);
      assertThat(PortfolioStatsCalculator.periodPoints(flowPoints(D1, D2), null)).extracting(ProfitPoint::date)
        .containsExactly(D1, D2);
    }

    @Test
    @DisplayName("체결일 · 점 날짜 이하 SPY 종가나 점 날짜의 환율이 없거나 기간 안 점이 없으면 빈 점 · 0 이다")
    void market_MissingCloseOrRateOrPoints() {
      List<Trade> trades = List.of(buy("AAPL", "1", "100.00", "0.00", D2));
      MarketComparisonResponse empty = new MarketComparisonResponse("SPY", List.of(), BigDecimal.ZERO);

      assertThat(market(List.of(new AccountFlow(trades, flowPoints(D2, D3))), closes(D3, "100.00"), rates, null))
        .isEqualTo(empty);
      assertThat(market(List.of(new AccountFlow(trades, flowPoints(D1, D2))), closes(D2, "100.00"), rates, null))
        .isEqualTo(empty);
      assertThat(market(List.of(new AccountFlow(trades, flowPoints(D2, D3))), closes(D2, "100.00"),
        Map.of(D2, new BigDecimal("1300")), null)).isEqualTo(empty);
      assertThat(market(List.of(new AccountFlow(trades, flowPoints(D2))), closes(D2, "100.00"), rates, D3))
        .isEqualTo(empty);
      assertThat(market(List.of(), closes(D2, "100.00"), rates, null)).isEqualTo(empty);
    }
  }
}
