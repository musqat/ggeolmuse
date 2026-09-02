package com.muscat.backtest.domain.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.calculation.ComparisonCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResponseMapper 단위 테스트")
class ResponseMapperTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate START = LocalDate.of(2024, 1, 1);
  private static final LocalDate END = LocalDate.of(2024, 9, 18);

  private ResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ResponseMapper();
  }

  // ===== 픽스처 =====

  private static StockPriceDto price(String currentPrice) {
    return new StockPriceDto(
      SYMBOL, "Apple Inc.", new BigDecimal(currentPrice), null, null, null, null,
      END, null, null, null, null, null, null, "USD", true, "EQUITY", null);
  }

  /** 자릿수 반올림이 눈에 보이도록 소수 3자리 이상으로 채운 계산 결과 */
  private static StrategyCalculationResult calculation() {
    return StrategyCalculationResult.builder()
      .totalInvested(new BigDecimal("1000000.678"))
      .totalShares(new BigDecimal("12.3456789"))
      .averagePrice(new BigDecimal("81.005"))
      .averageFxRate(new BigDecimal("1300.123456"))
      .currentValue(new BigDecimal("1234.567"))
      .currentValueKrw(new BigDecimal("1666666.5"))
      .totalDividends(new BigDecimal("12.344"))
      .dividendsReinvested(new BigDecimal("5.678"))
      .remainingCashKrw(new BigDecimal("1234.4"))
      .totalAssetKrw(new BigDecimal("1667900.9"))
      .totalReturnUsd(new BigDecimal("234.567"))
      .totalReturnPercent(new BigDecimal("23.456"))
      .totalReturnKrw(new BigDecimal("667900.4"))
      .fxReturn(new BigDecimal("38.461"))
      .fxReturnPercent(new BigDecimal("3.846"))
      .currentFxRate(new BigDecimal("1350.0"))
      .build();
  }

  private static List<StrategyTransaction> transactions(int count) {
    return java.util.stream.IntStream.range(0, count)
      .mapToObj(i -> StrategyTransaction.builder()
        .date(START.plusMonths(i))
        .actualDate(START.plusMonths(i))
        .price(new BigDecimal("100"))
        .shares(new BigDecimal("1"))
        .amount(new BigDecimal("130000"))
        .fxRate(new BigDecimal("1300"))
        .trigger("월정액")
        .build())
      .toList();
  }

  @Nested
  @DisplayName("toStrategyResponse")
  class ToStrategyResponse {

    @Test
    @DisplayName("DCA 요청이면 strategyType 이 DCA")
    void dca_타입() {
      DcaStrategyRequest request = DcaStrategyRequest.builder()
        .symbol(SYMBOL).startDate(START).endDate(END)
        .monthlyAmount(new BigDecimal("100000")).purchaseDay(15)
        .build();

      StrategyResponse result =
        mapper.toStrategyResponse(request, transactions(3), calculation(), price("120.5"));

      assertThat(result.getStrategyType()).isEqualTo(StrategyType.DCA);
      assertThat(result.getSymbol()).isEqualTo(SYMBOL);
      assertThat(result.getStartDate()).isEqualTo(START);
      assertThat(result.getEndDate()).isEqualTo(END);
    }

    @Test
    @DisplayName("조건부 요청이면 strategyType 이 CONDITIONAL_PURCHASE")
    void 조건부_타입() {
      ConditionalStrategyRequest request = new ConditionalStrategyRequest();
      request.setSymbol(SYMBOL);
      request.setStartDate(START);
      request.setEndDate(END);

      StrategyResponse result =
        mapper.toStrategyResponse(request, transactions(2), calculation(), price("120.5"));

      assertThat(result.getStrategyType()).isEqualTo(StrategyType.CONDITIONAL_PURCHASE);
    }

    @Test
    @DisplayName("거래 건수는 목록 크기를 따라간다")
    void 거래_건수() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(7), calculation(), price("120.5"));

      assertThat(result.getTotalTransactions()).isEqualTo(7);
      assertThat(result.getTransactions()).hasSize(7);
    }

    @Test
    @DisplayName("거래가 없어도 0건으로 처리한다")
    void 거래_없음() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), List.of(), calculation(), price("120.5"));

      assertThat(result.getTotalTransactions()).isZero();
      assertThat(result.getTransactions()).isEmpty();
    }

    @Test
    @DisplayName("KRW 금액은 소수점을 버리고 정수로 만든다")
    void krw_반올림() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(1), calculation(), price("120.5"));

      // 1000000.678 → 1000001 (HALF_UP)
      assertThat(result.getTotalInvested()).isEqualByComparingTo("1000001");
      assertThat(result.getTotalInvested().scale()).isZero();
      // 1666666.5 → 1666667
      assertThat(result.getCurrentValueKrw()).isEqualByComparingTo("1666667");
      // 1234.4 → 1234
      assertThat(result.getRemainingCashKrw()).isEqualByComparingTo("1234");
    }

    @Test
    @DisplayName("USD 금액은 소수 두 자리로 맞춘다")
    void usd_반올림() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(1), calculation(), price("120.5"));

      assertThat(result.getCurrentValue()).isEqualByComparingTo("1234.57");
      assertThat(result.getCurrentValue().scale()).isEqualTo(2);
      assertThat(result.getTotalReturn()).isEqualByComparingTo("234.57");
      assertThat(result.getTotalReturnPercent()).isEqualByComparingTo("23.46");
      assertThat(result.getFxReturn()).isEqualByComparingTo("38.46");
    }

    @Test
    @DisplayName("주식 수는 소수 여섯 자리를 유지한다")
    void 주식수_자릿수() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(1), calculation(), price("120.5"));

      // 소수점 이하를 잘라버리면 소액 적립식에서 수량이 통째로 사라진다
      assertThat(result.getTotalShares().scale()).isEqualTo(6);
      assertThat(result.getTotalShares()).isEqualByComparingTo("12.345679");
    }

    @Test
    @DisplayName("환율은 반올림하지 않고 그대로 넘긴다")
    void 환율_원본_유지() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(1), calculation(), price("120.5"));

      assertThat(result.getAverageFxRate()).isEqualByComparingTo("1300.123456");
      assertThat(result.getCurrentFxRate()).isEqualByComparingTo("1350.0");
    }

    @Test
    @DisplayName("현재가는 시세를 그대로 쓴다")
    void 현재가() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(1), calculation(), price("120.5"));

      assertThat(result.getCurrentPrice()).isEqualByComparingTo("120.5");
    }

    @Test
    @DisplayName("배당수익률을 현재 평가금액 기준으로 계산한다")
    void 배당수익률() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(1), calculation(), price("120.5"));

      // 12.344 / (12.3456789 * 120.5) * 100 = 0.8297... → 0.83
      assertThat(result.getDividendYield()).isEqualByComparingTo("0.83");
    }

    @Test
    @DisplayName("전략 설명에 전략명과 거래 건수가 들어간다")
    void 전략_설명() {
      StrategyResponse dca = mapper.toStrategyResponse(
        dcaRequest(), transactions(5), calculation(), price("120.5"));
      assertThat(dca.getStrategyDetails()).isEqualTo("DCA 전략 - 5회 투자 실행");

      ConditionalStrategyRequest conditional = new ConditionalStrategyRequest();
      conditional.setSymbol(SYMBOL);
      StrategyResponse cond = mapper.toStrategyResponse(
        conditional, transactions(2), calculation(), price("120.5"));
      assertThat(cond.getStrategyDetails()).isEqualTo("조건부매수 전략 - 2회 투자 실행");
    }

    @Test
    @DisplayName("성과 요약 문자열 형식")
    void 성과_요약() {
      StrategyResponse result = mapper.toStrategyResponse(
        dcaRequest(), transactions(1), calculation(), price("120.5"));

      assertThat(result.getPerformanceSummary())
        .isEqualTo("총 수익: $234.57 (23.46%), 환차익: 3.85%");
    }

    private DcaStrategyRequest dcaRequest() {
      return DcaStrategyRequest.builder()
        .symbol(SYMBOL).startDate(START).endDate(END)
        .monthlyAmount(new BigDecimal("100000")).purchaseDay(15)
        .build();
    }
  }

  @Nested
  @DisplayName("toComparisonResponse")
  class ToComparisonResponse {

    @Test
    @DisplayName("요청과 계산 결과를 그대로 옮긴다")
    void 전달() {
      ComparisonItem best = ComparisonItem.builder().name("최고").code("AAPL").build();
      ComparisonItem worst = ComparisonItem.builder().name("최저").code("TSLA").build();

      SymbolComparisonRequest request = new SymbolComparisonRequest();
      request.setStartDate(START);
      request.setEndDate(END);
      request.setInvestmentAmount(new BigDecimal("1000000"));

      ComparisonCalculationResult calc = ComparisonCalculationResult.builder()
        .bestPerformer(best)
        .worstPerformer(worst)
        .averageReturn(new BigDecimal("12.34"))
        .medianReturn(new BigDecimal("10.00"))
        .summary("요약")
        .analysisDetails(Map.of("k", "v"))
        .build();

      ComparisonResponse result =
        mapper.toComparisonResponse(request, List.of(best, worst), calc);

      assertThat(result.getStartDate()).isEqualTo(START);
      assertThat(result.getEndDate()).isEqualTo(END);
      assertThat(result.getInvestmentAmount()).isEqualByComparingTo("1000000");
      assertThat(result.getItems()).containsExactly(best, worst);
      assertThat(result.getBestPerformer()).isSameAs(best);
      assertThat(result.getWorstPerformer()).isSameAs(worst);
      assertThat(result.getAverageReturn()).isEqualByComparingTo("12.34");
      assertThat(result.getMedianReturn()).isEqualByComparingTo("10.00");
      assertThat(result.getSummary()).isEqualTo("요약");
      assertThat(result.getAnalysisDetails()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("비교 대상이 없어도 빈 목록으로 만든다")
    void 항목_없음() {
      SymbolComparisonRequest request = new SymbolComparisonRequest();
      request.setStartDate(START);
      request.setEndDate(END);
      request.setInvestmentAmount(BigDecimal.ONE);

      ComparisonResponse result = mapper.toComparisonResponse(
        request, List.of(), ComparisonCalculationResult.builder().build());

      assertThat(result.getItems()).isEmpty();
      assertThat(result.getBestPerformer()).isNull();
    }
  }

  @Nested
  @DisplayName("toSimulationResponse")
  class ToSimulationResponse {

    @Test
    @DisplayName("통화별 자릿수를 나눠서 맞춘다")
    void 통화별_반올림() {
      SimulationResponse result = simulate(new BigDecimal("500.4"), null, null, null, null, null);

      assertThat(result.getCurrentValue().scale()).isEqualTo(2);      // USD
      assertThat(result.getCurrentValueKrw().scale()).isZero();       // KRW
      assertThat(result.getShares().scale()).isEqualTo(6);            // 주식 수
    }

    @Test
    @DisplayName("잔액이 있으면 현재 환율로 원화 잔액을 만든다")
    void 잔액_환산() {
      SimulationResponse result = simulate(new BigDecimal("100"), null, null, null, null, null);

      // 100 USD * 1350 = 135,000 KRW
      assertThat(result.getRemainingCashKrw()).isEqualByComparingTo("135000");
    }

    @Test
    @DisplayName("잔액이 0이면 원화 잔액도 0")
    void 잔액_없음() {
      SimulationResponse result = simulate(BigDecimal.ZERO, null, null, null, null, null);

      assertThat(result.getRemainingCashKrw()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("잔액이 음수여도 환산하지 않고 0으로 둔다")
    void 잔액_음수() {
      // isPositive 로 걸러서 음수 환산을 막는다
      SimulationResponse result =
        simulate(new BigDecimal("-50"), null, null, null, null, null);

      assertThat(result.getRemainingCashKrw()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("최적 매매 정보가 없으면 null 을 유지한다")
    void 최적값_없음() {
      SimulationResponse result = simulate(BigDecimal.TEN, null, null, null, null, null);

      assertThat(result.getOptimalBuyPrice()).isNull();
      assertThat(result.getOptimalSellPrice()).isNull();
      assertThat(result.getOptimalReturnPercent()).isNull();
      assertThat(result.getDividendsReinvested()).isNull();
    }

    @Test
    @DisplayName("최적 매매 정보가 있으면 USD 자릿수로 맞춘다")
    void 최적값_있음() {
      SimulationResponse result = simulate(
        BigDecimal.TEN,
        new BigDecimal("100.456"), new BigDecimal("150.454"),
        new BigDecimal("49.786"), new BigDecimal("7.891"),
        List.of(LocalDate.of(2024, 3, 1)));

      assertThat(result.getOptimalBuyPrice()).isEqualByComparingTo("100.46");
      assertThat(result.getOptimalSellPrice()).isEqualByComparingTo("150.45");
      assertThat(result.getOptimalReturnPercent()).isEqualByComparingTo("49.79");
      assertThat(result.getDividendsReinvested()).isEqualByComparingTo("7.89");
      assertThat(result.getDividendReinvestDates()).containsExactly(LocalDate.of(2024, 3, 1));
    }

    @Test
    @DisplayName("성과 요약 문자열 형식")
    void 성과_요약() {
      SimulationResponse result = simulate(BigDecimal.TEN, null, null, null, null, null);

      assertThat(result.getPerformanceSummary())
        .isEqualTo("총 수익: $234.57 (23.46%), 환차익: 3.85%");
    }

    @Test
    @DisplayName("요청 값은 그대로 옮긴다")
    void 요청_전달() {
      SimulationResponse result = simulate(BigDecimal.TEN, null, null, null, null, null);

      assertThat(result.getSymbol()).isEqualTo(SYMBOL);
      assertThat(result.getPurchaseDate()).isEqualTo(START);
      assertThat(result.getInvestmentAmount()).isEqualByComparingTo("1000000");
      assertThat(result.getCurrentDate()).isEqualTo(LocalDate.now());
    }

    private SimulationResponse simulate(
      BigDecimal remainingCash,
      BigDecimal optimalBuyPrice, BigDecimal optimalSellPrice,
      BigDecimal optimalReturnPercent, BigDecimal dividendsReinvested,
      List<LocalDate> reinvestDates) {

      SimulationRequest request = SimulationRequest.builder()
        .symbol(SYMBOL)
        .purchaseDate(START)
        .investmentAmount(new BigDecimal("1000000"))
        .build();

      return mapper.toSimulationResponse(
        request,
        new BigDecimal("100.00"),        // purchasePriceUsd
        new BigDecimal("12.3456789"),    // shares
        new BigDecimal("120.50"),        // currentPriceUsd
        new BigDecimal("1234.567"),      // currentValueUsd
        new BigDecimal("1666666.5"),     // currentValueKrw
        new BigDecimal("234.567"),       // stockReturn
        new BigDecimal("23.456"),        // stockReturnPercent
        new BigDecimal("1300.00"),       // purchaseFxRate
        new BigDecimal("1350.00"),       // currentFxRate
        new BigDecimal("38.461"),        // fxReturn
        new BigDecimal("3.846"),         // fxReturnPercent
        new BigDecimal("12.344"),        // totalDividends
        new BigDecimal("1.234"),         // dividendYield
        new BigDecimal("2.505"),         // tradingFee
        remainingCash,
        new BigDecimal("1667900.9"),     // totalAssetKrw
        new BigDecimal("667900.4"),      // totalReturnKrw
        new BigDecimal("23.456"),        // totalReturnPercent
        optimalBuyPrice == null ? null : LocalDate.of(2024, 2, 1),
        optimalBuyPrice,
        optimalSellPrice == null ? null : LocalDate.of(2024, 8, 1),
        optimalSellPrice,
        optimalReturnPercent,
        dividendsReinvested,
        reinvestDates);
    }
  }

  @Nested
  @DisplayName("toInvestmentResponse")
  class ToInvestmentResponse {

    private SimulationResponse backtest() {
      return SimulationResponse.builder()
        .symbol(SYMBOL)
        .purchaseDate(START)
        .investmentAmount(new BigDecimal("1000000"))
        .purchasePrice(new BigDecimal("100.00"))
        .shares(new BigDecimal("12.345679"))
        .build();
    }

    @Test
    @DisplayName("과거 매수 경로는 시뮬레이션 값을 그대로 쓴다")
    void 과거_매수() {
      InvestmentResponse result = mapper.toInvestmentResponse(new InvestmentRequest(), backtest());

      assertThat(result.getSymbol()).isEqualTo(SYMBOL);
      assertThat(result.getTotalCost()).isEqualByComparingTo("1000000");
      assertThat(result.getStatus()).isEqualTo("SUCCESS");
      assertThat(result.getMessage()).isEqualTo("과거 매수 백테스트가 완료되었습니다");
      assertThat(result.isPortfolioCreated()).isFalse();
      assertThat(result.getPortfolioStatus()).isEqualTo("BACKTEST_COMPLETED");
      assertThat(result.getHoldingId()).isNull();
    }

    @Test
    @DisplayName("보유 주식 경로는 보유 정보를 우선한다")
    void 보유_주식() {
      HoldingDto holding = holding();

      InvestmentResponse result = mapper.toInvestmentResponse(holding, backtest());

      assertThat(result.getHoldingId()).isEqualTo(holding.holdingId());
      assertThat(result.getSymbol()).isEqualTo(holding.symbol());
      // 매수일·수량·투자금은 시뮬레이션이 아니라 보유 기록에서 온다
      assertThat(result.getPurchaseDate()).isEqualTo(holding.getPurchaseDate());
      assertThat(result.getShares()).isEqualByComparingTo(holding.getShares());
      assertThat(result.getInvestmentAmount()).isEqualByComparingTo(holding.getTotalInvested());
      assertThat(result.getMessage()).isEqualTo("보유 주식 백테스트가 완료되었습니다");
    }

    @Test
    @DisplayName("두 경로 모두 시뮬레이션 결과를 함께 담는다")
    void 시뮬레이션_포함() {
      SimulationResponse backtest = backtest();

      assertThat(mapper.toInvestmentResponse(new InvestmentRequest(), backtest).getSimulation())
        .isSameAs(backtest);
      assertThat(mapper.toInvestmentResponse(holding(), backtest).getSimulation())
        .isSameAs(backtest);
    }
  }

  @Nested
  @DisplayName("toComparisonItem")
  class ToComparisonItem {

    @Test
    @DisplayName("시뮬레이션은 category 가 SYMBOL")
    void 시뮬레이션_변환() {
      SimulationResponse simulation = SimulationResponse.builder()
        .symbol(SYMBOL)
        .investmentAmount(new BigDecimal("1000000"))
        .shares(new BigDecimal("12.345679"))
        .purchasePrice(new BigDecimal("100.00"))
        .currentValue(new BigDecimal("1234.57"))
        .currentValueKrw(new BigDecimal("1666667"))
        .totalReturn(new BigDecimal("234.57"))
        .totalReturnPercent(new BigDecimal("23.46"))
        .totalReturnKrw(new BigDecimal("667900"))
        .fxReturn(new BigDecimal("38.46"))
        .fxReturnPercent(new BigDecimal("3.85"))
        .totalDividends(new BigDecimal("12.34"))
        .optimalBuyDate(LocalDate.of(2024, 2, 1))
        .optimalSellDate(LocalDate.of(2024, 8, 1))
        .build();

      ComparisonItem item = mapper.toComparisonItemFromSimulation(simulation, "애플");

      assertThat(item.getName()).isEqualTo("애플");
      assertThat(item.getCode()).isEqualTo(SYMBOL);
      assertThat(item.getCategory()).isEqualTo("SYMBOL");
      assertThat(item.getTotalReturnPercent()).isEqualByComparingTo("23.46");
      // 날짜는 문자열로 바뀐다
      assertThat(item.getOptimalBuyDate()).isEqualTo("2024-02-01");
      assertThat(item.getOptimalSellDate()).isEqualTo("2024-08-01");
      assertThat(item.getAdditionalData()).isSameAs(simulation);
    }

    @Test
    @DisplayName("최적 매매일이 없으면 문자열도 null")
    void 최적일_없음() {
      SimulationResponse simulation = SimulationResponse.builder().symbol(SYMBOL).build();

      ComparisonItem item = mapper.toComparisonItemFromSimulation(simulation, "애플");

      assertThat(item.getOptimalBuyDate()).isNull();
      assertThat(item.getOptimalSellDate()).isNull();
    }

    @Test
    @DisplayName("전략은 category 가 STRATEGY 이고 성과 요약을 note 로 옮긴다")
    void 전략_변환() {
      StrategyResponse strategy = StrategyResponse.builder()
        .symbol(SYMBOL)
        .totalInvested(new BigDecimal("1000001"))
        .totalShares(new BigDecimal("12.345679"))
        .averagePrice(new BigDecimal("81.01"))
        .currentValue(new BigDecimal("1234.57"))
        .currentValueKrw(new BigDecimal("1666667"))
        .totalReturn(new BigDecimal("234.57"))
        .totalReturnPercent(new BigDecimal("23.46"))
        .totalReturnKrw(new BigDecimal("667900"))
        .fxReturn(new BigDecimal("38.46"))
        .fxReturnPercent(new BigDecimal("3.85"))
        .totalDividends(new BigDecimal("12.34"))
        .performanceSummary("총 수익: $234.57 (23.46%), 환차익: 3.85%")
        .build();

      ComparisonItem item = mapper.toComparisonItemFromStrategy(strategy, "DCA");

      assertThat(item.getName()).isEqualTo("DCA");
      assertThat(item.getCategory()).isEqualTo("STRATEGY");
      assertThat(item.getPerformanceNote())
        .isEqualTo("총 수익: $234.57 (23.46%), 환차익: 3.85%");
      assertThat(item.getAdditionalData()).isSameAs(strategy);
      // 전략에는 최적 매매 개념이 없다
      assertThat(item.getOptimalBuyDate()).isNull();
    }
  }

  private static HoldingDto holding() {
    return new HoldingDto(
      "holding-1",
      "account-1",
      SYMBOL,
      new BigDecimal("5.5"),           // totalQuantity
      new BigDecimal("90.00"),         // avgPurchasePrice
      new BigDecimal("495000"),        // totalInvestedAmount
      new BigDecimal("3.20"),          // totalDividends
      LocalDate.of(2024, 6, 1),        // lastDividendCalculated
      LocalDate.of(2024, 3, 10).atStartOfDay());
  }
}
