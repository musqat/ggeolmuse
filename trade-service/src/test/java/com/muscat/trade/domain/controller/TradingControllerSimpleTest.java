package com.muscat.trade.domain.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.request.TradeRequestDto;
import com.muscat.trade.domain.dto.request.TradingCapacityRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.dto.response.TradingCapacityResponseDto;
import com.muscat.trade.domain.service.TradingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@DisplayName("TradingController 단위 테스트")
class TradingControllerSimpleTest {

  @Mock
  private TradingService tradingService;

  @InjectMocks
  private TradingController tradingController;

  private Jwt mockJwt;

  @BeforeEach
  void setUp() {
    mockJwt = Jwt.withTokenValue("token")
      .header("alg", "none")
      .subject("test-user-123")
      .build();
  }

  @Test
  @DisplayName("매수 요청 처리")
  void buyStock_ValidRequest_ReturnsOk() {
    // Given
    TradeRequestDto request = new TradeRequestDto();
    request.setAccountId("1");
    request.setSymbol("AAPL");
    request.setQuantity(new BigDecimal("10"));
    request.setTradeDate(LocalDate.of(2024, 1, 15));
    request.setPriceType(PriceType.CLOSE);

    TradeResponseDto expectedResponse = new TradeResponseDto(
      "TRADE_001",
      "1",
      "AAPL",
      TradeType.BUY,
      new BigDecimal("10"),
      new BigDecimal("150.00"),
      new BigDecimal("1500.00"),
      new BigDecimal("1.50"),
      LocalDate.of(2024, 1, 15),
      LocalDateTime.now()
    );

    when(tradingService.buyStock(
      eq("test-user-123"),
      eq(1L),
      eq("AAPL"),
      any(BigDecimal.class),
      any(LocalDate.class),
      eq(PriceType.CLOSE),
      isNull()
    )).thenReturn(expectedResponse);

    // When
    ResponseEntity<TradeResponseDto> response = tradingController.buyStock(mockJwt, request);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().tradeId()).isEqualTo("TRADE_001");
    assertThat(response.getBody().symbol()).isEqualTo("AAPL");
  }

  @Test
  @DisplayName("매도 요청 처리")
  void sellStock_ValidRequest_ReturnsOk() {
    // Given
    TradeRequestDto request = new TradeRequestDto();
    request.setAccountId("1");
    request.setSymbol("AAPL");
    request.setQuantity(new BigDecimal("5"));
    request.setTradeDate(LocalDate.of(2024, 1, 15));
    request.setPriceType(PriceType.CLOSE);

    TradeResponseDto expectedResponse = new TradeResponseDto(
      "TRADE_002",
      "1",
      "AAPL",
      TradeType.SELL,
      new BigDecimal("5"),
      new BigDecimal("160.00"),
      new BigDecimal("800.00"),
      new BigDecimal("0.80"),
      LocalDate.of(2024, 1, 15),
      LocalDateTime.now()
    );

    when(tradingService.sellStock(
      eq("test-user-123"),
      eq(1L),
      eq("AAPL"),
      any(BigDecimal.class),
      any(LocalDate.class),
      eq(PriceType.CLOSE),
      isNull()
    )).thenReturn(expectedResponse);

    // When
    ResponseEntity<TradeResponseDto> response = tradingController.sellStock(mockJwt, request);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().symbol()).isEqualTo("AAPL");
    assertThat(response.getBody().tradeType()).isEqualTo(TradeType.SELL);
  }

  @Test
  @DisplayName("매수 가능 수량 계산")
  void calculateBuyingCapacity_ValidRequest_ReturnsOk() {
    // Given
    TradingCapacityRequestDto request = new TradingCapacityRequestDto();
    request.setAccountId("1");
    request.setSymbol("AAPL");
    request.setTradeDate(LocalDate.of(2024, 1, 15));

    TradingCapacityResponseDto expectedResponse = new TradingCapacityResponseDto(
      "AAPL",
      LocalDate.of(2024, 1, 15),
      new BigDecimal("150.00"),
      new BigDecimal("10000.00"),
      new BigDecimal("66"),
      new BigDecimal("9900.00"),
      "USD",
      new BigDecimal("50"),
      new BigDecimal("50")
    );

    when(tradingService.calculateBuyingCapacity(eq("test-user-123"),
      any(TradingCapacityRequestDto.class)))
      .thenReturn(expectedResponse);

    // When
    ResponseEntity<TradingCapacityResponseDto> response = tradingController.calculateBuyingCapacity(
      mockJwt, request);

    // Then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().symbol()).isEqualTo("AAPL");
    assertThat(response.getBody().maxShares()).isEqualByComparingTo(new BigDecimal("66"));
  }
}
