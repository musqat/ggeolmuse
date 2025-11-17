package com.muscat.trade.domain.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.DividendService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionHistoryController 단위 테스트")
class TransactionHistoryControllerSimpleTest {

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendService dividendService;

  @InjectMocks
  private TransactionHistoryController transactionHistoryController;

  private Jwt mockJwt;

  @BeforeEach
  void setUp() {
    mockJwt = Jwt.withTokenValue("token")
      .header("alg", "none")
      .subject("test-user-123")
      .build();
  }

  @Test
  @DisplayName("거래 내역 조회 - 매수/매도만")
  void getTransactionHistory_OnlyTrades_ReturnsHistory() {
    // Given
    Trade buyTrade = Trade.builder()
      .tradeId("TRADE_001")
      .userId("test-user-123")
      .accountId(1L)
      .symbol("AAPL")
      .tradeType(TradeType.BUY)
      .quantity(new BigDecimal("10"))
      .price(new BigDecimal("150.00"))
      .totalAmount(new BigDecimal("1500.00"))
      .fee(new BigDecimal("1.50"))
      .tradeDate(LocalDate.of(2024, 1, 15))
      .executedAt(LocalDateTime.of(2024, 1, 15, 10, 30))
      .build();

    when(
      tradeRepository.findByUserIdOrderByExecutedAtDesc(eq("test-user-123"), any(Pageable.class)))
      .thenReturn(new PageImpl<>(List.of(buyTrade)));

    when(dividendService.getUserDividends(eq("test-user-123")))
      .thenReturn(List.of());

    // When
    ResponseEntity<List<Map<String, Object>>> response = transactionHistoryController.getTransactionHistory(
      mockJwt);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).get("type")).isEqualTo("BUY");
    assertThat(response.getBody().get(0).get("symbol")).isEqualTo("AAPL");
  }

  @Test
  @DisplayName("거래 내역 조회 - 배당 포함")
  void getTransactionHistory_WithDividends_ReturnsHistory() {
    // Given
    Map<String, Object> dividend = new HashMap<>();
    dividend.put("tradeId", "TRADE_001");
    dividend.put("symbol", "AAPL");
    dividend.put("netAmount", new BigDecimal("25.00"));
    dividend.put("grossAmount", new BigDecimal("30.00"));
    dividend.put("taxAmount", new BigDecimal("5.00"));
    dividend.put("dividendDate", LocalDate.of(2024, 1, 20));
    dividend.put("processedAt", LocalDateTime.of(2024, 1, 20, 12, 0));
    dividend.put("dividendPerShare", new BigDecimal("0.25"));
    dividend.put("shares", 100);

    when(
      tradeRepository.findByUserIdOrderByExecutedAtDesc(eq("test-user-123"), any(Pageable.class)))
      .thenReturn(new PageImpl<>(List.of()));

    when(dividendService.getUserDividends(eq("test-user-123")))
      .thenReturn(List.of(dividend));

    // When
    ResponseEntity<List<Map<String, Object>>> response = transactionHistoryController.getTransactionHistory(
      mockJwt);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).get("type")).isEqualTo("DIVIDEND");
    assertThat(response.getBody().get(0).get("symbol")).isEqualTo("AAPL");
    assertThat(response.getBody().get(0).get("totalAmount")).isEqualTo(new BigDecimal("25.00"));
  }

  @Test
  @DisplayName("거래 내역 조회 - 빈 결과")
  void getTransactionHistory_NoData_ReturnsEmptyList() {
    // Given
    when(
      tradeRepository.findByUserIdOrderByExecutedAtDesc(eq("test-user-123"), any(Pageable.class)))
      .thenReturn(new PageImpl<>(List.of()));

    when(dividendService.getUserDividends(eq("test-user-123")))
      .thenReturn(List.of());

    // When
    ResponseEntity<List<Map<String, Object>>> response = transactionHistoryController.getTransactionHistory(
      mockJwt);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEmpty();
  }
}
