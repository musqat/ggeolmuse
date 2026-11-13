package com.muscat.backtest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.muscat.backtest.common.enums.type.TradeReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StrategyTransaction 응답 JSON")
class StrategyTransactionTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private static StrategyTransaction.StrategyTransactionBuilder buy() {
    LocalDate date = LocalDate.of(2024, 3, 4);
    return StrategyTransaction.builder().date(date).actualDate(date).price(BigDecimal.TEN).shares(BigDecimal.ONE)
      .amount(new BigDecimal("13000")).fxRate(new BigDecimal("1300")).trigger("월정액");
  }

  @Test
  @DisplayName("단일 전략 거래에는 종목 · 이유 칸이 없다")
  void singleStrategyHasNoSymbolOrReason() {
    JsonNode json = objectMapper.valueToTree(buy().build());

    assertThat(json.has("symbol")).isFalse();
    assertThat(json.has("reason")).isFalse();
    assertThat(json.get("trigger").asText()).isEqualTo("월정액");
  }

  @Test
  @DisplayName("매수 원가는 넣은 원화를 그 날짜 환율로 바꾼 값에 쓴 현금을 더하고, 넣은 원화가 없으면 쓴 현금만이다")
  void costUsd() {
    StrategyTransaction deposit = buy().cashUsed(new BigDecimal("50")).build();
    StrategyTransaction noAmount = buy().amount(null).cashUsed(new BigDecimal("50")).build();

    assertThat(deposit.costUsd()).isEqualByComparingTo("60");
    assertThat(noAmount.costUsd()).isEqualByComparingTo("50");
  }

  @Test
  @DisplayName("포트폴리오 거래는 종목 · 이유를 싣는다")
  void portfolioHasSymbolAndReason() {
    JsonNode json = objectMapper.valueToTree(buy().symbol("SPY").reason(TradeReason.DCA_BUY).build());

    assertThat(json.get("symbol").asText()).isEqualTo("SPY");
    assertThat(json.get("reason").asText()).isEqualTo("DCA_BUY");
  }
}
