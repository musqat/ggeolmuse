package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.exception.ServiceException;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import com.muscat.user.infra.kafka.DepositWithdrawalEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AccountService 통합 테스트")
class AccountServiceIntegrationTest {

  @Autowired
  private AccountServiceImpl accountService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private AccountHistoryRepository accountHistoryRepository;

  @Autowired
  private UserRepository userRepository;

  @MockBean
  private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

  @MockBean
  private AccountEventProducer accountEventProducer;

  @MockBean
  private DepositWithdrawalEventProducer depositWithdrawalEventProducer;

  @Value("${app.account.initial-krw-amount:1000000}")
  private BigDecimal initialKrwAmount;

  private static final BigDecimal TEST_EXCHANGE_RATE = new BigDecimal("1300.00");
  private static final String TEST_USER_EMAIL = "test@example.com";
  private static final String TEST_ACCOUNT_NAME = "테스트 계좌";

  private User testUser;
  private Long testUserId;

  @BeforeEach
  void setUp() {
    // 테스트 사용자 생성
    testUser = User.builder()
      .email(TEST_USER_EMAIL)
      .passwordHash("encodedPassword")
      .nickname("테스트 사용자")
      .keycloakId("keycloak-test-user-id")
      .build();
    testUser = userRepository.save(testUser);
    testUserId = testUser.getId();

    // MarketDataServiceClientWrapper Mock 설정
    FxRateDto mockFxRate = new FxRateDto(
      LocalDate.now(),
      TEST_EXCHANGE_RATE,
      "TEST_SOURCE"
    );
    given(marketDataServiceClientWrapper.getLatestFxRate()).willReturn(mockFxRate);
    given(marketDataServiceClientWrapper.getFxRate(any(String.class))).willReturn(mockFxRate);
  }

  @AfterEach
  void tearDown() {
    accountHistoryRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Nested
  @DisplayName("계좌 생성 통합 테스트")
  class CreateAccountIntegrationTests {

    @Test
    @DisplayName("계좌 생성 시 초기 KRW 잔액과 함께 DB에 저장된다")
    void createAccount_SavesAccountWithInitialBalance() {
      // given
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(new BigDecimal("0.001"));

      // when
      Account createdAccount = accountService.createAccount(testUserId, request);

      // then
      assertThat(createdAccount).isNotNull();
      assertThat(createdAccount.getId()).isNotNull();
      assertThat(createdAccount.getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
      assertThat(createdAccount.getBalanceKrw()).isEqualByComparingTo(initialKrwAmount);
      assertThat(createdAccount.getBalanceUsd()).isEqualByComparingTo(BigDecimal.ZERO);

      // DB에 저장되었는지 확인
      Optional<Account> savedAccount = accountRepository.findById(createdAccount.getId());
      assertThat(savedAccount).isPresent();
      assertThat(savedAccount.get().getAccountNumber()).isNotNull();
      assertThat(savedAccount.get().getAccountNumber()).startsWith("ACC");
    }

    @Test
    @DisplayName("중복된 계좌명 생성 시 예외 발생")
    void createAccount_DuplicateName_ThrowsException() {
      // given - 첫 번째 계좌 생성
      CreateAccountRequestDto request1 = new CreateAccountRequestDto();
      request1.setAccountName(TEST_ACCOUNT_NAME);
      request1.setCommissionRate(new BigDecimal("0.001"));
      accountService.createAccount(testUserId, request1);

      // when & then - 동일 이름으로 두 번째 계좌 생성 시도
      CreateAccountRequestDto request2 = new CreateAccountRequestDto();
      request2.setAccountName(TEST_ACCOUNT_NAME);
      request2.setCommissionRate(new BigDecimal("0.001"));

      assertThatThrownBy(() -> accountService.createAccount(testUserId, request2))
        .isInstanceOf(AccountException.class);
    }
  }

  @Nested
  @DisplayName("환전 거래 통합 테스트")
  class ExchangeIntegrationTests {

    private Account testAccount;

    @BeforeEach
    void setUpAccount() {
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(new BigDecimal("0.001"));
      testAccount = accountService.createAccount(testUserId, request);
    }

    @Test
    @DisplayName("KRW → USD 환전 시 잔액이 업데이트되고 거래내역이 생성된다")
    void exchangeKrwToUsd_UpdatesBalanceAndCreatesHistory() {
      // given
      BigDecimal krwAmount = new BigDecimal("100000");
      long historiesCountBefore = accountHistoryRepository.count();

      // when
      accountService.exchangeKrwToUsd(
        testAccount.getId(), testUserId, krwAmount, TEST_EXCHANGE_RATE);

      // then - DB에서 계좌 재조회
      Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();

      // KRW 잔액이 감소했는지 확인 (수수료 포함)
      assertThat(updatedAccount.getBalanceKrw()).isLessThan(initialKrwAmount);

      // USD 잔액이 증가했는지 확인
      assertThat(updatedAccount.getBalanceUsd()).isGreaterThan(BigDecimal.ZERO);

      // 평균 환율이 설정되었는지 확인
      assertThat(updatedAccount.getAvgExchangeRate()).isGreaterThan(BigDecimal.ZERO);

      // 거래내역이 생성되었는지 확인
      assertThat(accountHistoryRepository.count()).isGreaterThan(historiesCountBefore);
    }

    @Test
    @DisplayName("USD → KRW 환전 시 잔액이 업데이트된다")
    void exchangeUsdToKrw_UpdatesBalance() {
      // given - 먼저 USD 잔액 확보
      BigDecimal krwAmount = new BigDecimal("100000");
      accountService.exchangeKrwToUsd(
        testAccount.getId(), testUserId, krwAmount, TEST_EXCHANGE_RATE);

      Account accountAfterFirstExchange = accountRepository.findById(testAccount.getId())
        .orElseThrow();
      BigDecimal usdBalanceAfterBuy = accountAfterFirstExchange.getBalanceUsd();
      BigDecimal krwBalanceBeforeSell = accountAfterFirstExchange.getBalanceKrw();

      // when - USD → KRW 환전
      BigDecimal usdAmountToSell = new BigDecimal("50.00");
      accountService.exchangeUsdToKrw(
        testAccount.getId(), testUserId, usdAmountToSell, TEST_EXCHANGE_RATE);

      // then
      Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();

      // USD 잔액이 감소했는지 확인
      assertThat(updatedAccount.getBalanceUsd()).isLessThan(usdBalanceAfterBuy);

      // KRW 잔액이 증가했는지 확인
      assertThat(updatedAccount.getBalanceKrw()).isGreaterThan(krwBalanceBeforeSell);
    }

    @Test
    @DisplayName("잔액 부족 시 환전 실패하고 rollback된다")
    void exchangeKrwToUsd_InsufficientBalance_ThrowsExceptionAndRollback() {
      // given
      BigDecimal originalKrwBalance = testAccount.getBalanceKrw();
      BigDecimal originalUsdBalance = testAccount.getBalanceUsd();
      BigDecimal excessiveAmount = initialKrwAmount.add(new BigDecimal("1000000"));

      // when & then
      assertThatThrownBy(() ->
        accountService.exchangeKrwToUsd(
          testAccount.getId(), testUserId, excessiveAmount, TEST_EXCHANGE_RATE))
        .isInstanceOf(ServiceException.class);

      // 잔액이 변경되지 않았는지 확인 (rollback)
      Account unchangedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
      assertThat(unchangedAccount.getBalanceKrw()).isEqualByComparingTo(originalKrwBalance);
      assertThat(unchangedAccount.getBalanceUsd()).isEqualByComparingTo(originalUsdBalance);
    }
  }

  @Nested
  @DisplayName("입금 통합 테스트")
  class DepositIntegrationTests {

    private Account testAccount;

    @BeforeEach
    void setUpAccount() {
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(new BigDecimal("0.001"));
      testAccount = accountService.createAccount(testUserId, request);
    }

    @Test
    @DisplayName("KRW 입금 시 잔액이 증가하고 거래내역이 생성된다")
    void depositKrw_IncreasesBalanceAndCreatesHistory() {
      // given
      BigDecimal depositAmount = new BigDecimal("500000");
      BigDecimal expectedBalance = initialKrwAmount.add(depositAmount);
      long historiesCountBefore = accountHistoryRepository.count();

      // when
      accountService.depositKrw(testAccount.getId(), testUserId, depositAmount);

      // then
      Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
      assertThat(updatedAccount.getBalanceKrw()).isEqualByComparingTo(expectedBalance);

      // 거래내역 확인
      assertThat(accountHistoryRepository.count()).isGreaterThan(historiesCountBefore);
    }
  }

  @Nested
  @DisplayName("USD 잔액 업데이트 통합 테스트")
  class UpdateUsdBalanceIntegrationTests {

    private Account testAccount;

    @BeforeEach
    void setUpAccount() {
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(new BigDecimal("0.001"));
      testAccount = accountService.createAccount(testUserId, request);

      // USD 잔액 확보
      accountService.exchangeKrwToUsd(
        testAccount.getId(), testUserId, new BigDecimal("100000"), TEST_EXCHANGE_RATE);
    }

    @Test
    @DisplayName("USD 잔액 증가 업데이트가 DB에 반영된다")
    void updateUsdBalance_Increase_ReflectedInDb() {
      // given
      Account beforeUpdate = accountRepository.findById(testAccount.getId()).orElseThrow();
      BigDecimal originalBalance = beforeUpdate.getBalanceUsd();
      BigDecimal increaseAmount = new BigDecimal("50.00");

      // when
      accountService.updateUsdBalance(
        testAccount.getId(), testUserId, increaseAmount, "주식 매도: AAPL");

      // then
      Account afterUpdate = accountRepository.findById(testAccount.getId()).orElseThrow();
      BigDecimal expectedBalance = originalBalance.add(increaseAmount);
      assertThat(afterUpdate.getBalanceUsd()).isEqualByComparingTo(expectedBalance);

      // 거래내역 확인
      List<AccountHistory> histories = accountHistoryRepository.findAll();
      assertThat(histories).isNotEmpty();
      assertThat(histories).anyMatch(h -> h.getDescription().contains("주식 매도"));
    }

    @Test
    @DisplayName("USD 잔액 감소 업데이트가 DB에 반영된다")
    void updateUsdBalance_Decrease_ReflectedInDb() {
      // given
      Account beforeUpdate = accountRepository.findById(testAccount.getId()).orElseThrow();
      BigDecimal originalBalance = beforeUpdate.getBalanceUsd();
      BigDecimal decreaseAmount = new BigDecimal("-30.00");

      // when
      accountService.updateUsdBalance(
        testAccount.getId(), testUserId, decreaseAmount, "주식 매수: TSLA");

      // then
      Account afterUpdate = accountRepository.findById(testAccount.getId()).orElseThrow();
      BigDecimal expectedBalance = originalBalance.add(decreaseAmount);
      assertThat(afterUpdate.getBalanceUsd()).isEqualByComparingTo(expectedBalance);
    }
  }

  @Nested
  @DisplayName("계좌 삭제 통합 테스트")
  class DeleteAccountIntegrationTests {

    @Test
    @DisplayName("계좌 삭제 시 거래내역과 함께 DB에서 제거된다")
    void deleteAccount_RemovesAccountAndHistories() {
      // given
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(new BigDecimal("0.001"));
      Account testAccount = accountService.createAccount(testUserId, request);

      // 거래내역 생성
      accountService.depositKrw(testAccount.getId(), testUserId, new BigDecimal("100000"));

      Long accountId = testAccount.getId();
      assertThat(accountRepository.findById(accountId)).isPresent();

      // when
      accountService.deleteAccount(accountId, testUserId);

      // then
      assertThat(accountRepository.findById(accountId)).isEmpty();

      // 거래내역도 삭제되었는지 확인
      List<AccountHistory> histories = accountHistoryRepository.findAll();
      assertThat(histories).noneMatch(h -> h.getAccount().getId().equals(accountId));
    }
  }

  @Nested
  @DisplayName("잔액 조회 통합 테스트")
  class GetBalanceIntegrationTests {

    @Test
    @DisplayName("계좌 잔액 조회 시 현재 환율 기준으로 총 자산이 계산된다")
    void getAccountBalance_ReturnsBalanceWithTotalAssets() {
      // given
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(new BigDecimal("0.001"));
      Account testAccount = accountService.createAccount(testUserId, request);

      // KRW → USD 환전
      accountService.exchangeKrwToUsd(
        testAccount.getId(), testUserId, new BigDecimal("100000"), TEST_EXCHANGE_RATE);

      // when
      BalanceResponseDto balance = accountService.getAccountBalance(
        testAccount.getId(), testUserId);

      // then
      assertThat(balance).isNotNull();
      assertThat(balance.balanceKrw()).isNotNull();
      assertThat(balance.balanceUsd()).isNotNull();
      assertThat(balance.currentValueKrw()).isNotNull();
      assertThat(balance.currentExchangeRate()).isEqualByComparingTo(TEST_EXCHANGE_RATE);
    }
  }

  @Nested
  @DisplayName("전체 플로우 통합 테스트")
  class EndToEndFlowTests {

    @Test
    @DisplayName("계좌 생성 → 입금 → 환전 → 거래 → 조회 전체 플로우가 정상 동작한다")
    void createDepositExchangeTradeQuery_EndToEndFlow() {
      // 1. 계좌 생성
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(new BigDecimal("0.001"));
      Account account = accountService.createAccount(testUserId, request);
      assertThat(account.getId()).isNotNull();

      // 2. KRW 입금
      accountService.depositKrw(account.getId(), testUserId, new BigDecimal("500000"));
      Account afterDeposit = accountRepository.findById(account.getId()).orElseThrow();
      assertThat(afterDeposit.getBalanceKrw()).isEqualByComparingTo(
        initialKrwAmount.add(new BigDecimal("500000")));

      // 3. KRW → USD 환전
      accountService.exchangeKrwToUsd(
        account.getId(), testUserId, new BigDecimal("200000"), TEST_EXCHANGE_RATE);
      Account afterExchange = accountRepository.findById(account.getId()).orElseThrow();
      assertThat(afterExchange.getBalanceUsd()).isGreaterThan(BigDecimal.ZERO);

      // 4. USD 잔액 업데이트 (주식 거래 시뮬레이션)
      accountService.updateUsdBalance(
        account.getId(), testUserId, new BigDecimal("-50.00"), "주식 매수: AAPL");
      Account afterTrade = accountRepository.findById(account.getId()).orElseThrow();

      // 5. 최종 잔액 조회
      BalanceResponseDto finalBalance = accountService.getAccountBalance(
        account.getId(), testUserId);
      assertThat(finalBalance).isNotNull();
      assertThat(finalBalance.currentValueKrw()).isNotNull();

      // 전체 거래내역 확인
      List<AccountHistory> allHistories = accountHistoryRepository.findAll();
      assertThat(allHistories).hasSizeGreaterThanOrEqualTo(3); // 입금 + 환전 + 거래
    }
  }
}
