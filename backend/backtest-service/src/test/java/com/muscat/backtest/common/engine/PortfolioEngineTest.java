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
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
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
    return new PortfolioAsset(symbol, new BigDecimal(amount), null);
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
        new PortfolioAsset("AAA", null, new BigDecimal("33.33")), new PortfolioAsset("BBB", null, new BigDecimal("66.67")))
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
}
