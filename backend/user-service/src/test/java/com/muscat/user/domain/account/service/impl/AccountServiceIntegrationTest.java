package com.muscat.user.domain.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.exception.ServiceException;
import com.muscat.user.common.enums.response.AccountResponse;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exception.AccountException;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.request.IdempotencyKey;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.IdempotencyRecordRepository;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

  @Autowired
  private IdempotencyRecordRepository idempotencyRecordRepository;

  @MockitoBean
  private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

  @MockitoBean
  private AccountEventProducer accountEventProducer;

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
  }

  @AfterEach
  void tearDown() {
    idempotencyRecordRepository.deleteAll();
    accountHistoryRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  // 종류 · 통화가 같은 마지막 거래 내역
  private AccountHistory historyOf(TransactionType type, String currency) {
    return accountHistoryRepository.findAll().stream()
      .filter(h -> h.getTransactionType() == type && currency.equals(h.getCurrency()))
      .reduce((first, second) -> second)
      .orElseThrow();
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
      assertThat(historyOf(TransactionType.EXCHANGE, "USD").getBalanceAfter())
        .isEqualByComparingTo(updatedAccount.getBalanceUsd());
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
      assertThat(historyOf(TransactionType.EXCHANGE, "KRW").getBalanceAfter())
        .isEqualByComparingTo(updatedAccount.getBalanceKrw());
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

    @Test
    @DisplayName("환전 → 매수 → 환전 뒤 평균 환율은 환전한 원화 누계를 환전한 달러 누계로 나눈 값이다")
    void exchangeBuyExchange_AverageRateIsWeightedByExchangedUsd() {
      accountService.exchangeKrwToUsd(testAccount.getId(), testUserId, new BigDecimal("130000"), new BigDecimal("1300"));
      accountService.updateUsdBalance(testAccount.getId(), testUserId, new BigDecimal("-90.00"),
        TransactionType.TRADE_BUY, "주식 매수: AAPL");
      accountService.exchangeKrwToUsd(testAccount.getId(), testUserId, new BigDecimal("140000"), new BigDecimal("1400"));

      // (130,000 + 140,000) 원 / (100 + 100) 달러
      Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
      assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("1350");
      assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("매수로 달러를 거의 다 쓴 뒤 적은 금액을 환전해도 평균 환율은 두 환전의 달러 가중 평균이다")
    void exchangeAfterSpendingAlmostAllUsd_AverageRateIsWeightedByExchangedUsd() {
      accountService.exchangeKrwToUsd(testAccount.getId(), testUserId, new BigDecimal("650000"), TEST_EXCHANGE_RATE);
      accountService.updateUsdBalance(testAccount.getId(), testUserId, new BigDecimal("-499.99"),
        TransactionType.TRADE_BUY, "주식 매수: AAPL");
      accountService.exchangeKrwToUsd(testAccount.getId(), testUserId, new BigDecimal("1400"), new BigDecimal("1400"));

      // (650,000 + 1,400) 원 / (500 + 1) 달러
      Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
      assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("1300.199601");
      assertThat(account.getBalanceUsd()).isEqualByComparingTo("1.01");
    }

    @Test
    @DisplayName("부분 역환전 뒤 환전하면 역환전한 달러는 평균 환율에 무게를 갖지 않는다")
    void exchangeAfterPartialReverseExchange_ReversedUsdHasNoWeight() {
      accountService.exchangeKrwToUsd(testAccount.getId(), testUserId, new BigDecimal("130000"), TEST_EXCHANGE_RATE);
      accountService.exchangeUsdToKrw(testAccount.getId(), testUserId, new BigDecimal("99.99"), TEST_EXCHANGE_RATE);
      accountService.exchangeKrwToUsd(testAccount.getId(), testUserId, new BigDecimal("140000"), new BigDecimal("1400"));

      // 남은 0.01 달러 몫(13 원 / 0.01 달러)과 새 환전(140,000 원 / 100 달러)
      Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
      assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("140013");
      assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("100.01");
      assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("1399.990001");
    }

    @Test
    @DisplayName("달러 누계 없이 원화 누계만 남은 계좌도 평균 환율로 달러 누계를 맞춰 환전한다")
    void exchangeWithKrwTotalOnly_DerivesUsdTotalFromAverage() {
      Account stale = accountRepository.findById(testAccount.getId()).orElseThrow();
      stale.setBalanceUsd(new BigDecimal("1000.00"));
      stale.setTotalExchangedKrw(new BigDecimal("1300000"));
      stale.setTotalExchangedUsd(BigDecimal.ZERO);
      stale.setAvgExchangeRate(new BigDecimal("1300"));
      accountRepository.save(stale);

      accountService.exchangeKrwToUsd(testAccount.getId(), testUserId, new BigDecimal("13000"), TEST_EXCHANGE_RATE);

      // (1,300,000 + 13,000) 원 / (1,300,000 / 1,300 + 10) 달러
      Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
      assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("1010.00");
      assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("1300");
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
      assertThat(historyOf(TransactionType.DEPOSIT, "KRW").getBalanceAfter())
        .isEqualByComparingTo(expectedBalance);
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
        testAccount.getId(), testUserId, increaseAmount, TransactionType.TRADE_SELL, "주식 매도: AAPL");

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
        testAccount.getId(), testUserId, decreaseAmount, TransactionType.TRADE_BUY, "주식 매수: TSLA");

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
        account.getId(), testUserId, new BigDecimal("-50.00"), TransactionType.TRADE_BUY, "주식 매수: AAPL");
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

  @Nested
  @DisplayName("계좌 목록 조회 통합 테스트")
  class GetUserAccountsIntegrationTests {

    private CreateAccountRequestDto request(String name) {
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(name);
      request.setCommissionRate(new BigDecimal("0.001"));
      return request;
    }

    @Test
    @DisplayName("사용자의 계좌만 사용자 정보와 함께 가져온다(트랜잭션 밖에서 사용자를 읽어도 지연 로딩 오류가 없다)")
    void getUserAccounts_OnlyOwnWithUserFetched() {
      accountService.createAccount(testUserId, request("내 계좌 1"));
      accountService.createAccount(testUserId, request("내 계좌 2"));
      User other = userRepository.save(User.builder().email("other@example.com").passwordHash("x")
        .nickname("다른 사용자").keycloakId("keycloak-other").build());
      accountService.createAccount(other.getId(), request("남의 계좌"));

      List<Account> accounts = accountService.getUserAccounts(testUserId);

      assertThat(accounts).extracting(Account::getAccountName).containsExactlyInAnyOrder("내 계좌 1", "내 계좌 2");
      assertThat(accounts).allSatisfy(account -> assertThat(account.getUser().getEmail()).isEqualTo(TEST_USER_EMAIL));
    }
  }

  @Nested
  @DisplayName("환율 조회는 DB 트랜잭션 밖")
  class OutsideTransactionTests {

    // market-data 호출마다 그때 DB 트랜잭션이 열려 있었는지
    private final List<Boolean> inTransaction = new ArrayList<>();

    private Account newAccount() {
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(BigDecimal.ZERO);
      return accountService.createAccount(testUserId, request);
    }

    @Test
    @DisplayName("잔액 조회는 트랜잭션 없이 현재 환율을 받는다")
    void getAccountBalance_FetchesRateOutsideTransaction() {
      Account account = newAccount();
      willAnswer(invocation -> {
        inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
        return new FxRateDto(LocalDate.now(), TEST_EXCHANGE_RATE, "TEST_SOURCE");
      }).given(marketDataServiceClientWrapper).getLatestFxRate();

      accountService.getAccountBalance(account.getId(), testUserId);

      assertThat(inTransaction).containsExactly(false);
    }

    @Test
    @DisplayName("환전은 트랜잭션 밖에서 허용 범위를 받고 잠금 · 변경만 트랜잭션 안에서 한다")
    void exchange_FetchesRangeOutsideTransaction() {
      Account account = newAccount();
      willAnswer(invocation -> {
        inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
        return null;
      }).given(marketDataServiceClientWrapper).getFxRateRange();

      accountService.exchangeKrwToUsd(account.getId(), testUserId, new BigDecimal("100000"), TEST_EXCHANGE_RATE);
      accountService.exchangeUsdToKrw(account.getId(), testUserId, new BigDecimal("10"), TEST_EXCHANGE_RATE);

      assertThat(inTransaction).containsExactly(false, false);
      assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalanceUsd())
        .isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("현재 환율 조회는 트랜잭션을 열지 않는다")
    void getCurrentExchangeRate_OutsideTransaction() {
      willAnswer(invocation -> {
        inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
        return new FxRateDto(LocalDate.now(), TEST_EXCHANGE_RATE, "TEST_SOURCE");
      }).given(marketDataServiceClientWrapper).getLatestFxRate();

      accountService.getCurrentExchangeRate();

      assertThat(inTransaction).containsExactly(false);
    }
  }

  @Nested
  @DisplayName("같은 중복 요청 키로 다시 온 입금 · 환전")
  class IdempotencyTests {

    private final IdempotencyKey key = new IdempotencyKey("3f2b8c1e-9d4a-4e6f-8a7b-1c2d3e4f5a6b");

    private Account newAccount(Long userId) {
      CreateAccountRequestDto request = new CreateAccountRequestDto();
      request.setAccountName(TEST_ACCOUNT_NAME);
      request.setCommissionRate(BigDecimal.ZERO);
      return accountService.createAccount(userId, request);
    }

    private BigDecimal krwOf(Account account) {
      return accountRepository.findById(account.getId()).orElseThrow().getBalanceKrw();
    }

    @Test
    @DisplayName("같은 키로 두 번 입금하면 잔액과 내역은 한 번만 바뀐다")
    void deposit_SameKeyTwice_AppliedOnce() {
      Account account = newAccount(testUserId);
      long historiesBefore = accountHistoryRepository.count();

      accountService.depositKrw(account.getId(), testUserId, new BigDecimal("500000"), key);
      accountService.depositKrw(account.getId(), testUserId, new BigDecimal("500000.00"), key);

      assertThat(krwOf(account)).isEqualByComparingTo(initialKrwAmount.add(new BigDecimal("500000")));
      assertThat(accountHistoryRepository.count()).isEqualTo(historiesBefore + 1);
    }

    @Test
    @DisplayName("같은 키에 다른 금액이면 422 이고 잔액은 첫 입금 그대로다")
    void deposit_SameKeyOtherAmount_Unprocessable() {
      Account account = newAccount(testUserId);
      accountService.depositKrw(account.getId(), testUserId, new BigDecimal("500000"), key);

      assertThatThrownBy(() -> accountService.depositKrw(account.getId(), testUserId, new BigDecimal("600000"), key))
        .isInstanceOf(AccountException.class)
        .extracting("code").isEqualTo(AccountResponse.IDEMPOTENCY_KEY_REUSED);
      assertThat(krwOf(account)).isEqualByComparingTo(initialKrwAmount.add(new BigDecimal("500000")));
    }

    @Test
    @DisplayName("같은 키로 다시 온 환전은 환율 범위를 다시 받지 않고 한 번만 환전한다")
    void exchange_SameKeyTwice_AppliedOnceWithoutRemoteCall() {
      Account account = newAccount(testUserId);

      accountService.exchangeKrwToUsd(account.getId(), testUserId, new BigDecimal("100000"), TEST_EXCHANGE_RATE, key);
      BigDecimal usdAfterFirst = accountRepository.findById(account.getId()).orElseThrow().getBalanceUsd();
      accountService.exchangeKrwToUsd(account.getId(), testUserId, new BigDecimal("100000"), TEST_EXCHANGE_RATE, key);

      assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalanceUsd())
        .isEqualByComparingTo(usdAfterFirst);
      assertThat(krwOf(account)).isEqualByComparingTo(initialKrwAmount.subtract(new BigDecimal("100000")));
      verify(marketDataServiceClientWrapper, times(1)).getFxRateRange();
    }

    @Test
    @DisplayName("같은 키라도 방향이 다른 환전이면 422 다")
    void exchange_SameKeyOtherDirection_Unprocessable() {
      Account account = newAccount(testUserId);
      accountService.exchangeKrwToUsd(account.getId(), testUserId, new BigDecimal("100000"), TEST_EXCHANGE_RATE, key);

      assertThatThrownBy(() -> accountService.exchangeUsdToKrw(
        account.getId(), testUserId, new BigDecimal("100000"), TEST_EXCHANGE_RATE, key))
        .isInstanceOf(AccountException.class)
        .extracting("code").isEqualTo(AccountResponse.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    @DisplayName("잔액 부족으로 실패한 키는 남지 않아 입금 뒤 같은 키로 다시 보내면 환전된다")
    void exchange_FailedKeyRolledBack_RetryRuns() {
      Account account = newAccount(testUserId);
      BigDecimal amount = initialKrwAmount.add(new BigDecimal("1000000"));

      assertThatThrownBy(() -> accountService.exchangeKrwToUsd(
        account.getId(), testUserId, amount, TEST_EXCHANGE_RATE, key))
        .isInstanceOf(ServiceException.class);
      accountService.depositKrw(account.getId(), testUserId, new BigDecimal("1000000"));
      accountService.exchangeKrwToUsd(account.getId(), testUserId, amount, TEST_EXCHANGE_RATE, key);

      assertThat(krwOf(account)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("같은 키라도 사용자가 다르면 각자 입금된다")
    void deposit_SameKeyOtherUser_BothApplied() {
      User other = userRepository.save(User.builder()
        .email("other@example.com").passwordHash("encodedPassword").nickname("다른 사용자")
        .keycloakId("keycloak-other-user-id").build());
      Account mine = newAccount(testUserId);
      Account theirs = newAccount(other.getId());

      accountService.depositKrw(mine.getId(), testUserId, new BigDecimal("500000"), key);
      accountService.depositKrw(theirs.getId(), other.getId(), new BigDecimal("500000"), key);

      assertThat(krwOf(mine)).isEqualByComparingTo(initialKrwAmount.add(new BigDecimal("500000")));
      assertThat(krwOf(theirs)).isEqualByComparingTo(initialKrwAmount.add(new BigDecimal("500000")));
    }
  }
}
