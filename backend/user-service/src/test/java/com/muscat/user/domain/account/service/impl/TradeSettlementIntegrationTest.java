package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.SettlementStatus;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.TradeSettlement;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.TradeSettlementRepository;
import com.muscat.user.domain.account.service.TradeSettlementService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import com.muscat.user.infra.kafka.TradeSettledEventProducer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 반영 트랜잭션이 rollback-only 로 끝난 뒤 새 트랜잭션의 REJECTED 커밋(실제 트랜잭션)
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("체결 정산 통합 테스트")
class TradeSettlementIntegrationTest {

  @Autowired
  private TradeSettlementService tradeSettlementService;

  @Autowired
  private AccountServiceImpl accountService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private AccountHistoryRepository accountHistoryRepository;

  @Autowired
  private TradeSettlementRepository tradeSettlementRepository;

  @Autowired
  private UserRepository userRepository;

  @MockitoBean
  private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

  @MockitoBean
  private AccountEventProducer accountEventProducer;

  @MockitoBean
  private TradeRejectedEventProducer tradeRejectedEventProducer;

  @MockitoBean
  private TradeSettledEventProducer tradeSettledEventProducer;

  private Long userId;
  private Account account;

  @BeforeEach
  void setUp() {
    User user = userRepository.save(User.builder()
      .email("settle@example.com")
      .passwordHash("encodedPassword")
      .nickname("정산 사용자")
      .keycloakId("keycloak-settle-user")
      .build());
    userId = user.getId();

    CreateAccountRequestDto request = new CreateAccountRequestDto();
    request.setAccountName("정산 계좌");
    request.setCommissionRate(new BigDecimal("0.001"));
    account = accountService.createAccount(userId, request);
  }

  @AfterEach
  void tearDown() {
    tradeSettlementRepository.deleteAll();
    accountHistoryRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  private TradeCompletedEvent trade(long tradeId, String tradeType, String amount) {
    return TradeCompletedEvent.builder()
      .eventId("evt-" + tradeId)
      .tradeId(tradeId)
      .userId(String.valueOf(userId))
      .accountId(account.getId())
      .symbol("AAPL")
      .tradeType(tradeType)
      .quantity(BigDecimal.ONE)
      .price(new BigDecimal(amount))
      .totalAmount(new BigDecimal(amount))
      .build();
  }

  private BigDecimal usdBalance() {
    return accountRepository.findById(account.getId()).orElseThrow().getBalanceUsd();
  }

  @Test
  @DisplayName("잔액이 모자란 매수는 잔액을 그대로 두고 REJECTED 를 커밋한 뒤 반영 실패를 보낸다")
  void insufficientBuy_CommitsRejected() {
    tradeSettlementService.settle(trade(101L, "BUY", "100.00"));

    assertThat(tradeSettlementRepository.findById(101L)).get()
      .extracting(TradeSettlement::getStatus).isEqualTo(SettlementStatus.REJECTED);
    assertThat(usdBalance()).isEqualByComparingTo("0");
    verify(tradeRejectedEventProducer).publish(any(), eq("INSUFFICIENT_USD_BALANCE"), any());
    verify(tradeSettledEventProducer, never()).publish(any());
  }

  @Test
  @DisplayName("반영한 매도는 APPLIED 를 커밋하고 정산됨을 한 번 보낸다")
  void appliedSell_PublishesSettled() {
    TradeCompletedEvent sell = trade(103L, "SELL", "50.00");

    tradeSettlementService.settle(sell);

    assertThat(tradeSettlementRepository.findById(103L)).get()
      .extracting(TradeSettlement::getStatus).isEqualTo(SettlementStatus.APPLIED);
    verify(tradeSettledEventProducer).publish(sell);
  }

  @Test
  @DisplayName("정산됨을 못 보내도 반영과 APPLIED 는 커밋되고, 다시 받으면 잔액은 그대로 두고 정산됨만 보낸다")
  void settledPublishFails_KeepsAppliedAndResends() {
    TradeCompletedEvent sell = trade(104L, "SELL", "50.00");
    willThrow(new UserException(UserResponse.EVENT_PUBLISH_FAILED)).willDoNothing()
      .given(tradeSettledEventProducer).publish(any());

    assertThatThrownBy(() -> tradeSettlementService.settle(sell)).isInstanceOf(UserException.class);

    assertThat(tradeSettlementRepository.findById(104L)).get()
      .extracting(TradeSettlement::getStatus).isEqualTo(SettlementStatus.APPLIED);
    assertThat(usdBalance()).isEqualByComparingTo("50.00");

    tradeSettlementService.settle(sell);

    assertThat(usdBalance()).isEqualByComparingTo("50.00");
    verify(tradeSettledEventProducer, times(2)).publish(sell);
    verify(tradeRejectedEventProducer, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("반영 실패를 못 보내도 REJECTED 는 커밋되고, 다시 받으면 같은 사유로 반영 실패만 보낸다")
  void rejectedPublishFails_KeepsRejectedAndResends() {
    TradeCompletedEvent buy = trade(105L, "BUY", "100.00");
    willThrow(new UserException(UserResponse.EVENT_PUBLISH_FAILED)).willDoNothing()
      .given(tradeRejectedEventProducer).publish(any(), any(), any());

    assertThatThrownBy(() -> tradeSettlementService.settle(buy)).isInstanceOf(UserException.class);

    assertThat(tradeSettlementRepository.findById(105L)).get()
      .extracting(TradeSettlement::getStatus).isEqualTo(SettlementStatus.REJECTED);

    tradeSettlementService.settle(buy);

    assertThat(usdBalance()).isEqualByComparingTo("0");
    verify(tradeRejectedEventProducer, times(2)).publish(eq(buy), eq("INSUFFICIENT_USD_BALANCE"), any());
    verify(tradeSettledEventProducer, never()).publish(any());
  }

  @Test
  @DisplayName("같은 매도가 두 번 와도 잔액은 한 번만 늘고, 정산됨은 다시 보낸다")
  void duplicateSell_AppliedOnce() {
    TradeCompletedEvent sell = trade(102L, "SELL", "50.00");

    tradeSettlementService.settle(sell);
    tradeSettlementService.settle(sell);

    assertThat(usdBalance()).isEqualByComparingTo("50.00");
    assertThat(tradeSettlementRepository.findById(102L)).get()
      .extracting(TradeSettlement::getStatus).isEqualTo(SettlementStatus.APPLIED);
    verify(tradeSettledEventProducer, times(2)).publish(sell);
  }
}
