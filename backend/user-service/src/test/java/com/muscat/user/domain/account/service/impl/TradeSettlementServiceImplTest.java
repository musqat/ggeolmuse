package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.enums.type.SettlementStatus;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.domain.account.entity.TradeSettlement;
import com.muscat.user.domain.account.repository.TradeSettlementRepository;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import com.muscat.user.infra.kafka.TradeSettledEventProducer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
  private TradeSettledEventProducer tradeSettledEventProducer;

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
      tradeRejectedEventProducer, tradeSettledEventProducer, transactionManager);
  }

  @Test
  @DisplayName("처음 온 체결은 잔액에 반영하고 APPLIED 로 남긴 뒤 정산됨을 보낸다")
  void settle_NewTrade_AppliesAndRecords() {
    service.settle(event);

    verify(accountService).processTradeEvent(event);
    ArgumentCaptor<TradeSettlement> saved = ArgumentCaptor.forClass(TradeSettlement.class);
    verify(tradeSettlementRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getTradeId()).isEqualTo(7L);
    assertThat(saved.getValue().getAccountId()).isEqualTo(20L);
    assertThat(saved.getValue().getStatus()).isEqualTo(SettlementStatus.APPLIED);
    verify(tradeSettledEventProducer).publish(event);
  }

  @Test
  @DisplayName("정산됨은 APPLIED 를 커밋한 뒤에 보낸다")
  void settle_NewTrade_PublishesAfterCommit() {
    service.settle(event);

    InOrder order = inOrder(tradeSettlementRepository, transactionManager, tradeSettledEventProducer);
    order.verify(tradeSettlementRepository).saveAndFlush(any());
    order.verify(transactionManager).commit(any());
    order.verify(tradeSettledEventProducer).publish(event);
  }

  @Test
  @DisplayName("이미 반영한 체결이 다시 오면 잔액은 그대로 두고 정산됨만 다시 보낸다")
  void settle_AlreadyApplied_ResendsSettled() {
    given(tradeSettlementRepository.findById(7L))
      .willReturn(Optional.of(TradeSettlement.applied(7L, 20L, LocalDateTime.now())));

    service.settle(event);

    verify(accountService, never()).processTradeEvent(any());
    verify(tradeSettlementRepository, never()).saveAndFlush(any());
    verify(tradeSettledEventProducer).publish(event);
    verify(tradeRejectedEventProducer, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("이미 반영 실패한 체결이 다시 오면 저장된 사유로 반영 실패만 다시 보낸다")
  void settle_AlreadyRejected_ResendsRejected() {
    given(tradeSettlementRepository.findById(7L)).willReturn(Optional.of(
      TradeSettlement.rejected(7L, 20L, "INSUFFICIENT_USD_BALANCE", LocalDateTime.now())));

    service.settle(event);

    verify(accountService, never()).processTradeEvent(any());
    verify(tradeSettlementRepository, never()).saveAndFlush(any());
    verify(tradeRejectedEventProducer)
      .publish(event, "INSUFFICIENT_USD_BALANCE", AccountResponse.INSUFFICIENT_USD_BALANCE.getMessage());
    verify(tradeSettledEventProducer, never()).publish(any());
  }

  @Test
  @DisplayName("저장된 사유 코드가 지금 사유 목록에 없으면 코드를 문구 대신 보낸다")
  void settle_AlreadyRejected_UnknownReason_SendsCode() {
    given(tradeSettlementRepository.findById(7L)).willReturn(Optional.of(
      TradeSettlement.rejected(7L, 20L, "RETIRED_REASON", LocalDateTime.now())));

    service.settle(event);

    verify(tradeRejectedEventProducer).publish(event, "RETIRED_REASON", "RETIRED_REASON");
  }

  @Test
  @DisplayName("기술 실패는 기록 · 발행 없이 예외를 그대로 던진다")
  void settle_TechnicalFailure_Rethrows() {
    willThrow(new IllegalStateException("db down")).given(accountService).processTradeEvent(event);

    assertThatThrownBy(() -> service.settle(event)).isInstanceOf(IllegalStateException.class);
    verify(tradeSettlementRepository, never()).saveAndFlush(any());
    verify(tradeRejectedEventProducer, never()).publish(any(), any(), any());
    verify(tradeSettledEventProducer, never()).publish(any());
  }

  @Test
  @DisplayName("정산됨 발행이 실패해도 APPLIED 는 커밋돼 있고, 예외를 던져 다시 받게 한다")
  void settle_PublishSettledFails_Throws() {
    willThrow(new UserException(UserResponse.EVENT_PUBLISH_FAILED))
      .given(tradeSettledEventProducer).publish(event);

    assertThatThrownBy(() -> service.settle(event))
      .isInstanceOf(UserException.class)
      .hasFieldOrPropertyWithValue("errorCode", "EVENT_PUBLISH_FAILED");
    verify(transactionManager).commit(any());
    verify(transactionManager, never()).rollback(any());
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
    verify(tradeSettledEventProducer, never()).publish(any());
  }

  @Test
  @DisplayName("반영 실패는 REJECTED 를 커밋한 뒤에 보낸다")
  void settle_InsufficientBalance_PublishesAfterCommit() {
    willThrow(new AccountException(AccountResponse.INSUFFICIENT_USD_BALANCE))
      .given(accountService).processTradeEvent(event);

    service.settle(event);

    InOrder order = inOrder(tradeSettlementRepository, transactionManager, tradeRejectedEventProducer);
    order.verify(tradeSettlementRepository).saveAndFlush(any());
    order.verify(transactionManager).commit(any());
    order.verify(tradeRejectedEventProducer).publish(any(), any(), any());
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
  @DisplayName("반영 실패 발행이 실패해도 REJECTED 는 커밋돼 있고 예외를 던진다")
  void settle_PublishFails_Throws() {
    willThrow(new AccountException(AccountResponse.INSUFFICIENT_USD_BALANCE))
      .given(accountService).processTradeEvent(event);
    willThrow(new IllegalStateException("broker down"))
      .given(tradeRejectedEventProducer).publish(any(), any(), any());

    assertThatThrownBy(() -> service.settle(event)).isInstanceOf(IllegalStateException.class);
    verify(transactionManager).commit(any());
  }
}
