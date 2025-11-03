package com.muscat.user.domain.account.service.impl;

import com.muscat.commonlib.exception.ServiceException;
import com.muscat.user.common.enums.responses.AccountHistoryResponse;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.exceptions.AccountHistoryException;
import com.muscat.user.domain.account.dto.response.HistoryListResponseDto;
import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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

    @Nested
    @DisplayName("거래 내역 조회 테스트")
    class GetAccountHistoriesTests {

        @Test
        @DisplayName("계좌별 거래 내역을 페이징으로 조회한다")
        void getAccountHistories_Success() {
            // given
            int page = 0;
            int size = 10;
            LocalDateTime from = LocalDateTime.now().minusDays(30);
            LocalDateTime to = LocalDateTime.now();

            List<AccountHistory> histories = Arrays.asList(testHistory, testHistory);
            Page<AccountHistory> historyPage = new PageImpl<>(histories);

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndRange(
                    eq(testAccount), eq(from), eq(to), any(Pageable.class)))
                    .willReturn(historyPage);
            given(accountRepository.getTotalAmountByAccountAndType(testAccount, TransactionType.DEPOSIT))
                    .willReturn(new BigDecimal("1000000"));
            given(accountRepository.getTotalAmountByAccountAndType(testAccount, TransactionType.EXCHANGE))
                    .willReturn(new BigDecimal("500000"));

            // when
            HistoryListResponseDto result = accountHistoryService.getAccountHistories(
                    accountId, page, size, userId, from, to);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getHistories()).hasSize(2);
            assertThat(result.getAccountId()).isEqualTo(accountId);
            assertThat(result.getTotalDeposit()).isEqualByComparingTo(new BigDecimal("1000000"));
            assertThat(result.getTotalExchange()).isEqualByComparingTo(new BigDecimal("500000"));
        }

        @Test
        @DisplayName("다른 사용자의 계좌 내역은 조회할 수 없다")
        void getAccountHistories_OtherUser_ThrowsException() {
            // given
            Long otherUserId = 999L;
            given(accountRepository.findByIdAndUserId(accountId, otherUserId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountHistoryService.getAccountHistories(
                            accountId, 0, 10, otherUserId, null, null)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.ACCOUNT_ACCESS_DENIED.getMessage());
        }
    }

    @Nested
    @DisplayName("특정 거래 내역 상세 조회 테스트")
    class GetAccountHistoryTests {

        @Test
        @DisplayName("특정 거래 내역을 상세 조회한다")
        void getAccountHistory_Success() {
            // given
            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByIdAndAccountId(historyId, accountId))
                    .willReturn(Optional.of(testHistory));

            // when
            HistoryResponseDto result = accountHistoryService.getAccountHistory(
                    accountId, historyId, userId);

            // then
            assertThat(result).isNotNull();
            verify(accountRepository).findHistoryByIdAndAccountId(historyId, accountId);
        }

        @Test
        @DisplayName("존재하지 않는 거래 내역이면 예외가 발생한다")
        void getAccountHistory_NotFound_ThrowsException() {
            // given
            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByIdAndAccountId(historyId, accountId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountHistoryService.getAccountHistory(accountId, historyId, userId)
            ).isInstanceOf(AccountHistoryException.class)
             .hasMessage(AccountHistoryResponse.HISTORY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 거래 내역은 조회할 수 없다")
        void getAccountHistory_OtherUser_ThrowsException() {
            // given
            Long otherUserId = 999L;
            given(accountRepository.findByIdAndUserId(accountId, otherUserId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountHistoryService.getAccountHistory(accountId, historyId, otherUserId)
            ).isInstanceOf(AccountException.class)
             .hasMessage(AccountResponse.ACCOUNT_ACCESS_DENIED.getMessage());
        }
    }

    @Nested
    @DisplayName("환전 내역 조회 테스트")
    class GetExchangeHistoriesTests {

        @Test
        @DisplayName("환전 내역만 조회한다")
        void getExchangeHistories_Success() {
            // given
            AccountHistory exchangeHistory = AccountHistory.builder()
                    .id(historyId)
                    .account(testAccount)
                    .transactionType(TransactionType.EXCHANGE)
                    .amount(new BigDecimal("769.23"))
                    .currency("USD")
                    .fromCurrency("KRW")
                    .toCurrency("USD")
                    .originalAmount(new BigDecimal("1000000"))
                    .exchangeRate(new BigDecimal("1300.00"))
                    .build();

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findExchangeHistoryByAccount(testAccount))
                    .willReturn(Arrays.asList(exchangeHistory));

            // when
            List<HistoryResponseDto> result = accountHistoryService.getExchangeHistories(
                    accountId, userId);

            // then
            assertThat(result).hasSize(1);
            verify(accountRepository).findExchangeHistoryByAccount(testAccount);
        }

        @Test
        @DisplayName("환전 내역이 없으면 빈 리스트를 반환한다")
        void getExchangeHistories_Empty_ReturnsEmptyList() {
            // given
            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findExchangeHistoryByAccount(testAccount))
                    .willReturn(new ArrayList<>());

            // when
            List<HistoryResponseDto> result = accountHistoryService.getExchangeHistories(
                    accountId, userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("통화별 내역 조회 테스트")
    class GetHistoriesByCurrencyTests {

        @Test
        @DisplayName("특정 통화의 거래 내역을 조회한다")
        void getHistoriesByCurrency_Success() {
            // given
            String currency = "KRW";
            Page<AccountHistory> historyPage = new PageImpl<>(Arrays.asList(testHistory));

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndCurrency(
                    eq(testAccount), eq(currency), any(Pageable.class)))
                    .willReturn(historyPage);

            // when
            List<HistoryResponseDto> result = accountHistoryService.getHistoriesByCurrency(
                    accountId, currency, userId);

            // then
            assertThat(result).hasSize(1);
            verify(accountRepository).findHistoryByAccountAndCurrency(
                    eq(testAccount), eq(currency), any(Pageable.class));
        }

        @Test
        @DisplayName("USD 통화의 거래 내역을 조회한다")
        void getHistoriesByCurrency_USD_Success() {
            // given
            String currency = "USD";
            AccountHistory usdHistory = AccountHistory.builder()
                    .id(historyId)
                    .account(testAccount)
                    .transactionType(TransactionType.DEPOSIT)
                    .amount(new BigDecimal("100.00"))
                    .currency(currency)
                    .build();

            Page<AccountHistory> historyPage = new PageImpl<>(Arrays.asList(usdHistory));

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndCurrency(
                    eq(testAccount), eq(currency), any(Pageable.class)))
                    .willReturn(historyPage);

            // when
            List<HistoryResponseDto> result = accountHistoryService.getHistoriesByCurrency(
                    accountId, currency, userId);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("기간별 내역 조회 테스트")
    class GetHistoriesByDateRangeTests {

        @Test
        @DisplayName("특정 기간의 거래 내역을 조회한다")
        void getHistoriesByDateRange_Success() {
            // given
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();
            Page<AccountHistory> historyPage = new PageImpl<>(Arrays.asList(testHistory, testHistory));

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndRange(
                    eq(testAccount), eq(startDate), eq(endDate), any(Pageable.class)))
                    .willReturn(historyPage);

            // when
            List<HistoryResponseDto> result = accountHistoryService.getHistoriesByDateRange(
                    accountId, startDate, endDate, userId);

            // then
            assertThat(result).hasSize(2);
            verify(accountRepository).findHistoryByAccountAndRange(
                    eq(testAccount), eq(startDate), eq(endDate), any(Pageable.class));
        }

        @Test
        @DisplayName("기간 내 거래 내역이 없으면 빈 리스트를 반환한다")
        void getHistoriesByDateRange_NoData_ReturnsEmptyList() {
            // given
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();
            Page<AccountHistory> emptyPage = new PageImpl<>(new ArrayList<>());

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndRange(
                    eq(testAccount), eq(startDate), eq(endDate), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when
            List<HistoryResponseDto> result = accountHistoryService.getHistoriesByDateRange(
                    accountId, startDate, endDate, userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("최근 내역 조회 테스트")
    class GetRecentHistoriesTests {

        @Test
        @DisplayName("최근 N개 거래 내역을 조회한다")
        void getRecentHistories_Success() {
            // given
            int limit = 5;
            List<AccountHistory> histories = Arrays.asList(
                    testHistory, testHistory, testHistory);
            Page<AccountHistory> historyPage = new PageImpl<>(histories);

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndRange(
                    eq(testAccount), isNull(), isNull(), any(Pageable.class)))
                    .willReturn(historyPage);

            // when
            List<HistoryResponseDto> result = accountHistoryService.getRecentHistories(
                    accountId, limit, userId);

            // then
            assertThat(result).hasSize(3);
            verify(accountRepository).findHistoryByAccountAndRange(
                    eq(testAccount), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("최근 10개 거래 내역을 조회한다")
        void getRecentHistories_Limit10_Success() {
            // given
            int limit = 10;
            Page<AccountHistory> historyPage = new PageImpl<>(Arrays.asList(testHistory));

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndRange(
                    eq(testAccount), isNull(), isNull(), any(Pageable.class)))
                    .willReturn(historyPage);

            // when
            List<HistoryResponseDto> result = accountHistoryService.getRecentHistories(
                    accountId, limit, userId);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("거래 내역이 없으면 빈 리스트를 반환한다")
        void getRecentHistories_NoData_ReturnsEmptyList() {
            // given
            int limit = 5;
            Page<AccountHistory> emptyPage = new PageImpl<>(new ArrayList<>());

            given(accountRepository.findByIdAndUserId(accountId, userId))
                    .willReturn(Optional.of(testAccount));
            given(accountRepository.findHistoryByAccountAndRange(
                    eq(testAccount), isNull(), isNull(), any(Pageable.class)))
                    .willReturn(emptyPage);

            // when
            List<HistoryResponseDto> result = accountHistoryService.getRecentHistories(
                    accountId, limit, userId);

            // then
            assertThat(result).isEmpty();
        }
    }
}
