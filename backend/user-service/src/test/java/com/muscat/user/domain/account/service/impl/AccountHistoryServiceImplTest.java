package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.exception.ServiceException;
import com.muscat.user.common.enums.responses.AccountHistoryResponse;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.exceptions.AccountHistoryException;
import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.entity.User;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountHistoryService 테스트")
class AccountHistoryServiceImplTest {

  @Mock
  private AccountHistoryRepository accountHistoryRepository;

  @Mock
  private AccountRepository accountRepository;

  @InjectMocks
  private AccountHistoryServiceImpl accountHistoryService;

  private Long accountId;
  private Long userId;
  private Long historyId;
  private User testUser;
  private Account testAccount;
  private AccountHistory testHistory;

  @BeforeEach
  void setUp() {
    accountId = 100L;
    userId = 1L;
    historyId = 200L;

    testUser = User.builder()
      .id(userId)
      .email("test@example.com")
      .nickname("TestUser")
      .build();

    testAccount = Account.builder()
      .id(accountId)
      .user(testUser)
      .accountNumber("ACC1234567890")
      .accountName("테스트계좌")
      .balanceKrw(new BigDecimal("1000000"))
      .balanceUsd(new BigDecimal("500.00"))
      .build();

    testHistory = AccountHistory.builder()
      .id(historyId)
      .account(testAccount)
      .transactionType(TransactionType.DEPOSIT)
      .amount(new BigDecimal("100000"))
      .currency("KRW")
      .balanceAfter(new BigDecimal("1100000"))
      .description("KRW 입금")
      .referenceId("DEP_123456")
      .build();
  }

  @Nested
  @DisplayName("입금 거래 내역 생성 테스트")
  class CreateDepositHistoryTests {

    @Test
    @DisplayName("정상적으로 입금 거래 내역이 생성된다")
    void createDepositHistory_Success() {
      // given
      BigDecimal amount = new BigDecimal("500000");
      String currency = "KRW";
      String description = "테스트 입금";
      String referenceId = "DEP_TEST_001";

      given(accountRepository.findById(accountId)).willReturn(Optional.of(testAccount));
      given(accountHistoryRepository.existsByReferenceId(referenceId)).willReturn(false);
      given(accountHistoryRepository.save(any(AccountHistory.class))).willReturn(testHistory);

      // when
      HistoryResponseDto result = accountHistoryService.createDepositHistory(
        accountId, amount, currency, description, referenceId);

      // then
      assertThat(result).isNotNull();
      verify(accountHistoryRepository).save(any(AccountHistory.class));

      ArgumentCaptor<AccountHistory> historyCaptor = ArgumentCaptor.forClass(AccountHistory.class);
      verify(accountHistoryRepository).save(historyCaptor.capture());
      AccountHistory savedHistory = historyCaptor.getValue();

      assertThat(savedHistory.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
      assertThat(savedHistory.getAmount()).isEqualByComparingTo(amount);
      assertThat(savedHistory.getCurrency()).isEqualTo(currency);
      assertThat(savedHistory.getReferenceId()).isEqualTo(referenceId);
    }

    @Test
    @DisplayName("중복된 referenceId이면 예외가 발생한다")
    void createDepositHistory_DuplicateReferenceId_ThrowsException() {
      // given
      String duplicateReferenceId = "DEP_DUPLICATE";

      given(accountRepository.findById(accountId)).willReturn(Optional.of(testAccount));
      given(accountHistoryRepository.existsByReferenceId(duplicateReferenceId)).willReturn(true);

      // when & then
      assertThatThrownBy(() ->
        accountHistoryService.createDepositHistory(
          accountId, new BigDecimal("100000"), "KRW", "테스트", duplicateReferenceId)
      ).isInstanceOf(AccountHistoryException.class)
        .hasMessage(AccountHistoryResponse.DUPLICATE_TRANSACTION.getMessage());

      verify(accountHistoryRepository, never()).save(any(AccountHistory.class));
    }

    @Test
    @DisplayName("음수 금액이면 예외가 발생한다")
    void createDepositHistory_NegativeAmount_ThrowsException() {
      // given
      BigDecimal negativeAmount = new BigDecimal("-100000");

      // when & then
      assertThatThrownBy(() ->
        accountHistoryService.createDepositHistory(
          accountId, negativeAmount, "KRW", "테스트", "REF_001")
      ).isInstanceOf(ServiceException.class);

      verify(accountHistoryRepository, never()).save(any(AccountHistory.class));
    }

    @Test
    @DisplayName("잘못된 통화 코드이면 예외가 발생한다")
    void createDepositHistory_InvalidCurrency_ThrowsException() {
      // given
      String invalidCurrency = "EUR";

      // when & then
      assertThatThrownBy(() ->
        accountHistoryService.createDepositHistory(
          accountId, new BigDecimal("100000"), invalidCurrency, "테스트", "REF_001")
      ).isInstanceOf(AccountHistoryException.class)
        .hasMessage(AccountHistoryResponse.INVALID_CURRENCY.getMessage());

      verify(accountHistoryRepository, never()).save(any(AccountHistory.class));
    }

    @Test
    @DisplayName("계좌가 존재하지 않으면 예외가 발생한다")
    void createDepositHistory_AccountNotFound_ThrowsException() {
      // given
      given(accountRepository.findById(accountId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() ->
        accountHistoryService.createDepositHistory(
          accountId, new BigDecimal("100000"), "KRW", "테스트", "REF_001")
      ).isInstanceOf(AccountException.class)
        .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());
    }
  }

  @Nested
  @DisplayName("환전 거래 내역 생성 테스트")
  class CreateExchangeHistoryTests {

    @Test
    @DisplayName("정상적으로 환전 거래 내역이 생성된다")
    void createExchangeHistory_Success() {
      // given
      String fromCurrency = "KRW";
      String toCurrency = "USD";
      BigDecimal originalAmount = new BigDecimal("1000000");
      BigDecimal exchangedAmount = new BigDecimal("769.23");
      BigDecimal exchangeRate = new BigDecimal("1300.00");
      String description = "KRW → USD 환전";
      String referenceId = "EX_TEST_001";

      AccountHistory exchangeHistory = AccountHistory.builder()
        .id(historyId)
        .account(testAccount)
        .transactionType(TransactionType.EXCHANGE)
        .amount(exchangedAmount)
        .currency(toCurrency)
        .balanceAfter(new BigDecimal("1269.23"))
        .fromCurrency(fromCurrency)
        .toCurrency(toCurrency)
        .originalAmount(originalAmount)
        .exchangeRate(exchangeRate)
        .description(description)
        .referenceId(referenceId)
        .build();

      given(accountRepository.findById(accountId)).willReturn(Optional.of(testAccount));
      given(accountHistoryRepository.existsByReferenceId(referenceId)).willReturn(false);
      given(accountHistoryRepository.save(any(AccountHistory.class))).willReturn(exchangeHistory);

      // when
      HistoryResponseDto result = accountHistoryService.createExchangeHistory(
        accountId, fromCurrency, toCurrency, originalAmount,
        exchangedAmount, exchangeRate, description, referenceId);

      // then
      assertThat(result).isNotNull();
      verify(accountHistoryRepository).save(any(AccountHistory.class));
    }

    @Test
    @DisplayName("동일한 통화로 환전 시 예외가 발생한다")
    void createExchangeHistory_SameCurrency_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        accountHistoryService.createExchangeHistory(
          accountId, "KRW", "KRW",
          new BigDecimal("1000000"), new BigDecimal("1000000"),
          BigDecimal.ONE, "테스트", "REF_001")
      ).isInstanceOf(AccountHistoryException.class)
        .hasMessage(AccountHistoryResponse.SAME_CURRENCY_EXCHANGE.getMessage());
    }

    @Test
    @DisplayName("환율이 0 이하이면 예외가 발생한다")
    void createExchangeHistory_InvalidExchangeRate_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        accountHistoryService.createExchangeHistory(
          accountId, "KRW", "USD",
          new BigDecimal("1000000"), new BigDecimal("769.23"),
          BigDecimal.ZERO, "테스트", "REF_001")
      ).isInstanceOf(AccountHistoryException.class)
        .hasMessage(AccountHistoryResponse.INVALID_EXCHANGE_RATE.getMessage());
    }

    @Test
    @DisplayName("null 환율이면 예외가 발생한다")
    void createExchangeHistory_NullExchangeRate_ThrowsException() {
      // when & then
      assertThatThrownBy(() ->
        accountHistoryService.createExchangeHistory(
          accountId, "KRW", "USD",
          new BigDecimal("1000000"), new BigDecimal("769.23"),
          null, "테스트", "REF_001")
      ).isInstanceOf(AccountHistoryException.class)
        .hasMessage(AccountHistoryResponse.INVALID_EXCHANGE_RATE.getMessage());
    }
  }
}
