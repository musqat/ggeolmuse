package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioAllocation;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.PortfolioRebalance;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.PortfolioStrategy;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioEngine 여러 종목을 현금 하나로 사고 평가")
class PortfolioEngineTest {

  private static final LocalDate START = LocalDate.of(2024, 3, 4);
  private static final LocalDate APR = LocalDate.of(2024, 4, 4);
  private static final LocalDate MAY = LocalDate.of(2024, 5, 6);
  private static final BigDecimal FX = new BigDecimal("1300");

  @Mock
  private MarketDataClientWrapper marketData;

  private PortfolioEngine engine;

  @BeforeEach
  void setUp() {
    engine = new PortfolioEngine(marketData);
    lenient().when(marketData.defaultFxRate()).thenReturn(new BigDecimal("1350"));
    lenient().when(marketData.getDividendHistory(anyString(), anyString(), anyString())).thenReturn(List.of());
  }

  // 그 날짜 일봉(시가 · 고가 · 저가 · 종가 · 수정 종가 같음)
  private static OHLCPriceDto candle(String symbol, LocalDate date, String close) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(symbol, date, price, price, price, price, price, 1_000L, "USD", true);
  }

  // 그 날짜 시가 · 고가 · 저가 · 종가(수정 종가는 종가) 일봉
  private static OHLCPriceDto bar(String symbol, LocalDate date, String open, String high, String low, String close) {
    return new OHLCPriceDto(symbol, date, new BigDecimal(open), new BigDecimal(high), new BigDecimal(low),
      new BigDecimal(close), new BigDecimal(close), 1_000L, "USD", true);
  }

  private static LocalDate day(int offset) {
    return START.plusDays(offset);
  }

  private void prices(String symbol, OHLCPriceDto... candles) {
    given(marketData.getOHLCPriceRange(eq(symbol), anyString(), anyString())).willReturn(List.of(candles));
  }

  // 그 종목 배당(배당락일 · 주당 USD)
  private void dividends(String symbol, DividendDto... dividends) {
    given(marketData.getDividendHistory(eq(symbol), anyString(), anyString())).willReturn(List.of(dividends));
  }

  private static DividendDto dividend(String symbol, LocalDate exDate, String amount) {
    return new DividendDto(symbol, exDate, exDate, null, amount == null ? null : new BigDecimal(amount), "USD", "test");
  }

  private static PortfolioAsset asset(String symbol, String amount) {
    return selling(symbol, amount, null, null);
  }

  // 익절 · 손절 비율이 있는 금액 줄
  private static PortfolioAsset selling(String symbol, String amount, String takeProfitRate, String stopLossRate) {
    return new PortfolioAsset(symbol, new BigDecimal(amount), null,
      takeProfitRate == null ? null : new BigDecimal(takeProfitRate),
      stopLossRate == null ? null : new BigDecimal(stopLossRate), null);
  }

  // 줄 전략이 있는 금액 줄
  private static PortfolioAsset planned(String symbol, String amount, PortfolioStrategyType type) {
    return planned(symbol, amount, null, new PortfolioStrategy(type, null, null, null, null, null));
  }

  // 줄 전략 · 익절 비율이 있는 금액 줄
  private static PortfolioAsset planned(String symbol, String amount, String takeProfitRate,
    PortfolioStrategy strategy) {
    return new PortfolioAsset(symbol, new BigDecimal(amount), null,
      takeProfitRate == null ? null : new BigDecimal(takeProfitRate), null, strategy);
  }

  // 이동평균 교차(짧은 · 긴 기간)
  private static PortfolioStrategy cross(int shortWindow, int longWindow) {
    return new PortfolioStrategy(PortfolioStrategyType.MA_CROSS, shortWindow, longWindow, null, null, null);
  }

  // 이동평균 밴드(평균 기간 · 위 · 아래 비율)
  private static PortfolioStrategy band(int window, String upperRate, String lowerRate) {
    return new PortfolioStrategy(PortfolioStrategyType.MA_BAND, null, null, window, new BigDecimal(upperRate),
      new BigDecimal(lowerRate));
  }

  // 시작일 전 3거래일 · 3월 4일 10 달러, 3월 5일 골든 크로스(12.5) · 3월 7일 데드 크로스(7.5)인 짧은 2 · 긴 3 거래일 일봉 앞부분
  private static List<OHLCPriceDto> crossing(String symbol) {
    return List.of(candle(symbol, day(-3), "10"), candle(symbol, day(-2), "10"), candle(symbol, day(-1), "10"),
      candle(symbol, day(0), "10"), candle(symbol, day(1), "12.5"), candle(symbol, day(2), "12.5"),
      candle(symbol, day(3), "7.5"));
  }

  private void prices(String symbol, List<OHLCPriceDto> head, OHLCPriceDto... tail) {
    given(marketData.getOHLCPriceRange(eq(symbol), anyString(), anyString()))
      .willReturn(Stream.concat(head.stream(), Stream.of(tail)).toList());
  }

  // 시작일 ~ 종료일 · 매수 · 현재 환율 1300 고정 · 수수료 없음
  private static PortfolioRequest.PortfolioRequestBuilder request(LocalDate end, PortfolioAsset... assets) {
    return PortfolioRequest.builder().startDate(START).endDate(end).assets(List.of(assets))
      .purchaseFxRate(FX).currentFxRate(FX);
  }

  // 적립식 매달(투자일 4일) 시세: 3월 4일 · 4월 4일 · 5월 6일(5월 4일 토요일 다음 거래일)
  private void monthly(String symbol, String march, String april, String may) {
    prices(symbol, candle(symbol, START, march), candle(symbol, APR, april), candle(symbol, MAY, may));
  }

  private static String plain(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  // 종목 · 이유 · 거래일 · 이유 글 · 가격 · 주식 수 · 넣은 원화
  private static List<Tuple> trades(PortfolioEngine.Result result) {
    return result.transactions().stream()
      .map(tx -> tuple(tx.getSymbol(), tx.getReason(), tx.tradedOn(), tx.getTrigger(), plain(tx.getPrice()),
        plain(tx.getShares()), plain(tx.getAmount())))
      .toList();
  }

  private static List<Tuple> daily(PortfolioEngine.Result result) {
    return result.performance().dailyValues().stream()
      .map(value -> tuple(value.date(), plain(value.valueUsd()), plain(value.valueKrw())))
      .toList();
  }

  private static List<Tuple> positions(PortfolioEngine.Result result) {
    return result.positions().stream()
      .map(position -> tuple(position.symbol(), plain(position.plannedKrw()), plain(position.shares()),
        plain(position.closeUsd()), plain(position.valueKrw())))
      .toList();
  }

  @Nested
  @DisplayName("한 번에 사기")
  class LumpSum {

    @Test
    @DisplayName("줄마다 기간 안 그 종목 첫 거래일에 금액 전부로 사고 날마다 일봉이 없는 종목은 앞 가장 늦은 종가로 합쳐 평가한다")
    void buysEachOnItsFirstTradingDay() {
      // AAA 는 이틀째 일봉 없음, BBB 는 이틀째 상장
      prices("AAA", candle("AAA", day(0), "10"), candle("AAA", day(2), "12"));
      prices("BBB", candle("BBB", day(1), "20"), candle("BBB", day(2), "25"));

      PortfolioEngine.Result result = engine.run(
        request(day(2), asset("aaa ", "1300000"), asset("BBB", "2600000")).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, day(0), "한 번에 매수", "10", "100", "1300000"),
        tuple("BBB", TradeReason.BUY, day(1), "한 번에 매수", "20", "100", "2600000"));
      assertThat(daily(result)).containsExactly(
        tuple(day(0), "1000", "1300000"), tuple(day(1), "3000", "3900000"), tuple(day(2), "3700", "4810000"));
      assertThat(positions(result)).containsExactly(
        tuple("AAA", "1300000", "100", "12", "1560000"), tuple("BBB", "2600000", "100", "25", "3250000"));
      assertThat(result.startDate()).isEqualTo(day(0));
      assertThat(result.valuationDate()).isEqualTo(day(2));
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("3900000");
      assertThat(plain(result.stockValueKrw())).isEqualTo("4810000");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("4810000");
      assertThat(result.performance().metricsUsd()).isNotNull();
    }

    @Test
    @DisplayName("비율 방식은 총액 × 비율 ÷ 100 을 원 단위로 반올림한 금액으로 산다")
    void weightAllocation() {
      prices("AAA", candle("AAA", day(0), "10"));
      prices("BBB", candle("BBB", day(0), "10"));

      PortfolioEngine.Result result = engine.run(request(day(0),
        new PortfolioAsset("AAA", null, new BigDecimal("33.33"), null, null, null),
        new PortfolioAsset("BBB", null, new BigDecimal("66.67"), null, null, null))
        .allocation(PortfolioAllocation.WEIGHT).totalAmount(new BigDecimal("1000001")).build());

      assertThat(result.transactions()).extracting(tx -> plain(tx.getAmount())).containsExactly("333300", "666701");
      assertThat(result.positions()).extracting(position -> plain(position.plannedKrw()))
        .containsExactly("333300", "666701");
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("1000001");
    }

    @Test
    @DisplayName("줄 하나라도 기간 안 쓸 수 있는 일봉이 없으면 DATA_NOT_FOUND")
    void noPricesForOneLine() {
      prices("AAA", candle("AAA", day(0), "10"));
      prices("BBB", candle("BBB", day(0), "0"));

      assertThatThrownBy(() -> engine.run(request(day(0), asset("AAA", "1300000"), asset("BBB", "1300000")).build()))
        .isInstanceOf(BacktestException.class)
        .hasMessage("BBB 의 기간 안 시세가 없습니다")
        .extracting("code")
        .isEqualTo(BacktestResponse.DATA_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("적립식")
  class Dca {

    @Test
    @DisplayName("줄마다 회차일에 회당 금액으로 사고 넣은 돈은 새로 넣은 원화 합이다")
    void buysEachRound() {
      monthly("AAA", "10", "20", "25");
      monthly("BBB", "50", "40", "100");

      PortfolioEngine.Result result = engine.run(request(MAY, asset("AAA", "130000"), asset("BBB", "260000"))
        .method(PortfolioMethod.DCA).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.DCA_BUY, START, "월정액", "10", "10", "130000"),
        tuple("BBB", TradeReason.DCA_BUY, START, "월정액", "50", "4", "260000"),
        tuple("AAA", TradeReason.DCA_BUY, APR, "월정액", "20", "5", "130000"),
        tuple("BBB", TradeReason.DCA_BUY, APR, "월정액", "40", "5", "260000"),
        tuple("AAA", TradeReason.DCA_BUY, MAY, "월정액", "25", "4", "130000"),
        tuple("BBB", TradeReason.DCA_BUY, MAY, "월정액", "100", "2", "260000"));
      // 5월 4일 계획 회차를 5월 6일에 삼
      assertThat(result.transactions().get(4).getDate()).isEqualTo(LocalDate.of(2024, 5, 4));
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("1170000");
      // AAA 19주 × 25 + BBB 11주 × 100 = 1575달러
      assertThat(plain(result.totalAssetKrw())).isEqualTo("2047500");
    }

    @Test
    @DisplayName("주기 · 투자일 · 투자 주기를 주면 그대로 회차를 짜고 종료일이 오늘 뒤면 오늘 평가한다")
    void scheduleOptions() {
      LocalDate march5 = LocalDate.of(2024, 3, 5);
      prices("AAA", candle("AAA", START, "10"), candle("AAA", march5, "10"), candle("AAA", LocalDate.of(2024, 4, 5), "10"),
        candle("AAA", MAY, "10"));

      PortfolioEngine.Result result = engine.run(request(LocalDate.of(2099, 12, 31), asset("AAA", "13000"))
        .method(PortfolioMethod.DCA).frequency(DcaFrequency.MONTHLY).firstPurchaseDate(START).purchaseDay(5)
        .investmentInterval(2).build());

      // 3월 5일 · 5월 5일(일요일 다음 거래일 5월 6일)
      assertThat(result.transactions()).extracting(tx -> tuple(tx.getDate(), tx.tradedOn()))
        .containsExactly(tuple(march5, march5), tuple(LocalDate.of(2024, 5, 5), MAY));
      assertThat(result.valuationDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("현금 배당은 공용 현금으로 두고 다음 회차 매수가 먼저 쓰고 모자란 만큼만 새 돈을 넣는다")
    void cashDividendFundsNextRound() {
      monthly("AAA", "10", "20", "25");
      monthly("BBB", "50", "40", "100");
      // 4월 1일 배당락 주당 1달러 × 10주, 원천징수 15% 뒤 8.50달러
      dividends("AAA", dividend("AAA", LocalDate.of(2024, 4, 1), "1"));

      PortfolioEngine.Result result = engine.run(request(MAY, asset("AAA", "130000"), asset("BBB", "260000"))
        .method(PortfolioMethod.DCA).dividendTaxRate(new BigDecimal("0.15")).build());

      assertThat(result.dividends()).extracting(d -> tuple(d.symbol(), d.exDate(), plain(d.amountUsd()), plain(d.fxRate())))
        .containsExactly(tuple("AAA", LocalDate.of(2024, 4, 1), "8.5", "1300"));
      StrategyTransaction april = result.transactions().get(2);
      // 8.50달러 + 118,950원(91.50달러)으로 100달러어치
      assertThat(tuple(plain(april.getCashUsed()), plain(april.getAmount()), plain(april.getShares())))
        .isEqualTo(tuple("8.5", "118950", "5"));
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("1158950");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
    }

    @Test
    @DisplayName("공용 현금이 회당 금액보다 많으면 새 돈 없이 현금으로만 산다")
    void cashCoversRound() {
      monthly("AAA", "10", "10", "10");
      dividends("AAA", dividend("AAA", LocalDate.of(2024, 4, 1), "20"));

      PortfolioEngine.Result result = engine.run(request(MAY, asset("AAA", "13000")).method(PortfolioMethod.DCA).build());

      assertThat(result.transactions()).extracting(tx -> tuple(plain(tx.getAmount()), plain(tx.getCashUsed())))
        .containsExactly(tuple("13000", null), tuple("0", "10"), tuple("0", "10"));
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("13000");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("39000");
    }

    @Test
    @DisplayName("기간 안에 산 것이 없으면 합 0 · 일별 가치 없음 · 지표 null 이고 평가일 기본 환율 날을 남긴다")
    void noPurchases() {
      // 첫 매수일 3월 9일 뒤로 일봉이 없어 회차 없음
      prices("AAA", candle("AAA", day(0), "10"), candle("AAA", day(1), "10"));
      given(marketData.getBulkFxRates(any())).willReturn(Map.of());

      PortfolioEngine.Result result = engine.run(PortfolioRequest.builder().startDate(START).endDate(day(6))
        .assets(List.of(asset("AAA", "130000"))).method(PortfolioMethod.DCA).firstPurchaseDate(day(5))
        .periodEnd(PeriodEnd.SELL_AT_END).build());

      assertThat(result.transactions()).isEmpty();
      assertThat(result.performance().dailyValues()).isEmpty();
      assertThat(result.performance().metricsKrw()).isNull();
      assertThat(positions(result)).containsExactly(tuple("AAA", "130000", "0", "10", "0"));
      assertThat(plain(result.totalAssetKrw())).isEqualTo("0");
      assertThat(result.defaultFxDates()).containsExactly(day(6));
      assertThat(plain(result.currentFxRate())).isEqualTo("1350");
    }
  }

  @Nested
  @DisplayName("줄마다 한 번에 사기 · 적립식")
  class LineMethods {

    @Test
    @DisplayName("한 번에 사기 요청에서 적립식 줄은 요청의 적립식 주기로 회차마다 사고 나머지 줄은 첫 거래일에 한 번 산다")
    void dcaLineInLumpSumRequest() {
      monthly("AAA", "10", "20", "25");
      monthly("BBB", "50", "40", "100");

      PortfolioEngine.Result result = engine.run(request(MAY, asset("AAA", "130000"),
        planned("BBB", "260000", PortfolioStrategyType.DCA)).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, START, "한 번에 매수", "10", "10", "130000"),
        tuple("BBB", TradeReason.DCA_BUY, START, "월정액", "50", "4", "260000"),
        tuple("BBB", TradeReason.DCA_BUY, APR, "월정액", "40", "5", "260000"),
        tuple("BBB", TradeReason.DCA_BUY, MAY, "월정액", "100", "2", "260000"));
      assertThat(result.positions()).extracting(PortfolioEngine.Position::strategyType)
        .containsExactly(PortfolioStrategyType.LUMP_SUM, PortfolioStrategyType.DCA);
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("910000");
    }

    @Test
    @DisplayName("적립식 요청에서 한 번에 사기 줄은 첫 매수일과 상관없이 그 종목 첫 거래일에 금액 전부로 한 번만 산다")
    void lumpSumLineInDcaRequest() {
      monthly("AAA", "10", "20", "25");
      monthly("BBB", "50", "40", "100");

      PortfolioEngine.Result result = engine.run(request(MAY, planned("AAA", "130000", PortfolioStrategyType.LUMP_SUM),
        asset("BBB", "260000")).method(PortfolioMethod.DCA).firstPurchaseDate(APR).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, START, "한 번에 매수", "10", "10", "130000"),
        tuple("BBB", TradeReason.DCA_BUY, APR, "월정액", "40", "5", "260000"),
        tuple("BBB", TradeReason.DCA_BUY, MAY, "월정액", "100", "2", "260000"));
      assertThat(result.positions()).extracting(PortfolioEngine.Position::strategyType)
        .containsExactly(PortfolioStrategyType.LUMP_SUM, PortfolioStrategyType.DCA);
    }
  }

  @Nested
  @DisplayName("줄마다 이동평균 · 밴드")
  class SignalLines {

    private static final LocalDate APR1 = LocalDate.of(2024, 4, 1);

    @Test
    @DisplayName("이동평균 교차 줄은 첫 골든 크로스에 줄 금액을 새로 넣어 사고 데드 크로스에 판 돈을 줄 몫으로 두어 회차 매수에 쓰지 않고 다음 골든 크로스에 전부로 산다")
    void crossLineKeepsItsOwnCash() {
      // 3월 11일 7.5 · 3월 12일 골든 크로스(12)
      prices("AAA", crossing("AAA"), candle("AAA", day(4), "7.5"), candle("AAA", day(7), "7.5"),
        candle("AAA", day(8), "12"));
      // BBB 매주 적립식(3월 4일 · 3월 11일), 3월 9일 배당락 주당 1달러
      prices("BBB", candle("BBB", day(0), "10"), candle("BBB", day(5), "10"), candle("BBB", day(7), "10"),
        candle("BBB", day(8), "10"));
      dividends("BBB", dividend("BBB", day(5), "1"));

      PortfolioEngine.Result result = engine.run(request(day(8), planned("AAA", "1300000", null, cross(2, 3)),
        asset("BBB", "1300000")).method(PortfolioMethod.DCA).frequency(DcaFrequency.WEEKLY).build());

      assertThat(trades(result)).containsExactly(
        tuple("BBB", TradeReason.DCA_BUY, day(0), "매주 정액", "10", "100", "1300000"),
        tuple("AAA", TradeReason.STRATEGY_BUY, day(1), "골든 크로스", "12.5", "80", "1300000"),
        tuple("AAA", TradeReason.STRATEGY_SELL, day(3), "데드 크로스", "7.5", "-80", "0"),
        tuple("BBB", TradeReason.DCA_BUY, day(7), "매주 정액", "10", "100", "1170000"),
        tuple("AAA", TradeReason.STRATEGY_BUY, day(8), "골든 크로스", "12", "50", "0"));
      // BBB 회차는 배당 100 달러만 쓰고 AAA 판 돈 600 달러는 다음 골든 크로스에 전부
      assertThat(result.transactions()).extracting(tx -> plain(tx.getCashUsed()))
        .containsExactly(null, null, null, "100", "600");
      assertThat(plain(result.transactions().get(2).getRealizedProfit())).isEqualTo("-400");
      // 첫 골든 크로스 전에는 AAA 줄 금액이 넣은 돈 · 평가에 없음
      assertThat(daily(result).getFirst()).isEqualTo(tuple(day(0), "1000", "1300000"));
      assertThat(result.startDate()).isEqualTo(day(0));
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("3770000");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
      assertThat(positions(result)).containsExactly(
        tuple("AAA", "1300000", "50", "12", "780000"), tuple("BBB", "1300000", "200", "10", "2600000"));
      assertThat(result.positions()).extracting(PortfolioEngine.Position::strategyType)
        .containsExactly(PortfolioStrategyType.MA_CROSS, PortfolioStrategyType.DCA);
    }

    @Test
    @DisplayName("리밸런싱은 신호 줄과 그 줄 몫 현금을 빼고 나머지 줄을 그 줄들의 처음 비중으로 나누며 신호 줄 일봉이 없는 날에도 한다")
    void rebalanceSkipsSignalLine() {
      prices("AAA", crossing("AAA"));
      prices("BBB", candle("BBB", day(0), "10"), candle("BBB", APR1, "20"));
      prices("CCC", candle("CCC", day(0), "10"), candle("CCC", APR1, "10"));

      PortfolioEngine.Result result = engine.run(request(APR1, planned("AAA", "2600000", null, cross(2, 3)),
        asset("BBB", "1300000"), asset("CCC", "1300000")).rebalance(PortfolioRebalance.MONTHLY).build());

      // 4월 1일 BBB 2000 + CCC 1000 달러를 반씩, AAA 판 돈 1200 달러는 그대로
      assertThat(trades(result)).containsExactly(
        tuple("BBB", TradeReason.BUY, day(0), "한 번에 매수", "10", "100", "1300000"),
        tuple("CCC", TradeReason.BUY, day(0), "한 번에 매수", "10", "100", "1300000"),
        tuple("AAA", TradeReason.STRATEGY_BUY, day(1), "골든 크로스", "12.5", "160", "2600000"),
        tuple("AAA", TradeReason.STRATEGY_SELL, day(3), "데드 크로스", "7.5", "-160", "0"),
        tuple("BBB", TradeReason.REBALANCE_SELL, APR1, "리밸런싱 매도", "20", "-25", "0"),
        tuple("CCC", TradeReason.REBALANCE_BUY, APR1, "리밸런싱 매수", "10", "50", "0"));
      assertThat(plain(result.cashUsd())).isEqualTo("1200");
    }

    @Test
    @DisplayName("밴드 줄은 수정 종가가 위 선 위인 날 사고 아래 선 아래인 날 전량 팔며 신호 줄만 있으면 리밸런싱을 켜도 사고팔지 않는다")
    void bandLine() {
      prices("AAA", candle("AAA", day(-1), "10"), candle("AAA", day(0), "10"), candle("AAA", day(1), "12.5"),
        candle("AAA", day(2), "12.5"), candle("AAA", APR1, "10"));

      PortfolioEngine.Result result = engine.run(request(APR1, planned("AAA", "1300000", null, band(2, "0.05", "0.05")))
        .rebalance(PortfolioRebalance.MONTHLY).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.STRATEGY_BUY, day(1), "밴드 위 매수", "12.5", "80", "1300000"),
        tuple("AAA", TradeReason.STRATEGY_SELL, APR1, "밴드 아래 매도", "10", "-80", "0"));
      assertThat(plain(result.cashUsd())).isEqualTo("800");
      assertThat(result.positions()).extracting(PortfolioEngine.Position::strategyType)
        .containsExactly(PortfolioStrategyType.MA_BAND);
    }

    @Test
    @DisplayName("밴드 줄도 익절 · 손절에 걸리면 판 돈을 줄 몫으로 두고 위 선 아래로 한 번 내려온 뒤 다시 위 선을 넘은 날 산다")
    void exitRuleWaitsForNextBuySignal() {
      // 3월 6일 위 선 위에서 익절, 3월 7일 위 선 위 · 3월 8일 위 선 아래 · 3월 9일 다시 위
      prices("AAA", candle("AAA", day(-1), "10"), candle("AAA", day(0), "10"), candle("AAA", day(1), "12.5"),
        bar("AAA", day(2), "13", "14.5", "13", "14"), candle("AAA", day(3), "16"), candle("AAA", day(4), "16"),
        candle("AAA", day(5), "22"));

      PortfolioEngine.Result result = engine.run(
        request(day(5), planned("AAA", "1300000", "0.1", band(2, "0.05", "0.5"))).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.STRATEGY_BUY, day(1), "밴드 위 매수", "12.5", "80", "1300000"),
        tuple("AAA", TradeReason.TAKE_PROFIT, day(2), "10.0%익절", "13.75", "-80", "0"),
        tuple("AAA", TradeReason.STRATEGY_BUY, day(5), "밴드 위 매수", "22", "50", "0"));
      assertThat(plain(result.transactions().get(2).getCashUsed())).isEqualTo("1100");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
    }

    @Test
    @DisplayName("양도세를 공용 현금으로 다 못 내면 신호 줄 몫에서 마저 내고 그 줄은 남은 몫 전부로 다음 매수 신호에 산다")
    void taxFromSignalLineCash() {
      LocalDate due = LocalDate.of(2025, 5, 1);
      LocalDate next = LocalDate.of(2025, 5, 2);
      // 3월 5일 골든 크로스(12.5) · 3월 8일 데드 크로스(20) · 2025년 5월 2일 골든 크로스(40)
      prices("AAA", candle("AAA", day(-3), "10"), candle("AAA", day(-2), "10"), candle("AAA", day(-1), "10"),
        candle("AAA", day(0), "10"), candle("AAA", day(1), "12.5"), candle("AAA", day(2), "30"),
        candle("AAA", day(3), "30"), candle("AAA", day(4), "20"), candle("AAA", due, "20"), candle("AAA", next, "40"));
      prices("BBB", candle("BBB", day(0), "10"), candle("BBB", day(5), "10"), candle("BBB", due, "10"),
        candle("BBB", next, "10"));
      dividends("BBB", dividend("BBB", day(5), "1"));

      PortfolioEngine.Result result = engine.run(request(next, planned("AAA", "13000000", null, cross(2, 3)),
        asset("BBB", "1300000")).capitalGainsTax(true).build());

      assertThat(result.transactions()).extracting(tx -> tuple(tx.getSymbol(), tx.getReason(), tx.tradedOn()))
        .containsExactly(tuple("BBB", TradeReason.BUY, day(0)), tuple("AAA", TradeReason.STRATEGY_BUY, day(1)),
          tuple("AAA", TradeReason.STRATEGY_SELL, day(4)), tuple(null, TradeReason.TAX_PAYMENT, due),
          tuple("AAA", TradeReason.STRATEGY_BUY, next));
      // AAA 원가 1300만원 대비 2080만원, 공제 뒤 22% 116만 6천원 ÷ 1300 을 BBB 배당 100 달러와 AAA 판 돈 16000 달러에서
      assertThat(plain(result.transactions().get(3).getTaxPaid())).isEqualTo("896.92");
      assertThat(plain(result.transactions().get(4).getCashUsed())).isEqualTo("15203.08");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
    }

    @Test
    @DisplayName("양도세가 신호 줄 몫을 다 쓰면 그 줄은 다음 매수 신호에 사지 않는다")
    void taxTakesWholeSignalLineCash() {
      LocalDate due = LocalDate.of(2025, 5, 1);
      LocalDate next = LocalDate.of(2025, 5, 2);
      // BBB 는 3월 5일 90% 익절 뒤 4월 리밸런싱이 판 돈 전부로 다시 삼, AAA 는 2025년 5월 2일 골든 크로스(12)
      prices("BBB", candle("BBB", day(0), "10"), bar("BBB", day(1), "10", "19.5", "10", "19"),
        candle("BBB", APR1, "19"), candle("BBB", due, "19"), candle("BBB", next, "19"));
      prices("AAA", crossing("AAA"), candle("AAA", day(4), "7.5"), candle("AAA", due, "7.5"), candle("AAA", next, "12"));

      PortfolioEngine.Result result = engine.run(request(next, selling("BBB", "13000000", "0.9", null),
        planned("AAA", "1300000", null, cross(2, 3))).rebalance(PortfolioRebalance.MONTHLY).capitalGainsTax(true)
        .build());

      // 2024년 실현 이익 1170만원 - 52만원, 공제 뒤 22% 190만 9600원 가운데 현금 600 달러(AAA 판 돈)만 냄
      assertThat(result.transactions()).extracting(tx -> tuple(tx.getSymbol(), tx.getReason(), tx.tradedOn()))
        .containsExactly(tuple("BBB", TradeReason.BUY, day(0)), tuple("BBB", TradeReason.TAKE_PROFIT, day(1)),
          tuple("AAA", TradeReason.STRATEGY_BUY, day(1)), tuple("AAA", TradeReason.STRATEGY_SELL, day(3)),
          tuple("BBB", TradeReason.REBALANCE_BUY, APR1), tuple(null, TradeReason.TAX_PAYMENT, due));
      assertThat(plain(result.transactions().get(5).getTaxPaid())).isEqualTo("600");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
      assertThat(result.positions()).extracting(position -> plain(position.shares())).containsExactly("1000", "0");
    }

    @Test
    @DisplayName("신호 매도 날이 기본 환율 날이면 양도세를 켰을 때만 기본 환율 날로 남긴다")
    void saleDefaultFxOnlyWithTax() {
      prices("AAA", crossing("AAA"), candle("AAA", day(4), "7.5"));
      // 3월 7일(데드 크로스) 환율 없음
      given(marketData.getBulkFxRates(any())).willReturn(Map.of(day(0).toString(), FX, day(1).toString(), FX,
        day(2).toString(), FX, day(4).toString(), FX));
      given(marketData.getFxRate(day(4).toString())).willReturn(new FxRateDto(day(4), FX));
      PortfolioRequest.PortfolioRequestBuilder request = PortfolioRequest.builder().startDate(START).endDate(day(4))
        .assets(List.of(planned("AAA", "1300000", null, cross(2, 3))));

      assertThat(engine.run(request.build()).defaultFxDates()).isEmpty();
      assertThat(engine.run(request.capitalGainsTax(true).build()).defaultFxDates()).containsExactly(day(3));
    }
  }

  @Nested
  @DisplayName("배당")
  class Dividends {

    @Test
    @DisplayName("재투자하면 배당락일 전날까지 든 주식의 배당으로 그 종목을 그 날짜 종가에 산다")
    void reinvestsAtExDateClose() {
      prices("AAA", candle("AAA", day(0), "10"), candle("AAA", day(1), "11"), candle("AAA", day(2), "12.5"),
        candle("AAA", day(3), "12.5"));
      // 산 날 배당락은 받지 않고, 금액 없는 배당은 건너뜀
      dividends("AAA", dividend("AAA", day(0), "1"), dividend("AAA", day(1), null), dividend("AAA", day(2), "0.5"));

      PortfolioEngine.Result result = engine.run(request(day(3), asset("AAA", "1300000")).reinvestDividends(true).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, day(0), "한 번에 매수", "10", "100", "1300000"),
        tuple("AAA", TradeReason.DIVIDEND_REINVESTMENT, day(2), "배당 재투자", "12.5", "4", "0"));
      assertThat(plain(result.transactions().get(1).getCashUsed())).isEqualTo("50");
      assertThat(result.dividends()).extracting(d -> plain(d.amountUsd())).containsExactly("50");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
      // 104주 × 12.5
      assertThat(plain(result.totalAssetKrw())).isEqualTo("1690000");
    }

    @Test
    @DisplayName("재투자를 켜도 그 종목 일봉이 없는 날 받은 배당은 현금으로 둔다")
    void keepsCashWithoutCandle() {
      prices("AAA", candle("AAA", day(0), "10"), candle("AAA", day(2), "10"));
      prices("BBB", candle("BBB", day(0), "20"), candle("BBB", day(1), "20"), candle("BBB", day(2), "20"));
      dividends("AAA", dividend("AAA", day(1), "1"));

      PortfolioEngine.Result result = engine.run(request(day(2), asset("AAA", "1300000"), asset("BBB", "1300000"))
        .reinvestDividends(true).build());

      assertThat(result.transactions()).extracting(StrategyTransaction::getReason)
        .containsExactly(TradeReason.BUY, TradeReason.BUY);
      assertThat(plain(result.cashUsd())).isEqualTo("100");
      assertThat(plain(result.cashKrw())).isEqualTo("130000");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("2730000");
    }

    @Test
    @DisplayName("마지막 일봉 뒤 평가일까지의 배당락일 배당도 현금으로 받는다")
    void receivesDividendAfterLastCandle() {
      prices("AAA", candle("AAA", day(0), "10"), candle("AAA", day(1), "10"));
      dividends("AAA", dividend("AAA", day(3), "1"));

      PortfolioEngine.Result result = engine.run(request(day(3), asset("AAA", "1300000")).build());

      assertThat(result.dividends()).extracting(PortfolioEngine.Dividend::exDate).containsExactly(day(3));
      assertThat(daily(result)).containsExactly(
        tuple(day(0), "1000", "1300000"), tuple(day(1), "1000", "1300000"), tuple(day(3), "1100", "1430000"));
    }
  }

  @Nested
  @DisplayName("기간 끝 · 양도세 · 환율")
  class PeriodEndAndTax {

    @Test
    @DisplayName("기간 끝에 팔면 종목마다 그 종목 마지막 거래일 종가에 전량 팔고 판 돈은 현금으로 남긴다")
    void sellsEachAtItsLastTradingDay() {
      prices("AAA", candle("AAA", day(0), "10"), candle("AAA", day(1), "11"), candle("AAA", day(2), "12"));
      prices("BBB", candle("BBB", day(0), "20"), candle("BBB", day(1), "30"));

      PortfolioEngine.Result result = engine.run(request(day(2), asset("AAA", "1300000"), asset("BBB", "2600000"))
        .tradingFeeRate(new BigDecimal("0.01")).periodEnd(PeriodEnd.SELL_AT_END).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, day(0), "한 번에 매수", "10", "99", "1300000"),
        tuple("BBB", TradeReason.BUY, day(0), "한 번에 매수", "20", "99", "2600000"),
        tuple("BBB", TradeReason.PERIOD_END_SALE, day(1), "기간 끝 매도", "30", "-99", "0"),
        tuple("AAA", TradeReason.PERIOD_END_SALE, day(2), "기간 끝 매도", "12", "-99", "0"));
      assertThat(result.transactions()).extracting(tx -> plain(tx.getRealizedProfit()))
        .containsExactly(null, null, "940.3", "176.12");
      assertThat(daily(result)).containsExactly(
        tuple(day(0), "2970", "3861000"), tuple(day(1), "4029.3", "5238090"), tuple(day(2), "4116.42", "5351346"));
      assertThat(positions(result)).containsExactly(
        tuple("AAA", "1300000", "0", "12", "0"), tuple("BBB", "2600000", "0", "30", "0"));
      assertThat(plain(result.cashUsd())).isEqualTo("4116.42");
      assertThat(plain(result.stockValueKrw())).isEqualTo("0");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("5351346");
    }

    @Test
    @DisplayName("먼저 판 종목의 양도세는 그 종목 원가로 셈해 다음 해 5월 첫 거래일에 공용 현금에서 낸다")
    void paysTaxFromSharedCash() {
      LocalDate june = LocalDate.of(2024, 6, 3);
      LocalDate due = LocalDate.of(2025, 5, 1);
      LocalDate end = LocalDate.of(2025, 5, 2);
      prices("AAA", candle("AAA", START, "5"), candle("AAA", due, "5"), candle("AAA", end, "5"));
      prices("BBB", candle("BBB", START, "10"), candle("BBB", june, "30"));

      PortfolioEngine.Result result = engine.run(request(end, asset("AAA", "1300000"), asset("BBB", "3900000"))
        .periodEnd(PeriodEnd.SELL_AT_END).capitalGainsTax(true).build());

      assertThat(result.transactions()).extracting(tx -> tuple(tx.getSymbol(), tx.getReason(), tx.tradedOn()))
        .containsExactly(tuple("AAA", TradeReason.BUY, START), tuple("BBB", TradeReason.BUY, START),
          tuple("BBB", TradeReason.PERIOD_END_SALE, june), tuple(null, TradeReason.TAX_PAYMENT, due),
          tuple("AAA", TradeReason.PERIOD_END_SALE, end));
      // BBB 원가 390만원 대비 1170만원, 공제 뒤 22% 116만 6천원 ÷ 1300
      assertThat(plain(result.transactions().get(3).getTaxPaid())).isEqualTo("896.92");
      assertThat(result.taxPayments().paidKrw()).containsOnlyKeys(2024);
      assertThat(plain(result.taxPayments().paidKrw().get(2024))).isEqualTo("1166000");
      assertThat(result.taxPayments().dueDates()).containsEntry(2024, due);
      assertThat(plain(result.cashUsd())).isEqualTo("9103.08");
    }

    @Test
    @DisplayName("환율을 비우면 날마다 시장 환율로 사고 평가일 환율로 평가하며 기본 환율로 산 날을 남긴다")
    void marketFx() {
      prices("AAA", candle("AAA", day(0), "10"), candle("AAA", day(1), "10"), candle("AAA", day(2), "10"));
      prices("BBB", candle("BBB", day(1), "20"), candle("BBB", day(2), "20"));
      given(marketData.getBulkFxRates(any())).willReturn(Map.of(day(0).toString(), FX, day(2).toString(),
        new BigDecimal("1400")));
      given(marketData.getFxRate(day(2).toString())).willReturn(new FxRateDto(day(2), new BigDecimal("1400")));

      PortfolioEngine.Result result = engine.run(PortfolioRequest.builder().startDate(START).endDate(day(2))
        .assets(List.of(asset("AAA", "1300000"), asset("BBB", "1350000"))).build());

      assertThat(result.transactions()).extracting(tx -> tuple(tx.getSymbol(), plain(tx.getFxRate()), plain(tx.getShares())))
        .containsExactly(tuple("AAA", "1300", "100"), tuple("BBB", "1350", "50"));
      assertThat(result.defaultFxDates()).containsExactly(day(1));
      assertThat(plain(result.currentFxRate())).isEqualTo("1400");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("2800000");
      // 기본 환율 날은 앞 시장 환율로 평가
      assertThat(result.performance().dailyValues()).extracting(DailyValue::valueKrw)
        .extracting(BigDecimal::toPlainString).containsExactly("1300000", "2600000", "2800000");
    }
  }

  @Nested
  @DisplayName("리밸런싱")
  class Rebalance {

    private static final LocalDate DEC = LocalDate.of(2024, 12, 31);
    private static final LocalDate JAN2 = LocalDate.of(2025, 1, 2);
    private static final LocalDate JAN3 = LocalDate.of(2025, 1, 3);
    private static final LocalDate APR1 = LocalDate.of(2024, 4, 1);
    private static final LocalDate APR2 = LocalDate.of(2024, 4, 2);

    @Test
    @DisplayName("매년이면 처음 매수한 해 다음 해부터 해마다 한 번 처음 비중을 목표로 넘는 종목을 먼저 팔고 모자란 종목을 판 돈으로 산다")
    void yearly() {
      prices("AAA", candle("AAA", START, "10"), candle("AAA", DEC, "10"), candle("AAA", JAN2, "10"),
        candle("AAA", JAN3, "11"));
      prices("BBB", candle("BBB", START, "10"), candle("BBB", DEC, "25"), candle("BBB", JAN2, "25"),
        candle("BBB", JAN3, "25"));

      PortfolioEngine.Result result = engine.run(request(JAN3, asset("AAA", "780000"), asset("BBB", "520000"))
        .rebalance(PortfolioRebalance.YEARLY).build());

      // 1월 2일 600 + 1000 달러를 60 : 40 인 960 · 640 달러로
      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, START, "한 번에 매수", "10", "60", "780000"),
        tuple("BBB", TradeReason.BUY, START, "한 번에 매수", "10", "40", "520000"),
        tuple("BBB", TradeReason.REBALANCE_SELL, JAN2, "리밸런싱 매도", "25", "-14.4", "0"),
        tuple("AAA", TradeReason.REBALANCE_BUY, JAN2, "리밸런싱 매수", "10", "36", "0"));
      // 판 몫의 원가 400 × 14.4 ÷ 40 달러
      StrategyTransaction sale = result.transactions().get(2);
      assertThat(tuple(plain(sale.getProceeds()), plain(sale.getRealizedProfit()))).isEqualTo(tuple("360", "216"));
      assertThat(plain(result.transactions().get(3).getCashUsed())).isEqualTo("360");
      assertThat(positions(result)).containsExactly(
        tuple("AAA", "780000", "96", "11", "1372800"), tuple("BBB", "520000", "25.6", "25", "832000"));
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("1300000");
      assertThat(plain(result.cashUsd())).isEqualTo("0");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("2204800");
    }

    @Test
    @DisplayName("분기마다면 모든 종목의 처음 매수가 끝난 다음 분기부터 종목이 모두 일봉이 있는 첫 날 리밸런싱한다")
    void quarterlyAfterAllBought() {
      LocalDate july1 = LocalDate.of(2024, 7, 1);
      LocalDate july2 = LocalDate.of(2024, 7, 2);
      // BBB 는 4월 1일 상장, 7월 1일 일봉 없음
      prices("AAA", candle("AAA", START, "10"), candle("AAA", APR1, "20"), candle("AAA", july1, "20"),
        candle("AAA", july2, "20"));
      prices("BBB", candle("BBB", APR1, "10"), candle("BBB", july2, "40"));

      PortfolioEngine.Result result = engine.run(request(july2, asset("AAA", "1300000"), asset("BBB", "1300000"))
        .rebalance(PortfolioRebalance.QUARTERLY).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, START, "한 번에 매수", "10", "100", "1300000"),
        tuple("BBB", TradeReason.BUY, APR1, "한 번에 매수", "10", "100", "1300000"),
        tuple("BBB", TradeReason.REBALANCE_SELL, july2, "리밸런싱 매도", "40", "-25", "0"),
        tuple("AAA", TradeReason.REBALANCE_BUY, july2, "리밸런싱 매수", "20", "50", "0"));
    }

    @Test
    @DisplayName("매달이면 공용 현금까지 목표에 넣어 새 돈 없이 사고 목표와 1달러 미만 차이인 종목은 건너뛰며 남은 현금은 그대로 둔다")
    void monthlyWithSharedCash() {
      prices("AAA", candle("AAA", START, "10"), candle("AAA", APR1, "11.99"));
      prices("BBB", candle("BBB", START, "10"), candle("BBB", APR1, "10"));
      // 주당 2달러 × 100주 = 200달러 현금
      dividends("BBB", dividend("BBB", LocalDate.of(2024, 3, 5), "2"));

      PortfolioEngine.Result result = engine.run(request(APR1, asset("AAA", "1300000"), asset("BBB", "1300000"))
        .rebalance(PortfolioRebalance.MONTHLY).build());

      // 1199 + 1000 + 200 달러의 반 1199.5 달러, AAA 는 0.5 달러 모자라 건너뜀
      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, START, "한 번에 매수", "10", "100", "1300000"),
        tuple("BBB", TradeReason.BUY, START, "한 번에 매수", "10", "100", "1300000"),
        tuple("BBB", TradeReason.REBALANCE_BUY, APR1, "리밸런싱 매수", "10", "19.95", "0"));
      assertThat(plain(result.transactions().get(2).getCashUsed())).isEqualTo("199.5");
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("2600000");
      assertThat(plain(result.cashUsd())).isEqualTo("0.5");
      assertThat(positions(result)).containsExactly(
        tuple("AAA", "1300000", "100", "11.99", "1558700"), tuple("BBB", "1300000", "119.95", "10", "1559350"));
      assertThat(plain(result.totalAssetKrw())).isEqualTo("3118700");
    }

    @Test
    @DisplayName("수수료가 있으면 판 돈에서 매도 수수료를 빼고 모자란 종목은 남은 현금만큼만 산다")
    void fees() {
      prices("AAA", candle("AAA", START, "10"), candle("AAA", APR1, "20"));
      prices("BBB", candle("BBB", START, "10"), candle("BBB", APR1, "10"));

      PortfolioEngine.Result result = engine.run(request(APR1, asset("AAA", "1300000"), asset("BBB", "1300000"))
        .tradingFeeRate(new BigDecimal("0.01")).rebalance(PortfolioRebalance.MONTHLY).build());

      // 1980 + 990 달러의 반 1485 달러, AAA 495 달러어치를 팔아 490.05 달러
      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, START, "한 번에 매수", "10", "99", "1300000"),
        tuple("BBB", TradeReason.BUY, START, "한 번에 매수", "10", "99", "1300000"),
        tuple("AAA", TradeReason.REBALANCE_SELL, APR1, "리밸런싱 매도", "20", "-24.75", "0"),
        tuple("BBB", TradeReason.REBALANCE_BUY, APR1, "리밸런싱 매수", "10", "48.515", "0"));
      StrategyTransaction sale = result.transactions().get(2);
      // 판 몫의 원가 1000 × 24.75 ÷ 99 달러
      assertThat(tuple(plain(sale.getFee()), plain(sale.getProceeds()), plain(sale.getRealizedProfit())))
        .isEqualTo(tuple("4.95", "490.05", "240.05"));
      StrategyTransaction buy = result.transactions().get(3);
      assertThat(tuple(plain(buy.getFee()), plain(buy.getCashUsed()))).isEqualTo(tuple("4.9", "490.05"));
      assertThat(plain(result.cashUsd())).isEqualTo("0");
    }

    @Test
    @DisplayName("시장 환율이면 리밸런싱과 기간 끝 매도를 그 날짜 환율로 사고판다")
    void marketFx() {
      LocalDate april3 = LocalDate.of(2024, 4, 3);
      prices("AAA", candle("AAA", START, "10"), candle("AAA", APR1, "20"), candle("AAA", APR2, "20"));
      prices("BBB", candle("BBB", START, "10"), candle("BBB", APR1, "10"), candle("BBB", APR2, "10"));
      given(marketData.getBulkFxRates(any())).willReturn(Map.of(START.toString(), FX,
        APR1.toString(), new BigDecimal("1400"), APR2.toString(), new BigDecimal("1450")));
      given(marketData.getFxRate(april3.toString())).willReturn(new FxRateDto(april3, new BigDecimal("1500")));

      PortfolioEngine.Result result = engine.run(PortfolioRequest.builder().startDate(START).endDate(april3)
        .assets(List.of(asset("AAA", "1300000"), asset("BBB", "1300000"))).rebalance(PortfolioRebalance.MONTHLY)
        .periodEnd(PeriodEnd.SELL_AT_END).build());

      assertThat(result.transactions())
        .extracting(tx -> tuple(tx.getSymbol(), tx.getReason(), tx.tradedOn(), plain(tx.getShares()), plain(tx.getFxRate())))
        .containsExactly(
          tuple("AAA", TradeReason.BUY, START, "100", "1300"),
          tuple("BBB", TradeReason.BUY, START, "100", "1300"),
          tuple("AAA", TradeReason.REBALANCE_SELL, APR1, "-25", "1400"),
          tuple("BBB", TradeReason.REBALANCE_BUY, APR1, "50", "1400"),
          tuple("AAA", TradeReason.PERIOD_END_SALE, APR2, "-75", "1450"),
          tuple("BBB", TradeReason.PERIOD_END_SALE, APR2, "-150", "1450"));
      assertThat(plain(result.currentFxRate())).isEqualTo("1500");
      assertThat(result.defaultFxDates()).isEmpty();
    }

    @Test
    @DisplayName("기간 끝에 팔면 종목의 마지막 거래일에는 리밸런싱하지 않는다")
    void notOnPeriodEndSaleDay() {
      prices("AAA", candle("AAA", START, "10"), candle("AAA", JAN2, "20"));
      prices("BBB", candle("BBB", START, "10"), candle("BBB", JAN2, "10"));

      PortfolioEngine.Result result = engine.run(request(JAN2, asset("AAA", "1300000"), asset("BBB", "1300000"))
        .rebalance(PortfolioRebalance.YEARLY).periodEnd(PeriodEnd.SELL_AT_END).build());

      assertThat(result.transactions()).extracting(StrategyTransaction::getReason).containsExactly(
        TradeReason.BUY, TradeReason.BUY, TradeReason.PERIOD_END_SALE, TradeReason.PERIOD_END_SALE);
    }
  }

  @Nested
  @DisplayName("익절 · 손절")
  class SellRules {

    @Test
    @DisplayName("줄마다 평균 단가 대비 익절 · 손절 기준가에 닿은 날 그 값에 전량 팔고 판 돈은 공용 현금에 두며 산 날 · 일봉이 없는 날은 보지 않는다")
    void sellsEachLineAtItsTrigger() {
      prices("AAA", bar("AAA", day(0), "10", "12", "10", "10"), bar("AAA", day(1), "10.5", "11.2", "10.4", "11"),
        candle("AAA", day(2), "12"));
      prices("BBB", candle("BBB", day(0), "10"), bar("BBB", day(2), "9", "9", "7.5", "8"));

      PortfolioEngine.Result result = engine.run(request(day(2), selling("AAA", "1300000", "0.1", null),
        selling("BBB", "1300000", null, "0.2")).periodEnd(PeriodEnd.SELL_AT_END).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, day(0), "한 번에 매수", "10", "100", "1300000"),
        tuple("BBB", TradeReason.BUY, day(0), "한 번에 매수", "10", "100", "1300000"),
        tuple("AAA", TradeReason.TAKE_PROFIT, day(1), "10.0%익절", "11", "-100", "0"),
        tuple("BBB", TradeReason.STOP_LOSS, day(2), "20.0%손절", "8", "-100", "0"));
      assertThat(result.transactions()).extracting(tx -> tuple(plain(tx.getProceeds()), plain(tx.getRealizedProfit())))
        .containsExactly(tuple(null, null), tuple(null, null), tuple("1100", "100"), tuple("800", "-200"));
      assertThat(daily(result)).containsExactly(
        tuple(day(0), "2000", "2600000"), tuple(day(1), "2100", "2730000"), tuple(day(2), "1900", "2470000"));
      assertThat(positions(result)).containsExactly(
        tuple("AAA", "1300000", "0", "12", "0"), tuple("BBB", "1300000", "0", "8", "0"));
      assertThat(plain(result.cashUsd())).isEqualTo("1900");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("2470000");
    }

    @Test
    @DisplayName("판 돈은 공용 현금이라 그 날짜부터 회차 매수가 먼저 쓰고 판 종목도 회차에 다시 산다")
    void proceedsFundRounds() {
      prices("AAA", candle("AAA", START, "10"), bar("AAA", APR, "10.5", "11.5", "10", "10"), candle("AAA", MAY, "10"));
      monthly("BBB", "10", "10", "10");

      PortfolioEngine.Result result = engine.run(request(MAY, selling("AAA", "1300000", "0.1", null),
        asset("BBB", "1300000")).method(PortfolioMethod.DCA).build());

      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.DCA_BUY, START, "월정액", "10", "100", "1300000"),
        tuple("BBB", TradeReason.DCA_BUY, START, "월정액", "10", "100", "1300000"),
        tuple("AAA", TradeReason.TAKE_PROFIT, APR, "10.0%익절", "11", "-100", "0"),
        tuple("AAA", TradeReason.DCA_BUY, APR, "월정액", "10", "100", "0"),
        tuple("BBB", TradeReason.DCA_BUY, APR, "월정액", "10", "100", "1170000"),
        tuple("AAA", TradeReason.DCA_BUY, MAY, "월정액", "10", "100", "1300000"),
        tuple("BBB", TradeReason.DCA_BUY, MAY, "월정액", "10", "100", "1300000"));
      // 판 돈 1100 달러 가운데 AAA 회차가 1000 달러, BBB 회차가 남은 100 달러
      assertThat(result.transactions()).extracting(tx -> plain(tx.getCashUsed()))
        .containsExactly(null, null, null, "1000", "100", null, null);
      assertThat(plain(result.totalInvestedKrw())).isEqualTo("6370000");
      assertThat(result.positions()).extracting(position -> plain(position.shares())).containsExactly("200", "300");
    }

    @Test
    @DisplayName("평균 단가는 매수 수수료를 넣은 원가로 보고 판 돈에서 매도 수수료를 뺀다")
    void fees() {
      prices("AAA", candle("AAA", START, "10"), bar("AAA", day(1), "12", "12", "11.9", "12"));

      PortfolioEngine.Result result = engine.run(request(day(1), selling("AAA", "1300000", "0.1", null))
        .tradingFeeRate(new BigDecimal("0.01")).build());

      // 원가 1000 달러 ÷ 99주 대비 시가 12 달러, 1188 달러에서 수수료 11.88 달러
      StrategyTransaction sale = result.transactions().get(1);
      assertThat(tuple(sale.getReason(), sale.getTrigger(), plain(sale.getShares()), plain(sale.getFee()),
        plain(sale.getProceeds()), plain(sale.getRealizedProfit())))
        .isEqualTo(tuple(TradeReason.TAKE_PROFIT, "18.8%익절", "-99", "11.88", "1176.12", "176.12"));
      assertThat(plain(result.cashUsd())).isEqualTo("1176.12");
    }

    @Test
    @DisplayName("판 종목은 다음 리밸런싱이 판 돈으로 처음 비중까지 다시 산다")
    void rebalanceBuysBack() {
      LocalDate april1 = LocalDate.of(2024, 4, 1);
      prices("AAA", candle("AAA", START, "10"), bar("AAA", day(1), "10.5", "11.5", "10.4", "11"),
        candle("AAA", april1, "10.5"));
      prices("BBB", candle("BBB", START, "10"), candle("BBB", april1, "10"));

      PortfolioEngine.Result result = engine.run(request(april1, selling("AAA", "1300000", "0.1", null),
        asset("BBB", "1300000")).rebalance(PortfolioRebalance.MONTHLY).build());

      // 4월 1일 BBB 1000 + 현금 1100 달러의 반 1050 달러씩
      assertThat(trades(result)).containsExactly(
        tuple("AAA", TradeReason.BUY, START, "한 번에 매수", "10", "100", "1300000"),
        tuple("BBB", TradeReason.BUY, START, "한 번에 매수", "10", "100", "1300000"),
        tuple("AAA", TradeReason.TAKE_PROFIT, day(1), "10.0%익절", "11", "-100", "0"),
        tuple("AAA", TradeReason.REBALANCE_BUY, april1, "리밸런싱 매수", "10.5", "100", "0"),
        tuple("BBB", TradeReason.REBALANCE_BUY, april1, "리밸런싱 매수", "10", "5", "0"));
      assertThat(plain(result.cashUsd())).isEqualTo("0");
      assertThat(plain(result.totalAssetKrw())).isEqualTo("2730000");
    }
  }
}
