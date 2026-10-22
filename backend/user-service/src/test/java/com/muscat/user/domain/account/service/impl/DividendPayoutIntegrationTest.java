package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.entity.DividendSettlement;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.DividendSettlementRepository;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 같은 배당락일의 목표 금액이 여러 번 · 다른 값으로 와도 넣은 금액이 목표와 같아지는지 실제 DB 로 봄
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("배당 목표 금액 반영 통합 테스트")
class DividendPayoutIntegrationTest {

  private static final LocalDate EX_DATE = LocalDate.of(2024, 2, 9);

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

  @MockitoBean
  private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

  @MockitoBean
  private AccountEventProducer accountEventProducer;

  @MockitoBean
  private TradeRejectedEventProducer tradeRejectedEventProducer;

  private Long userId;
  private Account account;

  @BeforeEach
  void setUp() {
    User user = userRepository.save(User.builder()
      .email("payout@example.com")
      .passwordHash("encodedPassword")
      .nickname("배당 몫 사용자")
      .keycloakId("keycloak-payout-user")
      .build());
    userId = user.getId();

    CreateAccountRequestDto request = new CreateAccountRequestDto();
    request.setAccountName("배당 몫 계좌");
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

  private DividendPayoutEvent payout(String netAmount) {
    return DividendPayoutEvent.builder()
      .userId(String.valueOf(userId))
      .accountId(account.getId())
      .symbol("AAPL")
      .exDate(EX_DATE.toString())
      .dividendPerShare(new BigDecimal("0.24"))
      .quantity(new BigDecimal("10"))
      .grossAmount(new BigDecimal("2.40"))
      .taxAmount(new BigDecimal("0.37"))
      .netAmount(new BigDecimal(netAmount))
      .currency("USD")
      .build();
  }

  private void changeUsd(String amount) {
    Account saved = accountRepository.findById(account.getId()).orElseThrow();
    saved.adjustUsdBalance(new BigDecimal(amount));
    accountRepository.save(saved);
  }

  private BigDecimal usdBalance() {
    return accountRepository.findById(account.getId()).orElseThrow().getBalanceUsd();
  }

  private Optional<DividendSettlement> settlement() {
    return dividendSettlementRepository.findAll().stream()
      .filter(s -> s.getAccountId().equals(account.getId()) && s.getExDate().equals(EX_DATE))
      .findFirst();
  }

  private List<AccountHistory> histories(TransactionType type) {
    return accountHistoryRepository.findAll().stream()
      .filter(h -> h.getTransactionType() == type)
      .toList();
  }

  @Test
  @DisplayName("첫 목표 금액이면 그만큼 넣고 정산 행을 만든다")
  void firstTarget_Credited() {
    changeUsd("100.00");

    accountService.applyDividendPayout(payout("2.03"));

    assertThat(usdBalance()).isEqualByComparingTo("102.03");
    assertThat(settlement()).get().extracting(DividendSettlement::getAmount)
      .satisfies(amount -> assertThat(amount).isEqualByComparingTo("2.03"));
    assertThat(histories(TransactionType.DIVIDEND)).hasSize(1);
  }

  @Test
  @DisplayName("같은 목표 금액이 다시 오면 잔액을 바꾸지 않는다")
  void sameTargetTwice_CreditedOnce() {
    changeUsd("100.00");

    accountService.applyDividendPayout(payout("2.03"));
    accountService.applyDividendPayout(payout("2.03"));

    assertThat(usdBalance()).isEqualByComparingTo("102.03");
    assertThat(histories(TransactionType.DIVIDEND)).hasSize(1);
  }

  @Test
  @DisplayName("목표가 줄면 차이만큼 빼고 배당 반환으로 남긴다")
  void lowerTarget_ReversesDifference() {
    changeUsd("100.00");
    accountService.applyDividendPayout(payout("2.03"));

    accountService.applyDividendPayout(payout("0.00"));

    assertThat(usdBalance()).isEqualByComparingTo("100.00");
    assertThat(settlement()).get().extracting(DividendSettlement::getAmount)
      .satisfies(amount -> assertThat(amount).isEqualByComparingTo("0.00"));
    assertThat(histories(TransactionType.DIVIDEND_REVERSAL)).singleElement().satisfies(history -> {
      assertThat(history.getAmount()).isEqualByComparingTo("2.03");
      assertThat(history.getBalanceAfter()).isEqualByComparingTo("100.00");
    });
  }

  @Test
  @DisplayName("배당 반환은 잔액이 모자라도 마이너스로 뺀다")
  void reversalBeyondBalance_GoesNegative() {
    changeUsd("1.00");
    accountService.applyDividendPayout(payout("2.03"));
    changeUsd("-2.00");

    accountService.applyDividendPayout(payout("0.00"));

    assertThat(usdBalance()).isEqualByComparingTo("-1.00");
  }

  @Test
  @DisplayName("목표가 0 이고 넣은 적이 없으면 아무것도 안 한다")
  void zeroTargetWithoutCredit_NoOp() {
    changeUsd("100.00");

    accountService.applyDividendPayout(payout("0.00"));

    assertThat(usdBalance()).isEqualByComparingTo("100.00");
    assertThat(settlement()).isEmpty();
    assertThat(histories(TransactionType.DIVIDEND)).isEmpty();
    assertThat(histories(TransactionType.DIVIDEND_REVERSAL)).isEmpty();
  }

  @Test
  @DisplayName("배당 정산은 계좌 · 종목 · 배당락일 순으로 쪽을 나눈다")
  void dividendSettlements_PagedInKeyOrder() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 9, 0);
    dividendSettlementRepository.save(DividendSettlement.of(account.getId(), "MSFT", EX_DATE, new BigDecimal("1.90"), now));
    dividendSettlementRepository.save(
      DividendSettlement.of(account.getId(), "AAPL", EX_DATE.plusMonths(3), new BigDecimal("2.11"), now));
    dividendSettlementRepository.save(DividendSettlement.of(account.getId(), "AAPL", EX_DATE, new BigDecimal("2.03"), now));

    Page<DividendSettlement> first = accountService.getDividendSettlements(0, 2);
    Page<DividendSettlement> second = accountService.getDividendSettlements(1, 2);

    assertThat(first.getContent()).extracting(DividendSettlement::getSymbol, DividendSettlement::getExDate)
      .containsExactly(tuple("AAPL", EX_DATE), tuple("AAPL", EX_DATE.plusMonths(3)));
    assertThat(first.isLast()).isFalse();
    assertThat(second.getContent()).extracting(DividendSettlement::getSymbol).containsExactly("MSFT");
    assertThat(second.isLast()).isTrue();
  }

  @Test
  @DisplayName("지운 계좌로 온 목표 금액은 오류 없이 건너뛴다")
  void deletedAccount_Skipped() {
    DividendPayoutEvent event = payout("2.03");
    event.setAccountId(account.getId() + 1000);

    assertThatCode(() -> accountService.applyDividendPayout(event)).doesNotThrowAnyException();
    assertThat(dividendSettlementRepository.findAll()).isEmpty();
  }
}
