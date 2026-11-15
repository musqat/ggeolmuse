package com.muscat.backtest.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("이력에 저장된 요청 JSON 다시 읽기")
class StoredRequestJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  // 적립식 · 조건부 · 이동평균 교차 · 밴드 · 시뮬레이션 요청을 이력에 저장한 JSON
  private static final List<String> STORED = List.of(
    "{\"symbol\":\"AAPL\",\"startDate\":\"2023-01-02\",\"endDate\":\"2024-01-02\",\"userId\":\"u\",\"amountPerPu"
      + "rchase\":100000,\"sharesPerPurchase\":null,\"frequency\":\"MONTHLY\",\"firstPurchaseDate\":null,\"pu"
      + "rchaseDay\":null,\"investmentInterval\":1,\"totalInvestmentLimit\":null,\"purchaseFxRate\":1300,\""
      + "currentFxRate\":null,\"reinvestDividends\":false,\"tradingFeeRate\":0.0025,\"dividendTaxRate\":0,"
      + "\"capitalGainsTax\":true,\"periodEnd\":\"SELL_AT_END\",\"yearEndHarvest\":true}",
    "{\"symbol\":\"AAPL\",\"startDate\":\"2023-01-02\",\"endDate\":\"2024-01-02\",\"userId\":\"u\",\"amountPerPu"
      + "rchase\":100000,\"sharesPerPurchase\":null,\"totalInvestment\":null,\"direction\":\"DROP\",\"dropPer"
      + "centage\":0.05,\"takeProfitRate\":null,\"stopLossRate\":null,\"trailingStopRate\":null,\"trailingS"
      + "ellRate\":null,\"partialTakeProfits\":null,\"maxPurchases\":null,\"purchaseFxRate\":null,\"current"
      + "FxRate\":1350,\"reinvestDividends\":true,\"tradingFeeRate\":0,\"dividendTaxRate\":0.15,\"capitalGa"
      + "insTax\":null,\"periodEnd\":null,\"taxSale\":\"SELL\",\"yearEndHarvest\":null}",
    "{\"symbol\":\"SPY\",\"startDate\":\"2023-01-02\",\"endDate\":\"2024-01-02\",\"userId\":\"u\",\"investmentAm"
      + "ount\":1000000,\"shortWindow\":null,\"longWindow\":null,\"purchaseFxRate\":null,\"currentFxRate\":n"
      + "ull,\"reinvestDividends\":false,\"tradingFeeRate\":0.001,\"dividendTaxRate\":0,\"capitalGainsTax\""
      + ":true,\"periodEnd\":null,\"taxSale\":null,\"yearEndHarvest\":false,\"signalSymbol\":null,\"trailing"
      + "StopRate\":0.1,\"trailingSellRate\":null,\"partialTakeProfits\":null,\"executionTiming\":null}",
    "{\"symbol\":\"QQQ\",\"startDate\":\"2023-01-02\",\"endDate\":\"2024-01-02\",\"userId\":\"u\",\"investmentAm"
      + "ount\":1000000,\"bandWindow\":null,\"upperRate\":null,\"lowerRate\":null,\"purchaseFxRate\":1290.5,"
      + "\"currentFxRate\":null,\"reinvestDividends\":false,\"tradingFeeRate\":0,\"dividendTaxRate\":0,\"cap"
      + "italGainsTax\":null,\"periodEnd\":\"HOLD\",\"taxSale\":null,\"yearEndHarvest\":null,\"signalSymbol\":"
      + "null,\"trailingStopRate\":null,\"trailingSellRate\":null,\"partialTakeProfits\":null,\"executionT"
      + "iming\":null}",
    "{\"symbol\":\"AAPL\",\"purchaseDate\":\"2023-01-02\",\"saleDate\":\"2024-01-02\",\"investmentAmount\":10"
      + "00000,\"sharesPerPurchase\":null,\"tradingFeeRate\":0.0025,\"userId\":\"u\",\"purchaseFxRate\":null,"
      + "\"currentFxRate\":null,\"reinvestDividends\":true,\"dividendTaxRate\":0,\"capitalGainsTax\":false,"
      + "\"periodEnd\":null,\"yearEndHarvest\":null}");

  private static final List<Class<?>> TYPES = List.of(DcaStrategyRequest.class, ConditionalStrategyRequest.class, MaCrossStrategyRequest.class, MaBandStrategyRequest.class, SimulationRequest.class);

  @Test
  @DisplayName("저장된 JSON 을 읽어 다시 쓰면 칸 이름 · 값이 같음")
  void rewritesSameFields() throws Exception {
    for (int i = 0; i < STORED.size(); i++) {
      Object read = objectMapper.readValue(STORED.get(i), TYPES.get(i));

      assertThat(objectMapper.readTree(objectMapper.writeValueAsString(read))).as(TYPES.get(i).getSimpleName())
        .isEqualTo(objectMapper.readTree(STORED.get(i)));
    }
  }

  @Test
  @DisplayName("저장된 JSON 의 수동 환율 · 재투자 · 수수료율 · 원천징수 · 양도세 · 기간 끝 · 연말 정리를 그대로 읽음")
  void readsOptions() throws Exception {
    DcaStrategyRequest dca = objectMapper.readValue(STORED.get(0), DcaStrategyRequest.class);
    ConditionalStrategyRequest conditional = objectMapper.readValue(STORED.get(1), ConditionalStrategyRequest.class);
    SimulationRequest simulation = objectMapper.readValue(STORED.get(4), SimulationRequest.class);

    assertThat(List.of(dca.getPurchaseFxRate(), dca.getTradingFeeRate(), dca.getDividendTaxRate()))
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("1300"), new BigDecimal("0.0025"), BigDecimal.ZERO);
    assertThat(List.of(dca.getReinvestDividends(), dca.getCapitalGainsTax(), dca.getYearEndHarvest()))
      .containsExactly(false, true, true);
    assertThat(dca.getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
    assertThat(conditional.getCurrentFxRate()).isEqualByComparingTo("1350");
    assertThat(conditional.getDividendTaxRate()).isEqualByComparingTo("0.15");
    assertThat(conditional.getReinvestDividends()).isTrue();
    assertThat(conditional.getCapitalGainsTax()).isNull();
    assertThat(simulation.getReinvestDividends()).isTrue();
    assertThat(simulation.getTradingFeeRate()).isEqualByComparingTo("0.0025");
  }

  @Test
  @DisplayName("공통 옵션 칸이 없는 JSON 은 재투자 끔 · 수수료율 0 · 원천징수 0 으로, null 로 저장한 칸은 null 로 읽음")
  void defaultsWhenMissing() throws Exception {
    for (Class<?> type : TYPES) {
      Object missing = objectMapper.readValue("{\"symbol\":\"AAPL\"}", type);
      Object nulls = objectMapper.readValue(
        "{\"symbol\":\"AAPL\",\"tradingFeeRate\":null,\"reinvestDividends\":null}", type);

      JsonNode missingJson = objectMapper.readTree(objectMapper.writeValueAsString(missing));
      JsonNode nullsJson = objectMapper.readTree(objectMapper.writeValueAsString(nulls));

      assertThat(missingJson.get("reinvestDividends").asBoolean(true)).as(type.getSimpleName()).isFalse();
      assertThat(missingJson.get("tradingFeeRate").decimalValue()).as(type.getSimpleName()).isEqualByComparingTo("0");
      assertThat(missingJson.get("dividendTaxRate").decimalValue()).as(type.getSimpleName()).isEqualByComparingTo("0");
      assertThat(nullsJson.get("reinvestDividends").isNull()).as(type.getSimpleName()).isTrue();
      assertThat(nullsJson.get("tradingFeeRate").isNull()).as(type.getSimpleName()).isTrue();
    }
  }
}
