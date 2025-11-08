package com.muscat.user.domain.account.service.impl;

import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.messaging.event.TradeCancelledEvent;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.enums.type.CurrencyType;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.logging.UserLogger;
import com.muscat.user.common.util.AccountCalculatorUtil;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.dto.response.ExchangeCalculationResult;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.service.AccountHistoryService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.client.dto.FxRateDto;
import com.muscat.user.infra.kafka.AccountEventProducer;
import com.muscat.user.infra.kafka.DepositWithdrawalEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AccountService 단위 테스트")
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountHistoryRepository accountHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountHistoryService accountHistoryService;

    @Mock
    private AccountCalculatorUtil accountCalculator;

    @Mock
    private UserLogger userLogger;

    @Mock
    private MarketDataServiceClientWrapper marketDataServiceClientWrapper;

    @Mock
    private AccountEventProducer accountEventProducer;

    @Mock
    private DepositWithdrawalEventProducer depositWithdrawalEventProducer;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Long userId;
    private Long accountId;
    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        userId = 1L;
        accountId = 100L;

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .nickname("testuser")
                .build();

        testAccount = Account.builder()
                .id(accountId)
                .user(testUser)
                .accountNumber("ACC1234567890")
                .accountName("테스트계좌")
                .balanceKrw(new BigDecimal("1000000"))
                .balanceUsd(BigDecimal.ZERO)
                .avgExchangeRate(BigDecimal.ZERO)
                .totalExchangedKrw(BigDecimal.ZERO)
                .commissionRate(new BigDecimal("0.001"))
                .build();

        // ReflectionTestUtils로 private 필드 설정
        ReflectionTestUtils.setField(accountService, "initialKrwAmount", new BigDecimal("1000000"));
        ReflectionTestUtils.setField(accountService, "minValidRate", new BigDecimal("1000"));
        ReflectionTestUtils.setField(accountService, "maxValidRate", new BigDecimal("2000"));
        ReflectionTestUtils.setField(accountService, "fallbackRate", new BigDecimal("1350"));
    }

    @Nested
    @DisplayName("계좌 생성 테스트")
    class CreateAccountTests {

        @Test
        @DisplayName("정상적으로 계좌가 생성된다")
        void createAccount_Success() {
            // given
            CreateAccountRequestDto request = new CreateAccountRequestDto();
            request.setAccountName("신규계좌");
            request.setCommissionRate(new BigDecimal("0.001"));

            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            given(accountRepository.existsByUserIdAndAccountName(userId, "신규계좌"))
                    .willReturn(false);
            given(accountRepository.findByAccountNumber(anyString()))
                    .willReturn(Optional.empty());
            given(accountRepository.save(any(Account.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            Account result = accountService.createAccount(userId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAccountName()).isEqualTo("신규계좌");
            assertThat(result.getBalanceKrw()).isEqualByComparingTo(new BigDecimal("1000000"));
            assertThat(result.getBalanceUsd()).isEqualByComparingTo(BigDecimal.ZERO);

            verify(accountRepository).save(any(Account.class));
            verify(userLogger).logAccountCreation(eq(userId), any(), anyString(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("사용자가 없으면 예외가 발생한다")
        void createAccount_UserNotFound_ThrowsException() {
            // given
            CreateAccountRequestDto request = new CreateAccountRequestDto();
            request.setAccountName("신규계좌");
            request.setCommissionRate(new BigDecimal("0.001"));

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountService.createAccount(userId, request)
            ).isInstanceOf(Exception.class);

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("중복된 계좌명이 있으면 예외가 발생한다")
        void createAccount_DuplicateName_ThrowsException() {
            // given
            CreateAccountRequestDto request = new CreateAccountRequestDto();
            request.setAccountName("중복계좌");
            request.setCommissionRate(new BigDecimal("0.001"));

            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
            given(accountRepository.existsByUserIdAndAccountName(userId, "중복계좌"))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() ->
                    accountService.createAccount(userId, request)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.DUPLICATE_ACCOUNT_NAME.getMessage());

            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("KRW 입금 테스트")
    class DepositKrwTests {

        @Test
        @DisplayName("KRW 입금이 정상 처리된다")
        void depositKrw_Success() {
            // given
            BigDecimal depositAmount = new BigDecimal("500000");
            BigDecimal initialBalance = testAccount.getBalanceKrw();

            given(accountRepository.findByIdAndUserIdWithLock(accountId, userId))
                    .willReturn(Optional.of(testAccount));

            // when
            accountService.depositKrw(accountId, userId, depositAmount);

            // then
            assertThat(testAccount.getBalanceKrw())
                    .isEqualByComparingTo(initialBalance.add(depositAmount));

            verify(accountHistoryService).createDepositHistory(
                    eq(accountId), eq(depositAmount), eq("KRW"), anyString(), anyString());
            verify(userLogger).logKrwDeposit(eq(accountId), eq(depositAmount), anyString());
        }

        @Test
        @DisplayName("계좌가 없으면 예외가 발생한다")
        void depositKrw_AccountNotFound_ThrowsException() {
            // given
            BigDecimal depositAmount = new BigDecimal("500000");

            given(accountRepository.findByIdAndUserIdWithLock(accountId, userId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountService.depositKrw(accountId, userId, depositAmount)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("환전 테스트")
    class ExchangeTests {

        @Test
        @DisplayName("KRW to USD 환전이 정상 처리된다")
        void exchangeKrwToUsd_Success() {
            // given
            BigDecimal krwAmount = new BigDecimal("1000000");
            BigDecimal exchangeRate = new BigDecimal("1300.00"); // 소수점 2자리
            BigDecimal expectedUsd = krwAmount.divide(exchangeRate, 2, java.math.RoundingMode.HALF_UP);

            ExchangeCalculationResult calculation = new ExchangeCalculationResult(
                    krwAmount,              // requestAmount
                    exchangeRate,           // exchangeRate
                    "KRW",                  // fromCurrency
                    "USD",                  // toCurrency
                    expectedUsd,            // beforeCommissionAmount
                    BigDecimal.ZERO,        // commissionAmount
                    expectedUsd             // finalAmount
            );

            given(accountRepository.findByIdAndUserIdWithLock(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            doNothing().when(accountCalculator).validateExchangeRequest(testAccount, krwAmount, "KRW");
            // any()를 사용하여 반올림된 exchangeRate도 매칭
            given(accountCalculator.calculateExchangeWithCommission(
                    eq(testAccount), eq(krwAmount), eq("KRW"), eq("USD"), any(BigDecimal.class)))
                    .willReturn(calculation);
            given(accountCalculator.calculateNewAverageRate(eq(testAccount), eq(krwAmount), any(BigDecimal.class)))
                    .willReturn(exchangeRate);

            // when
            accountService.exchangeKrwToUsd(accountId, userId, krwAmount, exchangeRate);

            // then
            assertThat(testAccount.getBalanceKrw()).isLessThan(new BigDecimal("1000000"));
            assertThat(testAccount.getBalanceUsd()).isGreaterThan(BigDecimal.ZERO);
            assertThat(testAccount.getAvgExchangeRate()).isEqualByComparingTo(exchangeRate);

            verify(accountHistoryService).createExchangeHistory(
                    eq(accountId), eq("KRW"), eq("USD"), any(), any(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("잔액이 부족하면 예외가 발생한다")
        void exchangeKrwToUsd_InsufficientBalance_ThrowsException() {
            // given
            BigDecimal krwAmount = new BigDecimal("2000000"); // 초과 금액
            BigDecimal exchangeRate = new BigDecimal("1300");

            given(accountRepository.findByIdAndUserIdWithLock(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            doThrow(new AccountException(AccountResponse.INSUFFICIENT_BALANCE))
                    .when(accountCalculator).validateExchangeRequest(testAccount, krwAmount, "KRW");

            // when & then
            assertThatThrownBy(() ->
                    accountService.exchangeKrwToUsd(accountId, userId, krwAmount, exchangeRate)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.INSUFFICIENT_BALANCE.getMessage());
        }
    }

    @Nested
    @DisplayName("환율 조회 테스트")
    class GetExchangeRateTests {

        @Test
        @DisplayName("정상적으로 최신 환율을 조회한다")
        void getCurrentExchangeRate_Success() {
            // given
            FxRateDto fxRate = new FxRateDto();
            ReflectionTestUtils.setField(fxRate, "rate", new BigDecimal("1320.50"));
            ReflectionTestUtils.setField(fxRate, "date", LocalDate.now());

            given(marketDataServiceClientWrapper.getLatestFxRate()).willReturn(fxRate);

            // when
            BigDecimal rate = accountService.getCurrentExchangeRate();

            // then
            assertThat(rate).isEqualByComparingTo(new BigDecimal("1320.50"));
            verify(marketDataServiceClientWrapper).getLatestFxRate();
        }

        @Test
        @DisplayName("환율 조회 실패 시 Fallback 환율을 반환한다")
        void getCurrentExchangeRate_Failure_ReturnsFallback() {
            // given
            given(marketDataServiceClientWrapper.getLatestFxRate())
                    .willThrow(new RuntimeException("Service unavailable"));

            // when
            BigDecimal rate = accountService.getCurrentExchangeRate();

            // then
            assertThat(rate).isEqualByComparingTo(new BigDecimal("1350")); // fallback
            verify(userLogger).logExchangeRateFallback(any(BigDecimal.class), anyString());
        }

        @Test
        @DisplayName("비정상적인 환율은 Fallback으로 대체된다")
        void getCurrentExchangeRate_AbnormalRate_ReturnsFallback() {
            // given
            FxRateDto fxRate = new FxRateDto();
            ReflectionTestUtils.setField(fxRate, "rate", new BigDecimal("5000")); // 비정상
            ReflectionTestUtils.setField(fxRate, "date", LocalDate.now());

            given(marketDataServiceClientWrapper.getLatestFxRate()).willReturn(fxRate);

            // when
            BigDecimal rate = accountService.getCurrentExchangeRate();

            // then
            assertThat(rate).isEqualByComparingTo(new BigDecimal("1350")); // fallback
            verify(userLogger).logAbnormalExchangeRate(any(BigDecimal.class), anyString());
        }
    }

    @Nested
    @DisplayName("계좌 삭제 테스트")
    class DeleteAccountTests {

        @Test
        @DisplayName("잔액이 없으면 계좌가 삭제된다")
        void deleteAccount_ZeroBalance_Success() {
            // given
            Account emptyAccount = Account.builder()
                    .id(accountId)
                    .user(testUser)
                    .accountNumber("ACC1234567890")
                    .accountName("빈계좌")
                    .balanceKrw(BigDecimal.ZERO)
                    .balanceUsd(BigDecimal.ZERO)
                    .build();

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(emptyAccount));

            // when
            accountService.deleteAccount(accountId, userId);

            // then
            verify(accountHistoryRepository).deleteByAccount(emptyAccount);
            verify(accountRepository).delete(emptyAccount);
        }

        @Test
        @DisplayName("잔액이 있어도 계좌가 삭제된다 (로그만 기록)")
        void deleteAccount_HasBalance_StillDeletes() {
            // given - testAccount는 잔액이 있음
            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));

            // when
            accountService.deleteAccount(accountId, userId);

            // then - 잔액이 있어도 삭제됨 (정책 변경)
            verify(accountHistoryRepository).deleteByAccount(testAccount);
            verify(accountRepository).delete(testAccount);
        }

        @Test
        @DisplayName("다른 사용자의 계좌는 삭제할 수 없다")
        void deleteAccount_OtherUser_ThrowsException() {
            // given
            Long otherUserId = 999L;
            given(accountRepository.findByIdAndUserId(accountId, otherUserId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountService.deleteAccount(accountId, otherUserId)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());

            verify(accountHistoryRepository, never()).deleteByAccount(any(Account.class));
            verify(accountRepository, never()).delete(any(Account.class));
        }
    }

    @Nested
    @DisplayName("USD 환전 테스트")
    class ExchangeUsdToKrwTests {

        @Test
        @DisplayName("USD to KRW 환전이 정상 처리된다")
        void exchangeUsdToKrw_Success() {
            // given
            testAccount.setBalanceUsd(new BigDecimal("1000.00"));
            BigDecimal usdAmount = new BigDecimal("500.00");
            BigDecimal exchangeRate = new BigDecimal("1300.00");
            BigDecimal expectedKrw = usdAmount.multiply(exchangeRate);

            ExchangeCalculationResult calculation = new ExchangeCalculationResult(
                    usdAmount,              // requestAmount
                    exchangeRate,           // exchangeRate
                    "USD",                  // fromCurrency
                    "KRW",                  // toCurrency
                    expectedKrw,            // beforeCommissionAmount
                    BigDecimal.ZERO,        // commissionAmount
                    expectedKrw             // finalAmount
            );

            given(accountRepository.findByIdAndUserIdWithLock(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            doNothing().when(accountCalculator).validateExchangeRequest(testAccount, usdAmount, "USD");
            given(accountCalculator.calculateExchangeWithCommission(
                    eq(testAccount), eq(usdAmount), eq("USD"), eq("KRW"), any(BigDecimal.class)))
                    .willReturn(calculation);

            // when
            accountService.exchangeUsdToKrw(accountId, userId, usdAmount, exchangeRate);

            // then
            assertThat(testAccount.getBalanceUsd()).isLessThan(new BigDecimal("1000.00"));
            assertThat(testAccount.getBalanceKrw()).isGreaterThan(BigDecimal.ZERO);

            verify(accountHistoryService).createExchangeHistory(
                    eq(accountId), eq("USD"), eq("KRW"), any(), any(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("USD 잔액이 0이 되면 평균환율이 리셋된다")
        void exchangeUsdToKrw_AllUsd_ResetsAvgRate() {
            // given
            testAccount.setBalanceUsd(new BigDecimal("1000.00"));
            testAccount.setAvgExchangeRate(new BigDecimal("1300.00"));
            BigDecimal usdAmount = new BigDecimal("1000.00"); // 전액 환전
            BigDecimal exchangeRate = new BigDecimal("1300.00");

            ExchangeCalculationResult calculation = new ExchangeCalculationResult(
                    usdAmount, exchangeRate, "USD", "KRW",
                    usdAmount.multiply(exchangeRate), BigDecimal.ZERO,
                    usdAmount.multiply(exchangeRate)
            );

            given(accountRepository.findByIdAndUserIdWithLock(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            doNothing().when(accountCalculator).validateExchangeRequest(testAccount, usdAmount, "USD");
            given(accountCalculator.calculateExchangeWithCommission(
                    eq(testAccount), eq(usdAmount), eq("USD"), eq("KRW"), any(BigDecimal.class)))
                    .willReturn(calculation);

            // when
            accountService.exchangeUsdToKrw(accountId, userId, usdAmount, exchangeRate);

            // then
            assertThat(testAccount.getAvgExchangeRate()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(testAccount.getTotalExchangedKrw()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("계좌 목록 조회 테스트")
    class GetUserAccountsTests {

        @Test
        @DisplayName("사용자의 모든 계좌를 조회한다")
        void getUserAccounts_Success() {
            // given
            Account account2 = Account.builder()
                    .id(2L)
                    .user(testUser)
                    .accountNumber("ACC9876543210")
                    .accountName("두번째계좌")
                    .build();

            given(accountRepository.findByUserIdWithUser(userId))
                    .willReturn(Arrays.asList(testAccount, account2));

            // when
            List<Account> result = accountService.getUserAccounts(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(testAccount, account2);
            verify(accountRepository).findByUserIdWithUser(userId);
        }

        @Test
        @DisplayName("계좌가 없으면 빈 리스트를 반환한다")
        void getUserAccounts_NoAccounts_ReturnsEmptyList() {
            // given
            given(accountRepository.findByUserIdWithUser(userId))
                    .willReturn(new ArrayList<>());

            // when
            List<Account> result = accountService.getUserAccounts(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("잔액 조회 테스트")
    class GetAccountBalanceTests {

        @Test
        @DisplayName("계좌 잔액을 조회한다")
        void getAccountBalance_Success() {
            // given
            BigDecimal currentRate = new BigDecimal("1320.00");
            FxRateDto fxRate = new FxRateDto();
            ReflectionTestUtils.setField(fxRate, "rate", currentRate);

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(marketDataServiceClientWrapper.getLatestFxRate()).willReturn(fxRate);

            // when
            BalanceResponseDto result = accountService.getAccountBalance(accountId, userId);

            // then
            assertThat(result).isNotNull();
            verify(marketDataServiceClientWrapper).getLatestFxRate();
        }

        @Test
        @DisplayName("계좌가 없으면 예외가 발생한다")
        void getAccountBalance_AccountNotFound_ThrowsException() {
            // given
            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountService.getAccountBalance(accountId, userId)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("USD 잔액 업데이트 테스트")
    class UpdateUsdBalanceTests {

        @Test
        @DisplayName("USD 잔액이 증가한다 (매도)")
        void updateUsdBalance_Increase_Success() {
            // given
            BigDecimal sellAmount = new BigDecimal("500.00");
            testAccount.setBalanceUsd(new BigDecimal("1000.00"));

            given(accountRepository.findByIdWithLock(accountId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.save(testAccount)).willReturn(testAccount);

            // when
            accountService.updateUsdBalance(accountId, userId, sellAmount, "주식 매도");

            // then
            assertThat(testAccount.getBalanceUsd())
                    .isEqualByComparingTo(new BigDecimal("1500.00"));
            verify(accountHistoryRepository).save(any(AccountHistory.class));
        }

        @Test
        @DisplayName("USD 잔액이 감소한다 (매수)")
        void updateUsdBalance_Decrease_Success() {
            // given
            BigDecimal buyAmount = new BigDecimal("-500.00");
            testAccount.setBalanceUsd(new BigDecimal("1000.00"));

            given(accountRepository.findByIdWithLock(accountId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.save(testAccount)).willReturn(testAccount);

            // when
            accountService.updateUsdBalance(accountId, userId, buyAmount, "주식 매수");

            // then
            assertThat(testAccount.getBalanceUsd())
                    .isEqualByComparingTo(new BigDecimal("500.00"));
            verify(accountHistoryRepository).save(any(AccountHistory.class));
        }

        @Test
        @DisplayName("잔액 부족 시 예외가 발생한다")
        void updateUsdBalance_InsufficientBalance_ThrowsException() {
            // given
            BigDecimal excessiveAmount = new BigDecimal("-2000.00");
            testAccount.setBalanceUsd(new BigDecimal("1000.00"));

            given(accountRepository.findByIdWithLock(accountId))
                    .willReturn(Optional.of(testAccount));

            // when & then
            assertThatThrownBy(() ->
                    accountService.updateUsdBalance(accountId, userId, excessiveAmount, "주식 매수")
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.INSUFFICIENT_USD_BALANCE.getMessage());

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("금액이 0이면 예외가 발생한다")
        void updateUsdBalance_ZeroAmount_ThrowsException() {
            // given
            given(accountRepository.findByIdWithLock(accountId))
                    .willReturn(Optional.of(testAccount));

            // when & then
            assertThatThrownBy(() ->
                    accountService.updateUsdBalance(accountId, userId, BigDecimal.ZERO, "테스트")
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.INVALID_DEPOSIT_AMOUNT.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 계좌는 업데이트할 수 없다")
        void updateUsdBalance_WrongUser_ThrowsException() {
            // given
            Long otherUserId = 999L;
            User otherUser = User.builder().id(otherUserId).build();
            testAccount.setUser(otherUser);

            given(accountRepository.findByIdWithLock(accountId))
                    .willReturn(Optional.of(testAccount));

            // when & then
            assertThatThrownBy(() ->
                    accountService.updateUsdBalance(accountId, userId, new BigDecimal("100"), "테스트")
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.ACCOUNT_ACCESS_DENIED.getMessage());
        }
    }

    @Nested
    @DisplayName("특정 날짜 환율 조회 테스트")
    class GetExchangeRateByDateTests {

        @Test
        @DisplayName("특정 날짜의 환율을 조회한다")
        void getExchangeRateByDate_Success() {
            // given
            LocalDate testDate = LocalDate.of(2025, 1, 15);
            FxRateDto fxRate = new FxRateDto();
            ReflectionTestUtils.setField(fxRate, "rate", new BigDecimal("1315.50"));
            ReflectionTestUtils.setField(fxRate, "date", testDate);

            given(marketDataServiceClientWrapper.getFxRate(testDate.toString())).willReturn(fxRate);

            // when
            BigDecimal rate = accountService.getExchangeRateByDate(testDate);

            // then
            assertThat(rate).isEqualByComparingTo(new BigDecimal("1315.50"));
        }

        @Test
        @DisplayName("데이터가 없으면 최신 환율로 fallback한다")
        void getExchangeRateByDate_NoData_FallbackToLatest() {
            // given
            LocalDate testDate = LocalDate.of(2025, 1, 1);
            FxRateDto latestRate = new FxRateDto();
            ReflectionTestUtils.setField(latestRate, "rate", new BigDecimal("1320.00"));

            given(marketDataServiceClientWrapper.getFxRate(testDate.toString()))
                    .willReturn(null);
            given(marketDataServiceClientWrapper.getLatestFxRate()).willReturn(latestRate);

            // when
            BigDecimal rate = accountService.getExchangeRateByDate(testDate);

            // then
            assertThat(rate).isEqualByComparingTo(new BigDecimal("1320.00"));
            verify(marketDataServiceClientWrapper).getLatestFxRate();
        }
    }

    @Nested
    @DisplayName("수동 환율 생성 테스트")
    class CreateManualExchangeRateTests {

        @Test
        @DisplayName("유효한 수동 환율을 생성한다")
        void createManualExchangeRate_Valid_Success() {
            // given
            BigDecimal manualRate = new BigDecimal("1330.50");

            // when
            BigDecimal result = accountService.createManualExchangeRate(manualRate);

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("1330.50"));
            verify(userLogger).logManualExchangeRate(any(BigDecimal.class), anyString());
        }

        @Test
        @DisplayName("null 환율은 예외가 발생한다")
        void createManualExchangeRate_Null_ThrowsException() {
            // when & then
            assertThatThrownBy(() ->
                    accountService.createManualExchangeRate(null)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.INVALID_EXCHANGE_RATE.getMessage());
        }

        @Test
        @DisplayName("최소값보다 낮은 환율은 예외가 발생한다")
        void createManualExchangeRate_TooLow_ThrowsException() {
            // given
            BigDecimal tooLow = new BigDecimal("500");

            // when & then
            assertThatThrownBy(() ->
                    accountService.createManualExchangeRate(tooLow)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.INVALID_EXCHANGE_RATE.getMessage());
        }

        @Test
        @DisplayName("최대값보다 높은 환율은 예외가 발생한다")
        void createManualExchangeRate_TooHigh_ThrowsException() {
            // given
            BigDecimal tooHigh = new BigDecimal("3000");

            // when & then
            assertThatThrownBy(() ->
                    accountService.createManualExchangeRate(tooHigh)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.INVALID_EXCHANGE_RATE.getMessage());
        }
    }

    @Nested
    @DisplayName("거래 완료 이벤트 처리 테스트")
    class ProcessTradeEventTests {

        // Note: processTradeEvent 메서드는 updateUsdBalance를 내부적으로 호출하므로
        // 통합 테스트로 작성하는 것이 더 적합합니다. 여기서는 예외 케이스만 테스트합니다.

        @Test
        @DisplayName("잘못된 userId 형식 시 예외 발생")
        void processTradeEvent_InvalidUserId_ThrowsException() {
            // given
            com.muscat.messaging.event.TradeCompletedEvent event =
                    com.muscat.messaging.event.TradeCompletedEvent.builder()
                            .userId("invalid-user-id")
                            .tradeId("TRADE-003")
                            .symbol("TSLA")
                            .tradeType("BUY")
                            .quantity(new BigDecimal("1"))
                            .price(new BigDecimal("800.00"))
                            .totalAmount(new BigDecimal("800.00"))
                            .currency("USD")
                            .build();

            // when & then
            assertThatThrownBy(() -> accountService.processTradeEvent(event))
                    .isInstanceOf(AccountException.class)
                    .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("계좌가 없는 사용자의 거래 이벤트 처리 시 예외 발생")
        void processTradeEvent_NoAccount_ThrowsException() {
            // given
            com.muscat.messaging.event.TradeCompletedEvent event =
                    com.muscat.messaging.event.TradeCompletedEvent.builder()
                            .userId(userId.toString())
                            .tradeId("TRADE-004")
                            .symbol("MSFT")
                            .tradeType("BUY")
                            .quantity(new BigDecimal("10"))
                            .price(new BigDecimal("350.00"))
                            .totalAmount(new BigDecimal("3500.00"))
                            .currency("USD")
                            .build();

            given(accountRepository.findByUserIdWithUser(userId))
                    .willReturn(new ArrayList<>());

            // when & then
            assertThatThrownBy(() -> accountService.processTradeEvent(event))
                    .isInstanceOf(AccountException.class)
                    .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("알 수 없는 거래 타입 시 예외 발생")
        void processTradeEvent_UnknownTradeType_ThrowsException() {
            // given
            com.muscat.messaging.event.TradeCompletedEvent event =
                    com.muscat.messaging.event.TradeCompletedEvent.builder()
                            .userId(userId.toString())
                            .tradeId("TRADE-005")
                            .symbol("AMZN")
                            .tradeType("UNKNOWN")
                            .quantity(new BigDecimal("2"))
                            .price(new BigDecimal("3200.00"))
                            .totalAmount(new BigDecimal("6400.00"))
                            .currency("USD")
                            .build();

            given(accountRepository.findByUserIdWithUser(userId))
                    .willReturn(Arrays.asList(testAccount));

            // when & then
            assertThatThrownBy(() -> accountService.processTradeEvent(event))
                    .isInstanceOf(AccountException.class)
                    .hasMessage(AccountResponse.INVALID_TRANSACTION_TYPE.getMessage());
        }
    }

    @Nested
    @DisplayName("거래 취소 이벤트 처리 테스트")
    class ProcessTradeCancellationEventTests {

        // Note: 마찬가지로 통합 테스트가 더 적합합니다.

        @Test
        @DisplayName("취소 이벤트 - 계좌 없을 때 예외 발생")
        void processTradeCancellationEvent_NoAccount_ThrowsException() {
            // given
            TradeCancelledEvent event =
                    com.muscat.messaging.event.TradeCancelledEvent.builder()
                            .userId(userId.toString())
                            .tradeId("TRADE-003")
                            .symbol("TSLA")
                            .tradeType("BUY")
                            .quantity(new BigDecimal("1"))
                            .price(new BigDecimal("800.00"))
                            .totalAmount(new BigDecimal("800.00"))
                            .cancellationReason("System error")
                            .build();

            given(accountRepository.findByUserIdWithUser(userId))
                    .willReturn(new ArrayList<>());

            // when & then
            assertThatThrownBy(() -> accountService.processTradeCancellationEvent(event))
                    .isInstanceOf(AccountException.class)
                    .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("배당금 수령 이벤트 처리 테스트")
    class ProcessDividendReceivedEventTests {

        @Test
        @DisplayName("배당금 수령 - 계좌 없을 때 예외 발생")
        void processDividendReceivedEvent_AccountNotFound_ThrowsException() {
            // given
            DividendReceivedEvent event =
                    com.muscat.messaging.event.DividendReceivedEvent.builder()
                            .userId(userId.toString())
                            .accountId(999L)
                            .symbol("GOOGL")
                            .quantity(new BigDecimal("50"))
                            .dividendPerShare(new BigDecimal("0.50"))
                            .totalAmount(new BigDecimal("25.00"))
                            .exDate("2024-11-01")
                            .currency("USD")
                            .build();

            given(accountRepository.findById(999L))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> accountService.processDividendReceivedEvent(event))
                    .isInstanceOf(AccountException.class)
                    .hasMessage(AccountResponse.ACCOUNT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("배당금 수령 - 계좌 소유자 불일치 시 예외 발생")
        void processDividendReceivedEvent_OwnerMismatch_ThrowsException() {
            // given
            User otherUser = User.builder()
                    .id(999L)
                    .email("other@example.com")
                    .nickname("otheruser")
                    .build();

            Account otherAccount = Account.builder()
                    .id(accountId)
                    .user(otherUser)
                    .accountNumber("987654321")
                    .balanceKrw(BigDecimal.ZERO)
                    .balanceUsd(BigDecimal.ZERO)
                    .build();

            DividendReceivedEvent event =
                    com.muscat.messaging.event.DividendReceivedEvent.builder()
                            .userId(userId.toString())
                            .accountId(accountId)
                            .symbol("MSFT")
                            .quantity(new BigDecimal("20"))
                            .dividendPerShare(new BigDecimal("0.68"))
                            .totalAmount(new BigDecimal("13.60"))
                            .exDate("2024-11-01")
                            .currency("USD")
                            .build();

            given(accountRepository.findById(accountId))
                    .willReturn(Optional.of(otherAccount));

            // when & then
            assertThatThrownBy(() -> accountService.processDividendReceivedEvent(event))
                    .isInstanceOf(AccountException.class)
                    .hasMessage(AccountResponse.ACCOUNT_ACCESS_DENIED.getMessage());
        }
    }

    @Nested
    @DisplayName("사용자 ID로 계좌 조회 테스트")
    class FindAccountsByUserIdTests {

        @Test
        @DisplayName("사용자 ID로 계좌 목록 조회 성공")
        void findAccountsByUserId_Success() {
            // given
            List<Account> accounts = Arrays.asList(testAccount);
            given(accountRepository.findByUserIdWithUser(userId))
                    .willReturn(accounts);

            // when
            List<Account> result = accountService.findAccountsByUserId(userId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(testAccount);
            verify(accountRepository).findByUserIdWithUser(userId);
        }

        @Test
        @DisplayName("계좌가 없는 사용자 조회 시 빈 리스트 반환")
        void findAccountsByUserId_NoAccounts_ReturnsEmptyList() {
            // given
            given(accountRepository.findByUserIdWithUser(userId))
                    .willReturn(new ArrayList<>());

            // when
            List<Account> result = accountService.findAccountsByUserId(userId);

            // then
            assertThat(result).isEmpty();
            verify(accountRepository).findByUserIdWithUser(userId);
        }
    }
}
