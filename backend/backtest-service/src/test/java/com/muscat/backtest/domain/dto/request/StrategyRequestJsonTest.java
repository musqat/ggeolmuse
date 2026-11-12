package com.muscat.backtest.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PortfolioAllocation;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.model.StrategyParameter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 전략 타입 getter · 금액 · 주식 수 · 이동평균 기간 검사가 이력 저장 JSON 모양을 바꾸지 않는지
class StrategyRequestJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("요청마다 자기 전략 타입을 담는다")
  void strategyType_PerRequest() {
    assertThat(dca().getStrategyType()).isEqualTo(StrategyType.DCA);
    assertThat(conditional().getStrategyType()).isEqualTo(StrategyType.CONDITIONAL_PURCHASE);
    assertThat(maCross().getStrategyType()).isEqualTo(StrategyType.MA_CROSS);
    assertThat(maBand().getStrategyType()).isEqualTo(StrategyType.MA_BAND);
  }

  @Test
  @DisplayName("전략 타입은 이력 JSON 에 들어가지 않는다")
  void strategyType_NotSerialized() throws Exception {
    assertThat(objectMapper.writeValueAsString(dca())).doesNotContain("strategyType").contains("\"symbol\":\"AAPL\"");
    assertThat(objectMapper.writeValueAsString(conditional())).doesNotContain("strategyType");
    assertThat(objectMapper.writeValueAsString(maCross())).doesNotContain("strategyType");
    assertThat(objectMapper.writeValueAsString(maBand())).doesNotContain("strategyType");
  }

  @Test
  @DisplayName("이동평균 교차는 원금 · 짧은 · 긴 기간 · 신호 종목을 이력 JSON 에 저장하고 기간 검사 값은 넣지 않으며 비운 기간은 20 · 60, 비운 신호 종목은 매매 종목으로 본다")
  void maCrossWindowsSaved() throws Exception {
    MaCrossStrategyRequest saved = objectMapper.readValue(objectMapper.writeValueAsString(maCross()),
      MaCrossStrategyRequest.class);
    MaCrossStrategyRequest empty = objectMapper.readValue("{\"symbol\":\"AAPL\",\"investmentAmount\":1000}",
      MaCrossStrategyRequest.class);

    assertThat(objectMapper.writeValueAsString(maCross()))
      .contains("\"investmentAmount\":10000", "\"shortWindow\":5", "\"longWindow\":20", "\"signalSymbol\":\"^GSPC\"")
      .doesNotContain("windowOrder").doesNotContain("OrDefault");
    assertThat(saved.shortWindowOrDefault()).isEqualTo(5);
    assertThat(saved.longWindowOrDefault()).isEqualTo(20);
    assertThat(empty.shortWindowOrDefault()).isEqualTo(20);
    assertThat(empty.longWindowOrDefault()).isEqualTo(60);
    assertThat(saved.signalSymbolOrDefault()).isEqualTo("^GSPC");
    assertThat(empty.signalSymbolOrDefault()).isEqualTo("AAPL");
  }

  @Test
  @DisplayName("이동평균 밴드는 원금 · 평균 기간 · 위 · 아래 비율 · 신호 종목을 이력 JSON 에 저장하고 비운 칸은 200 · 0.025 · 0.025 · 매매 종목으로 본다")
  void maBandSaved() throws Exception {
    MaBandStrategyRequest saved = objectMapper.readValue(objectMapper.writeValueAsString(maBand()),
      MaBandStrategyRequest.class);
    MaBandStrategyRequest empty = objectMapper.readValue("{\"symbol\":\"SPY\",\"investmentAmount\":1000}",
      MaBandStrategyRequest.class);

    assertThat(objectMapper.writeValueAsString(maBand()))
      .contains("\"investmentAmount\":10000", "\"bandWindow\":100", "\"upperRate\":0.03", "\"lowerRate\":0.05",
        "\"signalSymbol\":\"^NDX\"")
      .doesNotContain("OrDefault");
    assertThat(saved.bandWindowOrDefault()).isEqualTo(100);
    assertThat(saved.upperRateOrDefault()).isEqualByComparingTo("0.03");
    assertThat(saved.lowerRateOrDefault()).isEqualByComparingTo("0.05");
    assertThat(empty.bandWindowOrDefault()).isEqualTo(200);
    assertThat(empty.upperRateOrDefault()).isEqualByComparingTo("0.025");
    assertThat(empty.lowerRateOrDefault()).isEqualByComparingTo("0.025");
    assertThat(saved.signalSymbolOrDefault()).isEqualTo("^NDX");
    assertThat(empty.signalSymbolOrDefault()).isEqualTo("SPY");
  }

  @Test
  @DisplayName("조건부 · 이동평균 교차 · 밴드는 트레일링 스탑 두 칸을 이력 JSON 에 저장하고 칸 검사 값은 넣지 않는다")
  void trailingStopSaved() throws Exception {
    ConditionalStrategyRequest conditional = conditional();
    conditional.setTrailingStopRate(new BigDecimal("0.1"));
    conditional.setTrailingSellRate(new BigDecimal("0.5"));
    MaCrossStrategyRequest maCross = maCross();
    maCross.setTrailingStopRate(new BigDecimal("0.1"));
    MaBandStrategyRequest maBand = maBand();
    maBand.setTrailingStopRate(new BigDecimal("0.15"));
    maBand.setTrailingSellRate(BigDecimal.ONE);

    assertThat(objectMapper.writeValueAsString(conditional))
      .contains("\"trailingStopRate\":0.1", "\"trailingSellRate\":0.5").doesNotContain("trailingSellWithStop");
    assertThat(objectMapper.writeValueAsString(maCross))
      .contains("\"trailingStopRate\":0.1").doesNotContain("trailingSellWithStop");
    assertThat(objectMapper.writeValueAsString(maBand))
      .contains("\"trailingStopRate\":0.15", "\"trailingSellRate\":1").doesNotContain("trailingSellWithStop");
  }

  @Test
  @DisplayName("조건부 · 이동평균 교차 · 밴드는 부분 익절 단계를 이력 JSON 에 저장하고 다시 읽으며 칸 검사 값은 넣지 않는다")
  void partialTakeProfitsSaved() throws Exception {
    List<PartialTakeProfit> steps = List.of(new PartialTakeProfit(new BigDecimal("0.2"), new BigDecimal("0.25")),
      new PartialTakeProfit(new BigDecimal("0.5"), BigDecimal.ONE));
    ConditionalStrategyRequest conditional = conditional();
    conditional.setPartialTakeProfits(steps);
    MaCrossStrategyRequest maCross = maCross();
    maCross.setPartialTakeProfits(steps);
    MaBandStrategyRequest maBand = maBand();
    maBand.setPartialTakeProfits(steps);
    String saved = "\"partialTakeProfits\":[{\"gainRate\":0.2,\"sellRate\":0.25},{\"gainRate\":0.5,\"sellRate\":1}]";

    for (Object request : List.of(conditional, maCross, maBand)) {
      assertThat(objectMapper.writeValueAsString(request)).contains(saved).doesNotContain("partialTakeProfitAscending");
    }
    MaBandStrategyRequest read = objectMapper.readValue(objectMapper.writeValueAsString(maBand), MaBandStrategyRequest.class);
    assertThat(read.getPartialTakeProfits()).isEqualTo(steps);
  }

  @Test
  @DisplayName("적립식 회당 금액은 amountPerPurchase 로 저장하고 monthlyAmount 도 읽으며 주기를 비우면 매달")
  void dcaAmountAlias() throws Exception {
    DcaStrategyRequest old = objectMapper.readValue("{\"symbol\":\"AAPL\",\"monthlyAmount\":1000,\"purchaseDay\":1}",
      DcaStrategyRequest.class);

    assertThat(old.getAmountPerPurchase()).isEqualByComparingTo("1000");
    assertThat(old.getFrequency()).isEqualTo(DcaFrequency.MONTHLY);
    assertThat(objectMapper.writeValueAsString(dca())).contains("\"amountPerPurchase\":1000", "\"frequency\":\"MONTHLY\"")
      .doesNotContain("monthlyAmount");
  }

  @Test
  @DisplayName("주식 수는 이력 JSON 에 저장하고 금액 · 주식 수 검사 값은 넣지 않는다")
  void sharesSavedWithoutCheck() throws Exception {
    DcaStrategyRequest dca = dca();
    dca.setAmountPerPurchase(null);
    dca.setSharesPerPurchase(2);
    ConditionalStrategyRequest conditional = conditional();
    conditional.setSharesPerPurchase(1);
    SimulationRequest simulation = SimulationRequest.builder().symbol("AAPL").purchaseDate(LocalDate.of(2023, 1, 2))
      .sharesPerPurchase(3).build();

    assertThat(objectMapper.writeValueAsString(dca)).contains("\"sharesPerPurchase\":2").doesNotContain("amountOrShares");
    assertThat(objectMapper.writeValueAsString(conditional)).contains("\"sharesPerPurchase\":1")
      .doesNotContain("amountOrShares");
    assertThat(objectMapper.writeValueAsString(simulation)).contains("\"sharesPerPurchase\":3")
      .doesNotContain("amountOrShares");
  }

  @Test
  @DisplayName("전략 비교 칩의 회당 금액은 amountPerPurchase 로 저장하고 monthlyAmount 도 읽으며 회당 금액 검사 값은 넣지 않는다")
  void comparisonChipAmountAlias() throws Exception {
    StrategyParameter old = objectMapper.readValue(
      "{\"strategyType\":\"DCA\",\"monthlyAmount\":1000,\"purchaseDay\":31,\"frequency\":\"WEEKLY\"}",
      StrategyParameter.class);

    assertThat(old.getAmountPerPurchase()).isEqualByComparingTo("1000");
    assertThat(old.getFrequency()).isEqualTo(DcaFrequency.WEEKLY);
    assertThat(objectMapper.writeValueAsString(old)).contains("\"amountPerPurchase\":1000", "\"strategyType\":\"DCA\"")
      .doesNotContain("monthlyAmount").doesNotContain("amountForPurchases");
  }

  private DcaStrategyRequest dca() {
    return DcaStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .amountPerPurchase(BigDecimal.valueOf(1000)).purchaseDay(1).userId("user-1").build();
  }

  @Test
  @DisplayName("전략 비교의 이동평균 칩은 짧은 · 긴 기간을 이력 JSON 에 저장하고 기간 검사 값은 넣지 않는다")
  void comparisonChipWindowsSaved() throws Exception {
    StrategyParameter chip = objectMapper.readValue("{\"strategyType\":\"MA_CROSS\",\"shortWindow\":5,\"longWindow\":20}",
      StrategyParameter.class);

    assertThat(objectMapper.writeValueAsString(chip)).contains("\"shortWindow\":5", "\"longWindow\":20")
      .doesNotContain("windowOrder");
  }

  @Test
  @DisplayName("전략 비교의 밴드 칩은 평균 기간 · 위 · 아래 비율을 이력 JSON 에 저장한다")
  void comparisonChipBandSaved() throws Exception {
    StrategyParameter chip = objectMapper.readValue(
      "{\"strategyType\":\"MA_BAND\",\"bandWindow\":100,\"upperRate\":0.03,\"lowerRate\":0.05}", StrategyParameter.class);

    assertThat(objectMapper.writeValueAsString(chip))
      .contains("\"strategyType\":\"MA_BAND\"", "\"bandWindow\":100", "\"upperRate\":0.03", "\"lowerRate\":0.05");
  }

  private MaCrossStrategyRequest maCross() {
    return MaCrossStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .investmentAmount(BigDecimal.valueOf(10000)).shortWindow(5).longWindow(20).signalSymbol("^GSPC").userId("user-1").build();
  }

  private MaBandStrategyRequest maBand() {
    return MaBandStrategyRequest.builder().symbol("SPY").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .investmentAmount(BigDecimal.valueOf(10000)).bandWindow(100).upperRate(new BigDecimal("0.03"))
      .lowerRate(new BigDecimal("0.05")).signalSymbol("^NDX").userId("user-1").build();
  }

  @Test
  @DisplayName("포트폴리오는 줄 금액 방식 · 총액 · 종목 줄 · 매수 방식을 이력 JSON 에 저장하고 다시 읽으며 줄 금액 셈 값은 넣지 않는다")
  void portfolioSaved() throws Exception {
    PortfolioRequest request = PortfolioRequest.builder().startDate(LocalDate.of(2023, 1, 1))
      .endDate(LocalDate.of(2024, 1, 1)).allocation(PortfolioAllocation.WEIGHT).totalAmount(BigDecimal.valueOf(1000000))
      .assets(List.of(new PortfolioAsset(" spy", null, BigDecimal.valueOf(60)),
        new PortfolioAsset("QQQ", null, BigDecimal.valueOf(40))))
      .method(PortfolioMethod.DCA).frequency(DcaFrequency.WEEKLY).userId("user-1").build();

    String json = objectMapper.writeValueAsString(request);
    PortfolioRequest saved = objectMapper.readValue(json, PortfolioRequest.class);

    assertThat(json).contains("\"allocation\":\"WEIGHT\"", "\"totalAmount\":1000000",
        "\"assets\":[{\"symbol\":\" spy\",\"amount\":null,\"weightPercent\":60}", "\"method\":\"DCA\"")
      .doesNotContain("normalizedSymbol").doesNotContain("amountOf");
    assertThat(saved.getAssets()).extracting(PortfolioAsset::normalizedSymbol).containsExactly("SPY", "QQQ");
    assertThat(saved.amountOf(saved.getAssets().get(1))).isEqualByComparingTo("400000");
  }

  private ConditionalStrategyRequest conditional() {
    return ConditionalStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .totalInvestment(BigDecimal.valueOf(10000)).dropPercentage(BigDecimal.valueOf(5)).maxPurchases(3).userId("user-1").build();
  }
}
