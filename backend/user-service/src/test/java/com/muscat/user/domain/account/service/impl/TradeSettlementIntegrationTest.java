package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.type.SettlementStatus;
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
import com.muscat.user.infra.kafka.DepositWithdrawalEventProducer;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

// 반영 트랜잭션이 rollback-only 로 끝난 뒤 새 트랜잭션에서 REJECTED 가 커밋되는지 실제 트랜잭션으로 본다
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

  @MockBean
  private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

  @MockBean
  private AccountEventProducer accountEventProducer;

  @MockBean
  private DepositWithdrawalEventProducer depositWithdrawalEventProducer;

  @MockBean
  private TradeRejectedEventProducer tradeRejectedEventProducer;

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
  }

  @Test
  @DisplayName("같은 매도가 두 번 와도 잔액은 한 번만 늘어난다")
  void duplicateSell_AppliedOnce() {
    TradeCompletedEvent sell = trade(102L, "SELL", "50.00");

    tradeSettlementService.settle(sell);
    tradeSettlementService.settle(sell);

    assertThat(usdBalance()).isEqualByComparingTo("50.00");
    assertThat(tradeSettlementRepository.findById(102L)).get()
      .extracting(TradeSettlement::getStatus).isEqualTo(SettlementStatus.APPLIED);
  }
}
