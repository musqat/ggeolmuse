package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.enums.type.SettlementStatus;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.domain.account.entity.TradeSettlement;
import com.muscat.user.domain.account.repository.TradeSettlementRepository;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("체결 이벤트 정산")
class TradeSettlementServiceImplTest {

  @Mock
  private AccountService accountService;

  @Mock
  private TradeSettlementRepository tradeSettlementRepository;

  @Mock
  private TradeRejectedEventProducer tradeRejectedEventProducer;

  @Mock
  private PlatformTransactionManager transactionManager;

  private TradeSettlementServiceImpl service;

  private final TradeCompletedEvent event = TradeCompletedEvent.builder()
    .tradeId(7L)
    .userId("user-1")
    .accountId(20L)
    .tradeType("BUY")
    .totalAmount(new BigDecimal("668.11"))
    .build();

  @BeforeEach
  void setUp() {
    service = new TradeSettlementServiceImpl(accountService, tradeSettlementRepository,
      tradeRejectedEventProducer, transactionManager);
  }

  @Test
  @DisplayName("처음 온 체결은 잔액에 반영하고 APPLIED 로 남긴다")
  void settle_NewTrade_AppliesAndRecords() {
    service.settle(event);

    verify(accountService).processTradeEvent(event);
    ArgumentCaptor<TradeSettlement> saved = ArgumentCaptor.forClass(TradeSettlement.class);
    verify(tradeSettlementRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getTradeId()).isEqualTo(7L);
    assertThat(saved.getValue().getAccountId()).isEqualTo(20L);
    assertThat(saved.getValue().getStatus()).isEqualTo(SettlementStatus.APPLIED);
  }

  @Test
  @DisplayName("이미 처리한 체결은 잔액을 다시 바꾸지 않는다")
  void settle_AlreadySettled_Skips() {
    given(tradeSettlementRepository.existsById(7L)).willReturn(true);

    service.settle(event);

    verify(accountService, never()).processTradeEvent(any());
    verify(tradeSettlementRepository, never()).saveAndFlush(any());
    verify(tradeRejectedEventProducer, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("기술 실패는 기록 · 발행 없이 예외를 그대로 던진다")
  void settle_TechnicalFailure_Rethrows() {
    willThrow(new IllegalStateException("db down")).given(accountService).processTradeEvent(event);

    assertThatThrownBy(() -> service.settle(event)).isInstanceOf(IllegalStateException.class);
    verify(tradeSettlementRepository, never()).saveAndFlush(any());
    verify(tradeRejectedEventProducer, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("잔액 부족이면 REJECTED 를 저장하고 반영 실패를 발행한다")
  void settle_InsufficientBalance_RejectsAndPublishes() {
    willThrow(new AccountException(AccountResponse.INSUFFICIENT_USD_BALANCE))
      .given(accountService).processTradeEvent(event);

    service.settle(event);

    ArgumentCaptor<TradeSettlement> saved = ArgumentCaptor.forClass(TradeSettlement.class);
    verify(tradeSettlementRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(SettlementStatus.REJECTED);
    assertThat(saved.getValue().getReasonCode()).isEqualTo("INSUFFICIENT_USD_BALANCE");
    verify(tradeRejectedEventProducer)
      .publish(event, "INSUFFICIENT_USD_BALANCE", AccountResponse.INSUFFICIENT_USD_BALANCE.getMessage());
  }

  @Test
  @DisplayName("5xx AccountException 은 업무 실패가 아니라 그대로 던진다")
  void settle_ServerErrorCode_Rethrows() {
    willThrow(new AccountException(AccountResponse.EXCHANGE_RATE_SERVICE_ERROR))
      .given(accountService).processTradeEvent(event);

    assertThatThrownBy(() -> service.settle(event)).isInstanceOf(AccountException.class);
    verify(tradeRejectedEventProducer, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("반영 실패 발행이 실패하면 예외를 던진다")
  void settle_PublishFails_Throws() {
    willThrow(new AccountException(AccountResponse.INSUFFICIENT_USD_BALANCE))
      .given(accountService).processTradeEvent(event);
    willThrow(new IllegalStateException("broker down"))
      .given(tradeRejectedEventProducer).publish(any(), any(), any());

    assertThatThrownBy(() -> service.settle(event)).isInstanceOf(IllegalStateException.class);
  }
}
