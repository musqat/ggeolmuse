package com.muscat.backtest.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.StrategyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 전략 타입 getter 가 이력 저장 JSON 모양을 바꾸지 않는지
class StrategyRequestJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("요청마다 자기 전략 타입을 담는다")
  void strategyType_PerRequest() {
    assertThat(dca().getStrategyType()).isEqualTo(StrategyType.DCA);
    assertThat(conditional().getStrategyType()).isEqualTo(StrategyType.CONDITIONAL_PURCHASE);
  }

  @Test
  @DisplayName("전략 타입은 이력 JSON 에 들어가지 않는다")
  void strategyType_NotSerialized() throws Exception {
    assertThat(objectMapper.writeValueAsString(dca())).doesNotContain("strategyType").contains("\"symbol\":\"AAPL\"");
    assertThat(objectMapper.writeValueAsString(conditional())).doesNotContain("strategyType");
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

  private DcaStrategyRequest dca() {
    return DcaStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .amountPerPurchase(BigDecimal.valueOf(1000)).purchaseDay(1).userId("user-1").build();
  }

  private ConditionalStrategyRequest conditional() {
    return ConditionalStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .totalInvestment(BigDecimal.valueOf(10000)).dropPercentage(BigDecimal.valueOf(5)).maxPurchases(3).userId("user-1").build();
  }
}
