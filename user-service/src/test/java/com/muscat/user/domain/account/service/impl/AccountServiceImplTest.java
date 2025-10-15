package com.muscat.user.domain.account.service.impl;

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
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountQueryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.service.AccountHistoryService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClient;
import com.muscat.user.infra.client.dto.FxRateDto;
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
    private AccountQueryRepository accountQueryRepository;

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
    private MarketDataServiceClient marketDataServiceClient;

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
            given(accountQueryRepository.existsByUserIdAndAccountName(userId, "신규계좌"))
                    .willReturn(false);
            given(accountQueryRepository.findByAccountNumber(anyString()))
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
            given(accountQueryRepository.existsByUserIdAndAccountName(userId, "중복계좌"))
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

            given(accountQueryRepository.findByIdAndUserIdWithLock(accountId, userId))
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

            given(accountQueryRepository.findByIdAndUserIdWithLock(accountId, userId))
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

            given(accountQueryRepository.findByIdAndUserIdWithLock(accountId, userId))
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

            given(accountQueryRepository.findByIdAndUserIdWithLock(accountId, userId))
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

            given(marketDataServiceClient.getLatestFxRate()).willReturn(fxRate);

            // when
            BigDecimal rate = accountService.getCurrentExchangeRate();

            // then
            assertThat(rate).isEqualByComparingTo(new BigDecimal("1320.50"));
            verify(marketDataServiceClient).getLatestFxRate();
        }

        @Test
        @DisplayName("환율 조회 실패 시 Fallback 환율을 반환한다")
        void getCurrentExchangeRate_Failure_ReturnsFallback() {
            // given
            given(marketDataServiceClient.getLatestFxRate())
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

            given(marketDataServiceClient.getLatestFxRate()).willReturn(fxRate);

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

            given(accountQueryRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(emptyAccount));

            // when
            accountService.deleteAccount(accountId, userId);

            // then
            verify(accountHistoryRepository).deleteByAccount(emptyAccount);
            verify(accountRepository).delete(emptyAccount);
        }

        @Test
        @DisplayName("잔액이 있으면 계좌 삭제가 실패한다")
        void deleteAccount_HasBalance_ThrowsException() {
            // given - testAccount는 잔액이 있음
            given(accountQueryRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));

            // when & then
            assertThatThrownBy(() ->
                    accountService.deleteAccount(accountId, userId)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.CANNOT_DELETE_ACCOUNT_WITH_BALANCE.getMessage());

            verify(accountHistoryRepository, never()).deleteByAccount(any(Account.class));
            verify(accountRepository, never()).delete(any(Account.class));
        }

        @Test
        @DisplayName("다른 사용자의 계좌는 삭제할 수 없다")
        void deleteAccount_OtherUser_ThrowsException() {
            // given
            Long otherUserId = 999L;
            given(accountQueryRepository.findByIdAndUserId(accountId, otherUserId))
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
}
