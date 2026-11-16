package com.muscat.messaging.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.JacksonUtils;

// Kafka JSON 직렬화기와 같은 ObjectMapper 로 칸 모양을 대조(이미 발행된 메시지와 호환)
@DisplayName("이벤트 JSON 칸 모양")
class EventJsonTest {

  private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();

  @Test
  @DisplayName("배당 몫의 배당락일은 yyyy-MM-dd 문자열로 쓰고 읽는다")
  void dividendPayoutExDate() throws Exception {
    String json = mapper.writeValueAsString(DividendPayoutEvent.builder().exDate(LocalDate.of(2024, 2, 9)).build());

    assertThat(json).contains("\"exDate\":\"2024-02-09\"");
    assertThat(mapper.readValue("{\"exDate\":\"2024-02-09\"}", DividendPayoutEvent.class).getExDate())
      .isEqualTo(LocalDate.of(2024, 2, 9));
  }

  @Test
  @DisplayName("분할일은 yyyy-MM-dd 문자열, 비율은 숫자로 쓰고 읽음")
  void stockSplitDateAndRatio() throws Exception {
    String json = mapper.writeValueAsString(StockSplitEvent.builder()
      .symbol("NVDA").splitDate(LocalDate.of(2024, 6, 10)).ratio(new BigDecimal("10")).build());

    assertThat(json).contains("\"splitDate\":\"2024-06-10\"").contains("\"ratio\":10");
    StockSplitEvent read = mapper.readValue("{\"splitDate\":\"2024-06-10\",\"ratio\":0.1}", StockSplitEvent.class);
    assertThat(read.getSplitDate()).isEqualTo(LocalDate.of(2024, 6, 10));
    assertThat(read.getRatio()).isEqualByComparingTo("0.1");
  }

  @Test
  @DisplayName("체결 구분은 BUY · SELL 문자열로 쓰고 읽는다")
  void tradeCompletedType() throws Exception {
    String json = mapper.writeValueAsString(
      TradeCompletedEvent.builder().tradeType(TradeCompletedEvent.TradeType.SELL).build());

    assertThat(json).contains("\"tradeType\":\"SELL\"");
    assertThat(mapper.readValue("{\"tradeType\":\"BUY\"}", TradeCompletedEvent.class).getTradeType())
      .isEqualTo(TradeCompletedEvent.TradeType.BUY);
  }
}
