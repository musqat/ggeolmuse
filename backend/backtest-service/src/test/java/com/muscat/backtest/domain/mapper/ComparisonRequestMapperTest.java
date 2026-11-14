package com.muscat.backtest.domain.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.model.StrategyParameter;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ComparisonRequestMapper 비교 요청 만들기")
class ComparisonRequestMapperTest {

  private static final LocalDate START = LocalDate.of(2023, 1, 2);
  private static final LocalDate END = LocalDate.of(2024, 1, 2);

  // 기간 · 투자금 100만원 · 사용자와 수수료 · 환율 둘 · 재투자 · 배당세 · 양도세 · 기간 끝 매도를 모두 채운 비교 요청
  private static <T extends BaseComparisonRequest> T withOptions(T request) {
    request.setStartDate(START);
    request.setEndDate(END);
    request.setInvestmentAmount(new BigDecimal("1000000"));
    request.setUserId("user-1");
    request.setTradingFeeRate(new BigDecimal("0.001"));
    request.setPurchaseFxRate(new BigDecimal("1300"));
    request.setCurrentFxRate(new BigDecimal("1350"));
    request.setReinvestDividends(true);
    request.setDividendTaxRate(new BigDecimal("0.15"));
    request.setCapitalGainsTax(true);
    request.setPeriodEnd(PeriodEnd.SELL_AT_END);
    return request;
  }

  private static StrategyComparisonRequest strategies() {
    StrategyComparisonRequest request = withOptions(new StrategyComparisonRequest());
    request.setSymbol("AAPL");
    return request;
  }

  private static StrategyParameter chip(StrategyType type) {
    StrategyParameter chip = new StrategyParameter();
    chip.setStrategyType(type);
    return chip;
  }

  private static BigDecimal rate(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  @Test
  @DisplayName("종목 비교는 종목 · 시작일 매수 · 종료일 매도와 투자금 · 수수료 · 환율 · 배당 · 양도세 · 기간 끝 옵션 그대로")
  void symbolSimulation() {
    SimulationRequest simulation = ComparisonRequestMapper.symbolSimulation(
      withOptions(new SymbolComparisonRequest()), "MSFT");

    assertThat(simulation.getSymbol()).isEqualTo("MSFT");
    assertThat(simulation.getPurchaseDate()).isEqualTo(START);
    assertThat(simulation.getSaleDate()).isEqualTo(END);
    assertThat(simulation.getInvestmentAmount()).isEqualByComparingTo("1000000");
    assertThat(simulation.getUserId()).isEqualTo("user-1");
    assertThat(simulation.getTradingFeeRate()).isEqualByComparingTo("0.001");
    assertThat(simulation.getPurchaseFxRate()).isEqualByComparingTo("1300");
    assertThat(simulation.getCurrentFxRate()).isEqualByComparingTo("1350");
    assertThat(simulation.getReinvestDividends()).isTrue();
    assertThat(simulation.getDividendTaxRate()).isEqualByComparingTo("0.15");
    assertThat(simulation.getCapitalGainsTax()).isTrue();
    assertThat(simulation.getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
  }

  @Test
  @DisplayName("단순 칩은 비교 종목과 칩의 매수일")
  void simpleSimulation() {
    StrategyParameter chip = chip(StrategyType.SIMPLE);
    chip.setPurchaseDate(LocalDate.of(2023, 6, 1));

    SimulationRequest simulation = ComparisonRequestMapper.simpleSimulation(strategies(), chip);

    assertThat(simulation.getSymbol()).isEqualTo("AAPL");
    assertThat(simulation.getPurchaseDate()).isEqualTo(LocalDate.of(2023, 6, 1));
    assertThat(simulation.getSaleDate()).isEqualTo(END);
  }

  @Test
  @DisplayName("옵션을 비우면 수수료 · 배당세 0, 재투자 끔, 적립식 투자 간격 1")
  void emptyOptions() {
    StrategyComparisonRequest request = new StrategyComparisonRequest();
    request.setSymbol("AAPL");
    request.setStartDate(START);
    request.setEndDate(END);
    request.setInvestmentAmount(new BigDecimal("1000000"));

    DcaStrategyRequest dca = (DcaStrategyRequest) ComparisonRequestMapper.strategyRequest(request,
      chip(StrategyType.DCA));

    assertThat(dca.getTradingFeeRate()).isEqualByComparingTo("0");
    assertThat(dca.getDividendTaxRate()).isEqualByComparingTo("0");
    assertThat(dca.getReinvestDividends()).isFalse();
    assertThat(dca.getInvestmentInterval()).isEqualTo(1);
  }

  @Test
  @DisplayName("적립식 칩은 칩의 회당 금액 · 주기 · 첫 매수일 · 투자일 · 간격과 비교 투자금을 예산으로")
  void dca() {
    StrategyParameter chip = chip(StrategyType.DCA);
    chip.setAmountPerPurchase(new BigDecimal("100000"));
    chip.setFrequency(DcaFrequency.WEEKLY);
    chip.setFirstPurchaseDate(LocalDate.of(2023, 2, 1));
    chip.setPurchaseDay(15);
    chip.setInvestmentInterval(3);

    DcaStrategyRequest dca = (DcaStrategyRequest) ComparisonRequestMapper.strategyRequest(strategies(), chip);

    assertThat(dca.getSymbol()).isEqualTo("AAPL");
    assertThat(dca.getAmountPerPurchase()).isEqualByComparingTo("100000");
    assertThat(dca.getFrequency()).isEqualTo(DcaFrequency.WEEKLY);
    assertThat(dca.getFirstPurchaseDate()).isEqualTo(LocalDate.of(2023, 2, 1));
    assertThat(dca.getPurchaseDay()).isEqualTo(15);
    assertThat(dca.getInvestmentInterval()).isEqualTo(3);
    assertThat(dca.getTotalInvestmentLimit()).isEqualByComparingTo("1000000");
    assertThat(dca.getReinvestDividends()).isTrue();
  }

  @ParameterizedTest(name = "익절 {0} · 손절 {1}: 재투자 {2}")
  @CsvSource({"0.1, , false", ", 0.1, false", "0.1, 0.1, false", ", , true"})
  @DisplayName("조건부 칩은 익절이나 손절을 켜면 재투자 끔, 회당 금액 · 하락률 · 방향 · 최대 횟수는 칩 값")
  void conditional(String takeProfit, String stopLoss, boolean reinvest) {
    StrategyParameter chip = chip(StrategyType.CONDITIONAL_PURCHASE);
    chip.setAmountPerPurchase(new BigDecimal("100000"));
    chip.setDropPercentage(new BigDecimal("0.05"));
    chip.setDirection(PriceDirection.RISE);
    chip.setMaxPurchases(4);
    chip.setTakeProfitRate(rate(takeProfit));
    chip.setStopLossRate(rate(stopLoss));

    ConditionalStrategyRequest conditional = (ConditionalStrategyRequest) ComparisonRequestMapper.strategyRequest(
      strategies(), chip);

    assertThat(conditional.getReinvestDividends()).isEqualTo(reinvest);
    assertThat(conditional.getAmountPerPurchase()).isEqualByComparingTo("100000");
    assertThat(conditional.getDropPercentage()).isEqualByComparingTo("0.05");
    assertThat(conditional.getDirection()).isEqualTo(PriceDirection.RISE);
    assertThat(conditional.getMaxPurchases()).isEqualTo(4);
    assertThat(conditional.getTotalInvestment()).isEqualByComparingTo("1000000");
  }

  @Test
  @DisplayName("이동평균 교차 · 밴드 칩은 재투자를 켜도 끄고 기간 · 밴드 칸은 칩 값")
  void signalChips() {
    StrategyParameter cross = chip(StrategyType.MA_CROSS);
    cross.setShortWindow(20);
    cross.setLongWindow(60);
    StrategyParameter band = chip(StrategyType.MA_BAND);
    band.setBandWindow(20);
    band.setUpperRate(new BigDecimal("0.05"));

    MaCrossStrategyRequest crossRequest = (MaCrossStrategyRequest) ComparisonRequestMapper.strategyRequest(
      strategies(), cross);
    MaBandStrategyRequest bandRequest = (MaBandStrategyRequest) ComparisonRequestMapper.strategyRequest(
      strategies(), band);

    assertThat(crossRequest.getReinvestDividends()).isFalse();
    assertThat(crossRequest.getShortWindow()).isEqualTo(20);
    assertThat(crossRequest.getLongWindow()).isEqualTo(60);
    assertThat(crossRequest.getInvestmentAmount()).isEqualByComparingTo("1000000");
    assertThat(bandRequest.getReinvestDividends()).isFalse();
    assertThat(bandRequest.getBandWindow()).isEqualTo(20);
    assertThat(bandRequest.getUpperRate()).isEqualByComparingTo("0.05");
    assertThat(bandRequest.getLowerRate()).isNull();
  }

  @Test
  @DisplayName("단순 · 비운 전략 타입은 STRATEGY_TYPE_MISMATCH")
  void unsupportedType() {
    assertThatThrownBy(() -> ComparisonRequestMapper.strategyRequest(strategies(), chip(StrategyType.SIMPLE)))
      .extracting("code").isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
    assertThatThrownBy(() -> ComparisonRequestMapper.strategyRequest(strategies(), chip(null)))
      .extracting("code").isEqualTo(BacktestResponse.STRATEGY_TYPE_MISMATCH);
  }
}
