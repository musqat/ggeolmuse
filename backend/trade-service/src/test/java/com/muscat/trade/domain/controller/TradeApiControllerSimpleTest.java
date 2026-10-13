package com.muscat.trade.domain.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.service.TradingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeApiController 단위 테스트")
class TradeApiControllerSimpleTest {

  @Mock
  private TradingService tradingService;

  @InjectMocks
  private TradeApiController controller;

  private final Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("test-user-123").build();

  private final List<TradeResponseDto> trades = List.of(new TradeResponseDto(1L, 1L, "AAPL", TradeType.BUY,
    BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, LocalDate.of(2024, 1, 15),
    LocalDateTime.of(2024, 1, 15, 10, 0), TradeStatus.COMPLETED, null));

  @Test
  @DisplayName("종목별 내역은 토큰의 사용자와 경로의 종목으로 조회한다")
  void bySymbol() {
    given(tradingService.getTradesBySymbol("test-user-123", "AAPL")).willReturn(trades);

    ResponseEntity<List<TradeResponseDto>> response = controller.getTradeHistoryBySymbol(jwt, "AAPL");

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isSameAs(trades);
  }

  @Test
  @DisplayName("전체 내역은 페이지 번호와 크기를 그대로 넘긴다")
  void paged() {
    given(tradingService.getUserTrades("test-user-123", 2, 50)).willReturn(trades);

    ResponseEntity<List<TradeResponseDto>> response = controller.getTradeHistory(jwt, 2, 50);

    assertThat(response.getBody()).isSameAs(trades);
  }

  @Test
  @DisplayName("기간 내역은 시작일 · 종료일을 그대로 넘긴다")
  void byPeriod() {
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 1, 31);
    given(tradingService.getTradesByDateRange("test-user-123", start, end)).willReturn(trades);

    ResponseEntity<List<TradeResponseDto>> response = controller.getTradeHistoryByPeriod(jwt, start, end);

    assertThat(response.getBody()).isSameAs(trades);
  }
}
