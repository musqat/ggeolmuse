package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.DividendSettlement;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.DividendSettlementRepository;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import com.muscat.user.infra.kafka.DepositWithdrawalEventProducer;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

// 같은 배당 지급이 두 번 와도 잔액이 한 번만 느는지 실제 트랜잭션 · DB 제약으로 본다
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("배당 입금 통합 테스트")
class DividendSettlementIntegrationTest {

  @Autowired
  private AccountServiceImpl accountService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private AccountHistoryRepository accountHistoryRepository;

  @Autowired
  private DividendSettlementRepository dividendSettlementRepository;

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
      .email("dividend@example.com")
      .passwordHash("encodedPassword")
      .nickname("배당 사용자")
      .keycloakId("keycloak-dividend-user")
      .build());
    userId = user.getId();

    CreateAccountRequestDto request = new CreateAccountRequestDto();
    request.setAccountName("배당 계좌");
    request.setCommissionRate(new BigDecimal("0.001"));
    account = accountService.createAccount(userId, request);
  }

  @AfterEach
  void tearDown() {
    dividendSettlementRepository.deleteAll();
    accountHistoryRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  private DividendReceivedEvent dividend(String exDate, String amount) {
    return DividendReceivedEvent.builder()
      .userId(String.valueOf(userId))
      .accountId(account.getId())
      .symbol("AAPL")
      .exDate(exDate)
      .dividendPerShare(new BigDecimal("0.25"))
      .quantity(new BigDecimal("10"))
      .totalAmount(new BigDecimal(amount))
      .currency("USD")
      .build();
  }

  private BigDecimal usdBalance() {
    return accountRepository.findById(account.getId()).orElseThrow().getBalanceUsd();
  }

  @Test
  @DisplayName("같은 배당이 두 번 와도 잔액은 한 번만 늘고 입금 기록은 하나다")
  void duplicateDividend_DepositedOnce() {
    DividendReceivedEvent event = dividend("2024-11-08", "2.50");

    accountService.processDividendReceivedEvent(event);
    accountService.processDividendReceivedEvent(event);

    assertThat(usdBalance()).isEqualByComparingTo("2.50");
    assertThat(dividendSettlementRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("배당락일이 다르면 따로 입금한다")
  void differentExDate_DepositedSeparately() {
    accountService.processDividendReceivedEvent(dividend("2024-08-12", "2.50"));
    accountService.processDividendReceivedEvent(dividend("2024-11-08", "2.50"));

    assertThat(usdBalance()).isEqualByComparingTo("5.00");
    assertThat(dividendSettlementRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("같은 계좌 · 종목 · 배당락일 기록은 DB 가 두 번째를 막는다(동시에 와도 하나만 남는다)")
  void sameKey_RejectedByUniqueConstraint() {
    LocalDate exDate = LocalDate.of(2024, 11, 8);
    dividendSettlementRepository.saveAndFlush(
      DividendSettlement.of(account.getId(), "AAPL", exDate, new BigDecimal("2.50"), LocalDateTime.now()));

    assertThatThrownBy(() -> dividendSettlementRepository.saveAndFlush(
      DividendSettlement.of(account.getId(), "AAPL", exDate, new BigDecimal("2.50"), LocalDateTime.now())))
      .isInstanceOf(DataIntegrityViolationException.class);
  }
}
