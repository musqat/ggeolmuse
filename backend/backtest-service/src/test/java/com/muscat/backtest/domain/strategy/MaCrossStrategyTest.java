package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.SignalChart;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaCrossStrategy 테스트")
class MaCrossStrategyTest {

  private static final String SYMBOL = "MAX";
  private static final LocalDate START = LocalDate.of(2024, 3, 4);

  @Mock
  private MarketDataClientWrapper marketDataClient;

  @Mock
  private ResponseMapper responseMapper;

  private MaCrossStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new MaCrossStrategy(marketDataClient, responseMapper);
    lenient().when(marketDataClient.defaultFxRate()).thenReturn(new BigDecimal("1300"));
  }

  // 시작일에서 offset 일 뒤 일봉(종가 · 수정 종가 같음)
  private static OHLCPriceDto day(int offset, String close) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(SYMBOL, START.plusDays(offset), price, price, price, price, price, 1_000L, "USD", true);
  }

  // 종가와 수정 종가가 다른 일봉(수정 종가가 null 이면 그 칸 없음)
  private static OHLCPriceDto adjusted(int offset, String close, String adjustedClose) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(SYMBOL, START.plusDays(offset), price, price, price, price,
      adjustedClose == null ? null : new BigDecimal(adjustedClose), 1_000L, "USD", true);
  }

  // 2 · 3일 선: 1일째 골든 크로스(10), 4일째 데드 크로스(12), 6일째 골든 크로스(30), 시작일 앞 이틀은 신호용
  private static List<OHLCPriceDto> crossing() {
    return List.of(day(-2, "8"), day(-1, "8"), day(0, "8"), day(1, "10"), day(2, "20"), day(3, "22"),
      day(4, "12"), day(5, "8"), day(6, "30"), day(7, "31"));
  }

  // 2025년 5월 1일(시작일 + 423일), 2024년 판 해의 납부일
  private static final LocalDate DUE = START.plusDays(423);

  // 1일째 골든 크로스(10), 4일째 데드 크로스(49) 뒤 5일째 40 에 이어지는 날
  private static List<OHLCPriceDto> taxedCrossing(OHLCPriceDto... rest) {
    List<OHLCPriceDto> prices = new ArrayList<>(List.of(day(-2, "8"), day(-1, "8"), day(0, "8"), day(1, "10"),
      day(2, "60"), day(3, "70"), day(4, "49"), day(5, "40")));
    prices.addAll(List.of(rest));
    return prices;
  }

  // 원금 130만원(환율 1300 고정이면 1000달러) · 2 · 3일 선 · 수수료 없음
  private static MaCrossStrategyRequest.MaCrossStrategyRequestBuilder request(int days) {
    return MaCrossStrategyRequest.builder().userId("test-user").symbol(SYMBOL)
      .startDate(START).endDate(START.plusDays(days))
      .investmentAmount(new BigDecimal("1300000"))
      .shortWindow(2).longWindow(3)
      .purchaseFxRate(new BigDecimal("1300"));
  }

  // 기간 시세 · 평가일(과거) 시세와 환율 · 배당
  private void stubMarket(List<OHLCPriceDto> prices) {
    given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString())).willReturn(prices);
    lenient().when(marketDataClient.getOHLCPrice(eq(SYMBOL), anyString())).thenReturn(prices.getLast());
    lenient().when(marketDataClient.getFxRate(anyString())).thenReturn(new FxRateDto(START, new BigDecimal("1300")));
    lenient().when(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString())).thenReturn(List.of());
    given(responseMapper.toStrategyResponse(any(MaCrossStrategyRequest.class), any(), any(), any(), any()))
      .willReturn(StrategyResponse.builder().build());
  }

  // 시작일 ~ 시작일 + days 날마다 1300원, 고른 날은 그 값
  private void stubDailyFx(int days, Map<Integer, String> overrides) {
    Map<String, BigDecimal> rates = new HashMap<>();
    for (int offset = 0; offset <= days; offset++) {
      rates.put(START.plusDays(offset).toString(), new BigDecimal(overrides.getOrDefault(offset, "1300")));
    }
    given(marketDataClient.getBulkFxRates(any())).willReturn(rates);
  }

  private List<StrategyTransaction> executed() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StrategyTransaction>> captor = ArgumentCaptor.forClass(List.class);
    verify(responseMapper).toStrategyResponse(any(MaCrossStrategyRequest.class), captor.capture(), any(), any(), any());
    return captor.getValue();
  }

  private StrategyCalculationResult calculation() {
    ArgumentCaptor<StrategyCalculationResult> captor = ArgumentCaptor.forClass(StrategyCalculationResult.class);
    verify(responseMapper).toStrategyResponse(any(MaCrossStrategyRequest.class), any(), captor.capture(), any(), any());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private List<SkippedRound> skippedRounds() {
    ArgumentCaptor<List<SkippedRound>> captor = ArgumentCaptor.forClass(List.class);
    verify(responseMapper).toStrategyResponse(any(MaCrossStrategyRequest.class), any(), any(), any(), captor.capture());
    return captor.getValue();
  }

  // 거래일 · 가격 · 이유 · 주식 수
  private List<Tuple> trades() {
    return executed().stream()
      .map(tx -> tuple(tx.getDate(), plain(tx.getPrice()), tx.getTrigger(), plain(tx.getShares())))
      .toList();
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  @Test
  @DisplayName("전략 타입은 MA_CROSS")
  void strategyType() {
    assertThat(strategy.getStrategyType()).isEqualTo(StrategyType.MA_CROSS);
  }

  @Nested
  @DisplayName("교차 매매")
  class CrossTrades {

    @Test
    @DisplayName("골든 크로스 날 종가에 원금 전부로 사고, 데드 크로스 날 종가에 전량 팔고, 다음 골든 크로스에 판 돈 전부로 다시 산다")
    void buysOnGoldenSellsOnDead() {
      stubMarket(crossing());

      strategy.execute(request(7).build());

      assertThat(trades()).containsExactly(
        tuple(START.plusDays(1), "10", "골든 크로스", "100"),
        tuple(START.plusDays(4), "12", "데드 크로스", "-100"),
        tuple(START.plusDays(6), "30", "골든 크로스", "40"));
      List<StrategyTransaction> trades = executed();
      assertThat(trades.get(0).getAmount()).isEqualByComparingTo("1300000");
      assertThat(trades.get(0).getCashUsed()).isNull();
      assertThat(trades.get(1).getAmount()).isEqualByComparingTo("0");
      assertThat(trades.get(1).getProceeds()).isEqualByComparingTo("1200");
      assertThat(trades.get(1).getRealizedProfit()).isEqualByComparingTo("200");
      assertThat(trades.get(2).getAmount()).isEqualByComparingTo("0");
      assertThat(trades.get(2).getCashUsed()).isEqualByComparingTo("1200");
      StrategyCalculationResult calculation = calculation();
      assertThat(calculation.getTotalInvested()).isEqualByComparingTo("1300000");
      assertThat(calculation.getTotalShares()).isEqualByComparingTo("40");
      assertThat(calculation.getCashBalance()).isEqualByComparingTo("0");
      // 40주 × 31달러 × 1300원
      assertThat(calculation.getTotalAssetKrw()).isEqualByComparingTo("1612000");
    }

    @Test
    @DisplayName("시작일 전 골든 크로스로는 사지 않고, 시작일에 이미 짧은 선이 위면 데드 크로스 뒤 골든 크로스를 기다려 산다")
    void waitsForCrossWhenAlreadyAbove() {
      // 시작일 전날 골든 크로스, 2일째 아래로, 3일째 다시 위로
      stubMarket(List.of(day(-4, "8"), day(-3, "8"), day(-2, "8"), day(-1, "10"), day(0, "12"), day(1, "13"),
        day(2, "5"), day(3, "25")));

      strategy.execute(request(3).build());

      assertThat(trades()).containsExactly(tuple(START.plusDays(3), "25", "골든 크로스", "40"));
    }

    @Test
    @DisplayName("교차가 한 번도 없으면 투자 · 평가가 0 인 매수 0회 결과를 돌려주고 배당은 조회하지 않는다")
    void noCrossReturnsZeroResult() {
      stubMarket(List.of(day(-2, "10"), day(-1, "10"), day(0, "10"), day(1, "10"), day(2, "10")));

      StrategyResponse response = strategy.execute(request(2).build());

      assertThat(executed()).isEmpty();
      StrategyCalculationResult calculation = calculation();
      assertThat(calculation.getTotalInvested()).isEqualByComparingTo("0");
      assertThat(calculation.getTotalAssetKrw()).isEqualByComparingTo("0");
      assertThat(response.getDailyValues()).isEmpty();
      verify(marketDataClient, never()).getDividendHistory(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("이동평균은 수정 종가로 내고 사는 값은 그 날짜 종가다")
    void signalUsesAdjustedClose() {
      // 종가는 늘 10, 수정 종가만 8 → 10 으로 올라 1일째 교차
      stubMarket(List.of(adjusted(-2, "10", "8"), adjusted(-1, "10", "8"), adjusted(0, "10", "8"),
        adjusted(1, "10", "10"), adjusted(2, "10", "10")));

      strategy.execute(request(2).build());

      assertThat(trades()).containsExactly(tuple(START.plusDays(1), "10", "골든 크로스", "100"));
    }

    @Test
    @DisplayName("수정 종가가 없거나 0 이하인 날은 종가로 이동평균을 낸다")
    void signalFallsBackToClose() {
      stubMarket(List.of(adjusted(-2, "8", null), adjusted(-1, "8", "0"), adjusted(0, "8", null),
        adjusted(1, "10", "0")));

      strategy.execute(request(1).build());

      assertThat(trades()).containsExactly(tuple(START.plusDays(1), "10", "골든 크로스", "100"));
    }

    @Test
    @DisplayName("짧은 · 긴 기간을 비우면 20 · 60 거래일 선으로 보고, 시작일 전 긴 기간 거래일이 들어가게 더 받아 시작일부터 교차를 본다")
    void defaultWindowsAndLookback() {
      // 시작일 앞 40일 10, 그 뒤 20일 9, 시작일 30 → 20일 선 10.05 · 60일 선 10 으로 시작일에 교차
      List<OHLCPriceDto> prices = new ArrayList<>();
      for (int offset = -60; offset < 0; offset++) {
        prices.add(day(offset, offset < -20 ? "10" : "9"));
      }
      prices.add(day(0, "30"));
      stubMarket(prices);

      strategy.execute(request(0).shortWindow(null).longWindow(null).build());

      assertThat(trades()).containsExactly(tuple(START, "30", "골든 크로스", plain(new BigDecimal("33.33333333"))));
      verify(marketDataClient).getOHLCPriceRange(SYMBOL, START.minusDays(104).toString(), START.toString());
    }

    @Test
    @DisplayName("수수료율을 주면 매수 · 매도 · 판 돈으로 다시 살 때 모두 수수료를 뺀다")
    void deductsFees() {
      stubMarket(crossing());

      strategy.execute(request(7).tradingFeeRate(new BigDecimal("0.01")).build());

      List<StrategyTransaction> trades = executed();
      // 1000달러에서 10달러를 빼고 10달러에 99주
      assertThat(trades.get(0).getFee()).isEqualByComparingTo("10.00");
      assertThat(trades.get(0).getShares()).isEqualByComparingTo("99");
      // 99주 × 12 = 1188달러에서 11.88달러
      assertThat(trades.get(1).getFee()).isEqualByComparingTo("11.88");
      assertThat(trades.get(1).getProceeds()).isEqualByComparingTo("1176.12");
      assertThat(trades.get(1).getRealizedProfit()).isEqualByComparingTo("176.12");
      // 판 돈 1176.12달러에서 11.76달러를 빼고 30달러에 38.812주
      assertThat(trades.get(2).getFee()).isEqualByComparingTo("11.76");
      assertThat(trades.get(2).getCashUsed()).isEqualByComparingTo("1176.12");
      assertThat(trades.get(2).getShares()).isEqualByComparingTo("38.812");
    }

    @Test
    @DisplayName("기간 끝 매도를 고르면 남은 주식을 마지막 거래일 종가에 판다")
    void sellAtEnd() {
      stubMarket(crossing());

      strategy.execute(request(7).periodEnd(PeriodEnd.SELL_AT_END).build());

      assertThat(trades()).last().isEqualTo(tuple(START.plusDays(7), "31", "기간 끝 매도", "-40"));
    }

    @Test
    @DisplayName("양도세를 켜면 판 해의 원화 실현 이익 · 공제 · 세금을 싣고, 비우면 싣지 않는다")
    void capitalGainsTax() {
      stubMarket(crossing());

      StrategyResponse response = strategy.execute(request(7).capitalGainsTax(true).build());

      // 130만원어치 100주를 1200달러 × 1300원에 팔아 26만원 이익, 공제 안이라 세금 0
      assertThat(response.getCapitalGainsTax().years()).containsExactly(
        new CapitalGainsTax.Year(2024, new BigDecimal("260000"), new BigDecimal("260000"), new BigDecimal("0"), null,
          BigDecimal.ZERO));
      assertThat(strategy.execute(request(7).build()).getCapitalGainsTax()).isNull();
    }

    @Test
    @DisplayName("양도세를 켜면 판 해의 세금을 다음 해 5월 1일 이후 첫 거래일에 그 날짜 매매보다 먼저 판 돈에서 내고 남은 돈으로 산다")
    void paysCapitalGainsTaxBeforeTrading() {
      stubMarket(taxedCrossing(day(422, "30"), day(423, "60"), day(424, "61")));

      StrategyResponse response = strategy.execute(request(424).capitalGainsTax(true).build());

      assertThat(trades()).containsExactly(
        tuple(START.plusDays(1), "10", "골든 크로스", "100"),
        tuple(START.plusDays(4), "49", "데드 크로스", "-100"),
        tuple(DUE, "0", "양도세 납부", "0"),
        tuple(DUE, "60", "골든 크로스", "74.418"));
      // 130만원어치 100주를 4900달러 × 1300원에 팔아 507만원 이익, 257만원 × 22% ÷ 1300원
      StrategyTransaction payment = executed().get(2);
      assertThat(payment.getTaxPaid()).isEqualByComparingTo("434.92");
      assertThat(payment.getFxRate()).isEqualByComparingTo("1300");
      assertThat(executed().get(3).getCashUsed()).isEqualByComparingTo("4465.08");
      StrategyCalculationResult calculation = calculation();
      assertThat(calculation.getCashBalance()).isEqualByComparingTo("0");
      // 74.418주 × 61달러 × 1300원
      assertThat(calculation.getTotalAssetKrw()).isEqualByComparingTo("5901350");
      assertThat(response.getDailyValues()).filteredOn(value -> value.date().isAfter(START.plusDays(5)))
        .extracting(value -> value.valueUsd().toPlainString())
        .containsExactly("4900.00", "4465.08", "4539.50");
      CapitalGainsTax tax = response.getCapitalGainsTax();
      assertThat(tax.years()).containsExactly(new CapitalGainsTax.Year(2024, new BigDecimal("5070000"),
        new BigDecimal("2500000"), new BigDecimal("565400"), DUE, new BigDecimal("565400")));
      assertThat(tax.paidTaxKrw()).isEqualByComparingTo("565400");
      assertThat(tax.unpaidTaxKrw()).isEqualByComparingTo("0");
      assertThat(tax.cashKrw()).isEqualByComparingTo("0");
      assertThat(tax.stockKrw()).isEqualByComparingTo("5901350");
      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("5901350");
    }

    @Test
    @DisplayName("납부일에 현금이 없으면 판 다음 거래일에 있는 만큼 내고, 현금을 다 쓰면 골든 크로스에도 사지 않으며 못 낸 몫은 세후 총 평가에서 뺀다")
    void paysWhatCashAllowsAfterSale() {
      // 6일째 골든 크로스(80)에 판 돈 전부로 다시 사 납부일(423일째)에 들고 있음, 424일째 데드 크로스(4), 426일째 골든 크로스(50)
      stubMarket(taxedCrossing(day(6, "80"), day(423, "81"), day(424, "4"), day(425, "3"), day(426, "50")));

      StrategyResponse response = strategy.execute(request(426).capitalGainsTax(true).build());

      assertThat(trades()).containsExactly(
        tuple(START.plusDays(1), "10", "골든 크로스", "100"),
        tuple(START.plusDays(4), "49", "데드 크로스", "-100"),
        tuple(START.plusDays(6), "80", "골든 크로스", "61.25"),
        tuple(START.plusDays(424), "4", "데드 크로스", "-61.25"),
        tuple(START.plusDays(425), "0", "양도세 납부", "0"));
      assertThat(executed().getLast().getTaxPaid()).isEqualByComparingTo("245");
      CapitalGainsTax tax = response.getCapitalGainsTax();
      // 245달러 × 1300원, 2025년은 637만원어치를 31만 8,500원에 팔아 손실
      assertThat(tax.years()).containsExactly(
        new CapitalGainsTax.Year(2024, new BigDecimal("5070000"), new BigDecimal("2500000"), new BigDecimal("565400"),
          DUE, new BigDecimal("318500")),
        new CapitalGainsTax.Year(2025, new BigDecimal("-6051500"), new BigDecimal("0"), new BigDecimal("0"), null,
          BigDecimal.ZERO));
      assertThat(tax.paidTaxKrw()).isEqualByComparingTo("318500");
      assertThat(tax.unpaidTaxKrw()).isEqualByComparingTo("246900");
      assertThat(tax.afterTaxValueKrw()).isEqualByComparingTo("-246900");
      assertThat(tax.afterTaxReturnPercent()).isEqualByComparingTo("-118.99");
    }

    @Test
    @DisplayName("양도세를 끄면 판 돈에서 양도세를 내지 않는다")
    void noPaymentWithoutCapitalGainsTax() {
      stubMarket(taxedCrossing(day(422, "30"), day(423, "60"), day(424, "61")));

      strategy.execute(request(424).build());

      assertThat(executed()).noneMatch(StrategyTransaction::isTaxPayment);
      assertThat(executed().getLast().getCashUsed()).isEqualByComparingTo("4900");
    }

    @Test
    @DisplayName("골든 크로스 날 계산이 실패하면(환율 0) 사지 않고 계산 오류로 남긴 뒤 다음 골든 크로스에 원금으로 산다")
    void calculationFailureWaitsForNextCross() {
      stubMarket(crossing());
      stubDailyFx(7, Map.of(1, "0"));

      strategy.execute(request(7).purchaseFxRate(null).build());

      assertThat(skippedRounds()).containsExactly(new SkippedRound(START.plusDays(1), SkipReason.CALCULATION_ERROR));
      assertThat(executed()).singleElement().satisfies(tx -> {
        assertThat(tx.getDate()).isEqualTo(START.plusDays(6));
        assertThat(tx.getAmount()).isEqualByComparingTo("1300000");
        assertThat(tx.getCashUsed()).isNull();
      });
    }

    @Test
    @DisplayName("환율이 없어 기본 환율로 산 날은 기본 환율 날로 남기고, 양도세를 끄면 판 날은 남기지 않는다")
    void defaultFxDates() {
      stubMarket(crossing());
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());

      strategy.execute(request(7).purchaseFxRate(null).build());

      assertThat(executed()).extracting(StrategyTransaction::getDefaultFxDate)
        .containsExactly(START.plusDays(1), null, START.plusDays(6));
    }

    @Test
    @DisplayName("양도세를 켜면 기본 환율로 판 날도 기본 환율 날로 남긴다")
    void defaultFxSaleDateWithCapitalGainsTax() {
      stubMarket(crossing());
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());

      strategy.execute(request(7).purchaseFxRate(null).capitalGainsTax(true).build());

      assertThat(executed()).extracting(StrategyTransaction::getDefaultFxDate)
        .containsExactly(START.plusDays(1), START.plusDays(4), START.plusDays(6));
    }

    @Test
    @DisplayName("기본 환율로 양도세를 낸 날도 기본 환율 날로 남긴다")
    void defaultFxTaxPaymentDate() {
      stubMarket(taxedCrossing(day(422, "30"), day(423, "60"), day(424, "61")));
      given(marketDataClient.getBulkFxRates(any())).willReturn(Map.of());

      strategy.execute(request(424).purchaseFxRate(null).capitalGainsTax(true).build());

      assertThat(executed()).extracting(StrategyTransaction::getTrigger, StrategyTransaction::getDefaultFxDate)
        .containsExactly(
          tuple("골든 크로스", START.plusDays(1)),
          tuple("데드 크로스", START.plusDays(4)),
          tuple("양도세 납부", DUE),
          tuple("골든 크로스", DUE));
    }

    @Test
    @DisplayName("기간에 쓸 수 있는 시세가 하루도 없으면 DATA_NOT_FOUND")
    void noDataInPeriod() {
      given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString()))
        .willReturn(List.of(day(-2, "10"), day(-1, "10"), day(0, "0")));

      assertThatThrownBy(() -> strategy.execute(request(1).build()))
        .isInstanceOf(BacktestException.class)
        .extracting("code")
        .isEqualTo(BacktestResponse.DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("종료일이 오늘 이후면 오늘까지만 조회하고 현재가로 평가한다")
    void futureEndDateStopsToday() {
      LocalDate today = LocalDate.now();
      // 오늘 5일 전부터 어제까지, 2일 전 골든 크로스
      List<String> closes = List.of("8", "8", "8", "10", "11");
      List<OHLCPriceDto> prices = IntStream.range(0, closes.size()).mapToObj(i -> {
        BigDecimal price = new BigDecimal(closes.get(i));
        return new OHLCPriceDto(SYMBOL, today.minusDays(5 - i), price, price, price, price, price, 1_000L, "USD", true);
      }).toList();
      given(marketDataClient.getOHLCPriceRange(eq(SYMBOL), anyString(), anyString())).willReturn(prices);
      given(marketDataClient.getCurrentPrice(SYMBOL)).willReturn(new StockPriceDto(SYMBOL, null, new BigDecimal("12"),
        null, null, null, null, today, null, null, null, null, null, null, "USD", true, null, null));
      given(marketDataClient.getLatestFxRate()).willReturn(new FxRateDto(today, new BigDecimal("1300")));
      given(marketDataClient.getDividendHistory(eq(SYMBOL), anyString(), anyString())).willReturn(List.of());
      given(responseMapper.toStrategyResponse(any(MaCrossStrategyRequest.class), any(), any(), any(), any()))
        .willReturn(StrategyResponse.builder().build());

      strategy.execute(request(0).startDate(today.minusDays(3)).endDate(today.plusDays(10)).build());

      assertThat(executed()).hasSize(1);
      verify(marketDataClient).getOHLCPriceRange(SYMBOL, today.minusDays(3).minusDays(18).toString(), today.toString());
    }
  }

  // 시작일에서 offset 일 뒤 점(평균이 null 이면 그 칸 없음)
  private static SignalChart.Point point(int offset, String close, String shortAverage, String longAverage) {
    return new SignalChart.Point(START.plusDays(offset), new BigDecimal(close),
      shortAverage == null ? null : new BigDecimal(shortAverage),
      longAverage == null ? null : new BigDecimal(longAverage));
  }

  private static SignalChart.Cross cross(int offset, SignalChart.CrossType type) {
    return new SignalChart.Cross(START.plusDays(offset), type);
  }

  @Nested
  @DisplayName("이동평균 차트")
  class Chart {

    @Test
    @DisplayName("시작일 ~ 평가일 일봉마다 수정 종가와 두 이동평균을 오래된 날부터 싣고, 교차한 날을 골든 · 데드로 싣는다")
    void pointsAndCrosses() {
      stubMarket(crossing());

      SignalChart chart = strategy.execute(request(7).build()).getSignalChart();

      assertThat(chart.symbol()).isEqualTo(SYMBOL);
      assertThat(chart.points()).containsExactly(
        point(0, "8.00", "8.00", "8.00"),
        point(1, "10.00", "9.00", "8.67"),
        point(2, "20.00", "15.00", "12.67"),
        point(3, "22.00", "21.00", "17.33"),
        point(4, "12.00", "17.00", "18.00"),
        point(5, "8.00", "10.00", "14.00"),
        point(6, "30.00", "19.00", "16.67"),
        point(7, "31.00", "30.50", "23.00"));
      assertThat(chart.crosses()).containsExactly(
        cross(1, SignalChart.CrossType.GOLDEN),
        cross(4, SignalChart.CrossType.DEAD),
        cross(6, SignalChart.CrossType.GOLDEN));
    }

    @Test
    @DisplayName("시작일 앞 일봉이 모자라 N 거래일이 안 찬 평균은 비우고, 교차는 두 평균이 다 있는 둘째 날부터 본다")
    void blankAveragesBeforeWindowFills() {
      stubMarket(List.of(day(0, "8"), day(1, "10"), day(2, "6"), day(3, "20")));

      SignalChart chart = strategy.execute(request(3).build()).getSignalChart();

      assertThat(chart.points()).containsExactly(
        point(0, "8.00", null, null),
        point(1, "10.00", "9.00", null),
        point(2, "6.00", "8.00", "8.00"),
        point(3, "20.00", "13.00", "12.00"));
      assertThat(chart.crosses()).containsExactly(cross(3, SignalChart.CrossType.GOLDEN));
    }

    @Test
    @DisplayName("첫 매수 전 데드 크로스도 싣고, 시작일 앞 교차는 싣지 않는다")
    void crossesRegardlessOfHolding() {
      // 시작일 전날 골든 크로스, 2일째 데드 크로스, 3일째 골든 크로스
      stubMarket(List.of(day(-4, "8"), day(-3, "8"), day(-2, "8"), day(-1, "10"), day(0, "12"), day(1, "13"),
        day(2, "5"), day(3, "25")));

      SignalChart chart = strategy.execute(request(3).build()).getSignalChart();

      assertThat(chart.crosses()).containsExactly(
        cross(2, SignalChart.CrossType.DEAD),
        cross(3, SignalChart.CrossType.GOLDEN));
      assertThat(trades()).containsExactly(tuple(START.plusDays(3), "25", "골든 크로스", "40"));
    }

    @Test
    @DisplayName("계산이 실패해 사지 못한 골든 크로스 날과 들고 있지 않은 데드 크로스 날도 교차로 싣는다")
    void crossesOnCalculationFailure() {
      stubMarket(crossing());
      stubDailyFx(7, Map.of(1, "0"));

      SignalChart chart = strategy.execute(request(7).purchaseFxRate(null).build()).getSignalChart();

      assertThat(chart.crosses()).containsExactly(
        cross(1, SignalChart.CrossType.GOLDEN),
        cross(4, SignalChart.CrossType.DEAD),
        cross(6, SignalChart.CrossType.GOLDEN));
    }

    @Test
    @DisplayName("점의 값은 수정 종가(없거나 0 이하면 종가)를 소수 둘째에서 반올림하고, 쓸 수 없는 일봉은 뺀다")
    void adjustedCloseRounded() {
      // 1일째는 종가 0 이라 빠짐
      stubMarket(List.of(adjusted(0, "10", "8.125"), day(1, "0"), adjusted(2, "10", null), adjusted(3, "10", "0")));

      SignalChart chart = strategy.execute(request(3).build()).getSignalChart();

      assertThat(chart.points()).containsExactly(
        point(0, "8.13", null, null),
        point(2, "10.00", "9.06", null),
        point(3, "10.00", "10.00", "9.38"));
      assertThat(chart.crosses()).isEmpty();
    }
  }

  // 요청 하나를 바꾼 경우 · 기대 오류 코드
  private static Arguments invalid(String label, UnaryOperator<MaCrossStrategyRequest.MaCrossStrategyRequestBuilder> change,
    BacktestResponse code) {
    return Arguments.of(label, change, code);
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
      invalid("종목 없음", b -> b.symbol(" "), BacktestResponse.STRATEGY_SYMBOL_REQUIRED),
      invalid("시작일이 종료일 뒤", b -> b.startDate(START.plusDays(3)).endDate(START),
        BacktestResponse.STRATEGY_DATE_RANGE_INVALID),
      invalid("원금 없음", b -> b.investmentAmount(null), BacktestResponse.INVALID_INVESTMENT_AMOUNT),
      invalid("원금 0", b -> b.investmentAmount(BigDecimal.ZERO), BacktestResponse.INVALID_INVESTMENT_AMOUNT),
      invalid("짧은 기간 1", b -> b.shortWindow(1), BacktestResponse.INVALID_MA_WINDOWS),
      invalid("긴 기간 251", b -> b.longWindow(251), BacktestResponse.INVALID_MA_WINDOWS),
      invalid("두 기간이 같음", b -> b.shortWindow(60).longWindow(60), BacktestResponse.INVALID_MA_WINDOWS),
      invalid("짧은 기간 70 · 긴 기간 비움", b -> b.shortWindow(70).longWindow(null), BacktestResponse.INVALID_MA_WINDOWS),
      invalid("배당 재투자", b -> b.reinvestDividends(true), BacktestResponse.SELL_WITH_REINVEST));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  @DisplayName("종목 · 기간 · 원금 · 이동평균 기간이 맞지 않거나 배당 재투자를 켜면 시세를 받기 전에 막는다")
  void rejects(String label, UnaryOperator<MaCrossStrategyRequest.MaCrossStrategyRequestBuilder> change,
    BacktestResponse code) {
    MaCrossStrategyRequest request = change.apply(request(7)).build();

    assertThatThrownBy(() -> strategy.execute(request))
      .isInstanceOf(BacktestException.class)
      .extracting("code")
      .isEqualTo(code);
    verifyNoInteractions(marketDataClient);
  }

  @Test
  @DisplayName("요청이 null 이면 STRATEGY_REQUEST_NULL")
  void rejectsNull() {
    assertThatThrownBy(() -> strategy.execute(null))
      .isInstanceOf(BacktestException.class)
      .extracting("code")
      .isEqualTo(BacktestResponse.STRATEGY_REQUEST_NULL);
  }

  @Test
  @DisplayName("적립식 요청이 오면 전략 타입 불일치로 막는다")
  void rejectsOtherRequestType() {
    DcaStrategyRequest other = DcaStrategyRequest.builder().symbol("AAPL")
      .startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1)).build();

    assertThatThrownBy(() -> strategy.execute(other))
      .isInstanceOf(BacktestException.class)
      .extracting("code")
      .isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
    verifyNoInteractions(marketDataClient);
  }
}
