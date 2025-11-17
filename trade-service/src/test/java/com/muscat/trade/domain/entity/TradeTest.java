package com.muscat.trade.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trade Entity 테스트")
class TradeTest {

  @Test
  @DisplayName("Builder로 Trade 객체 생성")
  void createTrade_WithBuilder_Success() {
    // Given & When
    Trade trade = Trade.builder()
      .tradeId("TRADE_001")
      .userId("user-123")
      .accountId(1L)
      .symbol("AAPL")
      .tradeType(TradeType.BUY)
      .quantity(new BigDecimal("10.5"))
      .price(new BigDecimal("150.00"))
      .totalAmount(new BigDecimal("1575.00"))
      .fee(new BigDecimal("1.50"))
      .tradeDate(LocalDate.of(2024, 1, 15))
      .executedAt(LocalDateTime.of(2024, 1, 15, 10, 30))
      .build();

    // Then
    assertThat(trade.getTradeId()).isEqualTo("TRADE_001");
    assertThat(trade.getUserId()).isEqualTo("user-123");
    assertThat(trade.getAccountId()).isEqualTo(1L);
    assertThat(trade.getSymbol()).isEqualTo("AAPL");
    assertThat(trade.getTradeType()).isEqualTo(TradeType.BUY);
    assertThat(trade.getQuantity()).isEqualByComparingTo(new BigDecimal("10.5"));
    assertThat(trade.getPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(trade.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1575.00"));
    assertThat(trade.getFee()).isEqualByComparingTo(new BigDecimal("1.50"));
    assertThat(trade.getTradeDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    assertThat(trade.getExecutedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30));
  }

  @Test
  @DisplayName("기본 생성자로 Trade 객체 생성")
  void createTrade_WithNoArgsConstructor_Success() {
    // When
    Trade trade = new Trade();

    // Then
    assertThat(trade).isNotNull();
  }

  @Test
  @DisplayName("AllArgsConstructor로 Trade 객체 생성")
  void createTrade_WithAllArgsConstructor_Success() {
    // When
    Trade trade = new Trade(
      "TRADE_002",
      "user-456",
      2L,
      "MSFT",
      TradeType.SELL,
      new BigDecimal("5"),
      new BigDecimal("300.00"),
      new BigDecimal("1500.00"),
      new BigDecimal("1.00"),
      LocalDate.of(2024, 1, 16),
      LocalDateTime.of(2024, 1, 16, 14, 20),
      LocalDateTime.now()
    );

    // Then
    assertThat(trade.getTradeId()).isEqualTo("TRADE_002");
    assertThat(trade.getSymbol()).isEqualTo("MSFT");
    assertThat(trade.getTradeType()).isEqualTo(TradeType.SELL);
  }

  @Test
  @DisplayName("Setter로 값 변경")
  void modifyTrade_WithSetters_Success() {
    // Given
    Trade trade = new Trade();

    // When
    trade.setTradeId("TRADE_003");
    trade.setUserId("user-789");
    trade.setAccountId(3L);
    trade.setSymbol("GOOGL");
    trade.setTradeType(TradeType.BUY);
    trade.setQuantity(new BigDecimal("20"));
    trade.setPrice(new BigDecimal("100.00"));
    trade.setTotalAmount(new BigDecimal("2000.00"));
    trade.setFee(new BigDecimal("2.00"));
    trade.setTradeDate(LocalDate.of(2024, 1, 17));
    trade.setExecutedAt(LocalDateTime.of(2024, 1, 17, 9, 0));

    // Then
    assertThat(trade.getTradeId()).isEqualTo("TRADE_003");
    assertThat(trade.getUserId()).isEqualTo("user-789");
    assertThat(trade.getSymbol()).isEqualTo("GOOGL");
  }

  @Test
  @DisplayName("Fee 기본값은 ZERO")
  void defaultFee_IsZero() {
    // When
    Trade trade = Trade.builder()
      .tradeId("TRADE_004")
      .userId("user-999")
      .accountId(1L)
      .symbol("TSLA")
      .tradeType(TradeType.BUY)
      .quantity(new BigDecimal("1"))
      .price(new BigDecimal("200.00"))
      .totalAmount(new BigDecimal("200.00"))
      .tradeDate(LocalDate.now())
      .executedAt(LocalDateTime.now())
      .build();

    // Then
    assertThat(trade.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
