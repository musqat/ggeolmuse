package com.muscat.backtest.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.muscat.backtest.common.enums.type.StrategyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 이력 저장이 요청을 JSON 으로 쓰므로 전략 타입 getter 가 저장 모양을 바꾸지 않는지 본다
class StrategyRequestJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("요청마다 자기 전략 타입을 말한다")
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

  private DcaStrategyRequest dca() {
    return DcaStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .monthlyAmount(BigDecimal.valueOf(1000)).purchaseDay(1).userId("user-1").build();
  }

  private ConditionalStrategyRequest conditional() {
    return ConditionalStrategyRequest.builder().symbol("AAPL").startDate(LocalDate.of(2023, 1, 1)).endDate(LocalDate.of(2024, 1, 1))
      .totalInvestment(BigDecimal.valueOf(10000)).dropPercentage(BigDecimal.valueOf(5)).maxPurchases(3).userId("user-1").build();
  }
}
