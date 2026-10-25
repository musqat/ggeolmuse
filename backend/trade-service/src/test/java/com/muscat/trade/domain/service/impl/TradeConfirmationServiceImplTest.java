package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeSettledEvent;
import com.muscat.trade.common.metrics.TradeMetrics;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeConfirmedEvent;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.HoldingsRebuilder;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("정산된 매수 완료")
class TradeConfirmationServiceImplTest {

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private HoldingsRebuilder holdingsRebuilder;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private TradeMetrics tradeMetrics;

  @InjectMocks
  private TradeConfirmationServiceImpl service;

  private final TradeSettledEvent event = TradeSettledEvent.builder()
    .tradeId(7L).userId("user-1").accountId(20L).build();

  private Trade trade(String userId, TradeStatus status) {
    return Trade.builder().id(7L).userId(userId).accountId(20L).symbol("AAPL")
      .tradeType(TradeType.BUY).status(status).build();
  }

  private void verifyNothingDone() {
    verify(holdingsRebuilder, never()).rebuild(any(), any(), any(), anyBoolean());
    verify(eventPublisher, never()).publishEvent(any(Object.class));
    verify(tradeMetrics, never()).recordCompleted(any());
  }

  @Test
  @DisplayName("대기 매수를 완료로 바꾸고 그 종목 보유를 다시 쌓은 뒤 배당 몫 재계산을 알리고 완료 지표에 남긴다")
  void confirm_Pending_CompletesRebuildsAndPublishes() {
    Trade trade = trade("user-1", TradeStatus.PENDING);
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade));

    service.confirm(event);

    assertThat(trade.getStatus()).isEqualTo(TradeStatus.COMPLETED);
    verify(holdingsRebuilder).rebuild("user-1", 20L, "AAPL", false);
    verify(eventPublisher).publishEvent(new TradeConfirmedEvent("user-1", 20L, "AAPL"));
    verify(tradeMetrics).recordCompleted(trade);
  }

  @Test
  @DisplayName("이미 완료된 체결이면 아무것도 안 한다")
  void confirm_NotPending_Skips() {
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade("user-1", TradeStatus.COMPLETED)));

    service.confirm(event);

    verifyNothingDone();
  }

  @Test
  @DisplayName("취소된 체결은 완료로 바꾸지 않는다")
  void confirm_Cancelled_Skips() {
    Trade trade = trade("user-1", TradeStatus.PENDING);
    trade.cancel("INSUFFICIENT_USD_BALANCE", LocalDateTime.of(2026, 10, 19, 9, 0));
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade));

    service.confirm(event);

    assertThat(trade.getStatus()).isEqualTo(TradeStatus.CANCELLED);
    verifyNothingDone();
  }

  @Test
  @DisplayName("체결이 없으면 아무것도 안 한다")
  void confirm_Missing_Skips() {
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.empty());

    service.confirm(event);

    verifyNothingDone();
  }

  @Test
  @DisplayName("이벤트 사용자와 체결 사용자가 다르면 완료하지 않는다")
  void confirm_OtherUser_Skips() {
    Trade trade = trade("someone-else", TradeStatus.PENDING);
    given(tradeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(trade));

    service.confirm(event);

    assertThat(trade.getStatus()).isEqualTo(TradeStatus.PENDING);
    verifyNothingDone();
  }
}
