package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeRejectedEvent;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.HoldingsRebuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("반영 실패 체결 취소")
class TradeCancellationServiceImplTest {

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendRepository dividendRepository;

  @Mock
  private HoldingsRebuilder holdingsRebuilder;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private TradeCancellationServiceImpl service;

  private final TradeRejectedEvent event = TradeRejectedEvent.builder()
    .tradeId(7L).userId("user-1").accountId(20L).reasonCode("INSUFFICIENT_USD_BALANCE").build();

  @BeforeEach
  void setUp() {
    service = new TradeCancellationServiceImpl(tradeRepository, dividendRepository, holdingsRebuilder,
      meterRegistry);
  }

  private Trade trade(String userId) {
    return Trade.builder().id(7L).userId(userId).accountId(20L).symbol("AAPL")
      .executedAt(LocalDateTime.of(2026, 10, 8, 9, 0)).build();
  }

  private double dividendRecordedCount() {
    return meterRegistry.get("trade.cancel.review").tag("reason", "dividend_recorded").counter().count();
  }

  @Test
  @DisplayName("체결을 취소됨으로 바꾸고 그 종목 보유를 다시 쌓는다")
  void cancel_MarksCancelledAndRebuilds() {
    Trade trade = trade("user-1");
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade));

    service.cancel(event);

    assertThat(trade.getStatus()).isEqualTo(TradeStatus.CANCELLED);
    assertThat(trade.getCancelReason()).isEqualTo("INSUFFICIENT_USD_BALANCE");
    assertThat(trade.getCancelledAt()).isNotNull();
    verify(holdingsRebuilder).rebuild("user-1", 20L, "AAPL");
    assertThat(dividendRecordedCount()).isZero();
  }

  @Test
  @DisplayName("체결이 없으면 아무것도 안 한다")
  void cancel_NotFound_DoesNothing() {
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.empty());

    service.cancel(event);

    verify(holdingsRebuilder, never()).rebuild(any(), any(), any());
  }

  @Test
  @DisplayName("이미 취소한 체결이면 아무것도 안 한다")
  void cancel_AlreadyCancelled_DoesNothing() {
    Trade trade = trade("user-1");
    trade.cancel("ACCOUNT_NOT_FOUND", LocalDateTime.of(2026, 10, 8, 8, 0));
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade));

    service.cancel(event);

    assertThat(trade.getCancelReason()).isEqualTo("ACCOUNT_NOT_FOUND");
    verify(holdingsRebuilder, never()).rebuild(any(), any(), any());
  }

  @Test
  @DisplayName("이벤트 사용자와 체결 사용자가 다르면 취소하지 않는다")
  void cancel_UserMismatch_DoesNothing() {
    Trade trade = trade("someone-else");
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade));

    service.cancel(event);

    assertThat(trade.getStatus()).isEqualTo(TradeStatus.COMPLETED);
    verify(holdingsRebuilder, never()).rebuild(any(), any(), any());
  }

  @Test
  @DisplayName("이미 배당이 지급된 체결이면 확인 필요 카운터를 올린다")
  void cancel_DividendRecorded_Counts() {
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade("user-1")));
    given(dividendRepository.existsByTradeId(anyLong())).willReturn(true);

    service.cancel(event);

    assertThat(dividendRecordedCount()).isEqualTo(1.0);
  }
}
