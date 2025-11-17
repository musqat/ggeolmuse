package com.muscat.trade.domain.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.domain.service.HoldingsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioController 단위 테스트")
class PortfolioControllerSimpleTest {

  @Mock
  private HoldingsService holdingsService;

  @InjectMocks
  private PortfolioController portfolioController;

  private Jwt mockJwt;

  @BeforeEach
  void setUp() {
    mockJwt = Jwt.withTokenValue("token")
      .header("alg", "none")
      .subject("test-user-123")
      .build();
  }

  @Test
  @DisplayName("전체 포트폴리오 조회")
  void getPortfolio_ReturnsHoldings() {
    // Given
    List<HoldingResponseDto> holdings = List.of(
      new HoldingResponseDto(
        "HOLD_001",
        "1",
        "AAPL",
        new BigDecimal("50"),
        new BigDecimal("150.00"),
        new BigDecimal("7500.00"),
        LocalDateTime.now(),
        new BigDecimal("160.00"),
        new BigDecimal("8000.00"),
        new BigDecimal("500.00"),
        new BigDecimal("6.67")
      )
    );

    when(holdingsService.getPortfolio(eq("test-user-123"), isNull()))
      .thenReturn(holdings);

    // When
    ResponseEntity<List<HoldingResponseDto>> response = portfolioController.getPortfolio(mockJwt);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).symbol()).isEqualTo("AAPL");
  }

  @Test
  @DisplayName("계좌별 포트폴리오 조회")
  void getAccountPortfolio_ReturnsAccountHoldings() {
    // Given
    List<HoldingResponseDto> holdings = List.of(
      new HoldingResponseDto(
        "HOLD_002",
        "1",
        "MSFT",
        new BigDecimal("30"),
        new BigDecimal("300.00"),
        new BigDecimal("9000.00"),
        LocalDateTime.now(),
        new BigDecimal("320.00"),
        new BigDecimal("9600.00"),
        new BigDecimal("600.00"),
        new BigDecimal("6.67")
      )
    );

    when(holdingsService.getPortfolio(eq("test-user-123"), eq(1L)))
      .thenReturn(holdings);

    // When
    ResponseEntity<List<HoldingResponseDto>> response = portfolioController.getAccountPortfolio(
      mockJwt, "1");

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).symbol()).isEqualTo("MSFT");
  }

  @Test
  @DisplayName("포트폴리오 요약 조회")
  void getPortfolioSummary_ReturnsSummary() {
    // Given
    PortfolioSummary summary = PortfolioSummary.builder()
      .totalInvestedAmount(new BigDecimal("10000.00"))
      .totalCurrentValue(new BigDecimal("11000.00"))
      .totalUnrealizedPnL(new BigDecimal("1000.00"))
      .totalReturnRate(new BigDecimal("10.00"))
      .holdingCount(5)
      .build();

    when(holdingsService.getPortfolioSummary(eq("test-user-123"), isNull()))
      .thenReturn(summary);

    // When
    ResponseEntity<PortfolioSummary> response = portfolioController.getPortfolioSummary(mockJwt,
      null);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getTotalInvestedAmount()).isEqualByComparingTo(
      new BigDecimal("10000.00"));
    assertThat(response.getBody().getHoldingCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("특정 종목 보유 내역 조회")
  void getHoldingBySymbol_ReturnsHolding() {
    // Given
    HoldingResponseDto holding = new HoldingResponseDto(
      "HOLD_003",
      "1",
      "AAPL",
      new BigDecimal("100"),
      new BigDecimal("150.00"),
      new BigDecimal("15000.00"),
      LocalDateTime.now(),
      new BigDecimal("160.00"),
      new BigDecimal("16000.00"),
      new BigDecimal("1000.00"),
      new BigDecimal("6.67")
    );

    when(holdingsService.getHoldingBySymbol(eq("test-user-123"), eq(1L), eq("AAPL")))
      .thenReturn(holding);

    // When
    ResponseEntity<HoldingResponseDto> response = portfolioController.getHoldingBySymbol(mockJwt,
      "1", "AAPL");

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().symbol()).isEqualTo("AAPL");
    assertThat(response.getBody().totalQuantity()).isEqualByComparingTo(new BigDecimal("100"));
  }
}
