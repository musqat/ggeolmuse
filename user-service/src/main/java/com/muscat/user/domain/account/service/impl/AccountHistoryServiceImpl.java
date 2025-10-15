package com.muscat.user.domain.account.service.impl;

import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.exceptions.AccountHistoryException;
import com.muscat.user.common.enums.responses.AccountHistoryResponse;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.domain.account.dto.response.HistoryListResponseDto;
import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.AccountQueryRepository;
import com.muscat.user.domain.account.service.AccountHistoryService;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AccountHistoryServiceImpl implements AccountHistoryService {

  private final AccountHistoryRepository accountHistoryRepository;
  private final AccountRepository accountRepository;
  private final AccountQueryRepository accountQueryRepository;

  // 입금 거래 내역 생성 (내부 호출용)
  @Override
  @Transactional
  public HistoryResponseDto createDepositHistory(Long accountId, BigDecimal amount,
      String currency, String description, String referenceId) {

    validateAmount(amount);
    validateCurrency(currency);

    Account account = getAccountByIdInternal(accountId);

    if (referenceId != null && accountHistoryRepository.existsByReferenceId(referenceId)) {
      throw new AccountHistoryException(AccountHistoryResponse.DUPLICATE_TRANSACTION);
    }

    BigDecimal balanceAfter = calculateBalanceAfter(account, amount, currency);

    AccountHistory history = AccountHistory.builder()
        .account(account)
        .transactionType(TransactionType.DEPOSIT)
        .amount(amount)
        .currency(currency)
        .balanceAfter(balanceAfter)
        .description(description)
        .referenceId(referenceId)
        .build();

    AccountHistory savedHistory = accountHistoryRepository.save(history);
    log.info("입금 거래 내역 생성: accountId={}, amount={} {}", accountId, amount, currency);
    return HistoryResponseDto.from(savedHistory);
  }

  // 환전 거래 내역 생성 (내부 호출용)
  @Override
  @Transactional
  public HistoryResponseDto createExchangeHistory(Long accountId, String fromCurrency,
      String toCurrency, BigDecimal originalAmount, BigDecimal exchangedAmount,
      BigDecimal exchangeRate, String description, String referenceId) {

    validateExchangeInputs(fromCurrency, toCurrency, originalAmount, exchangedAmount, exchangeRate);

    Account account = getAccountByIdInternal(accountId);

    if (referenceId != null && accountHistoryRepository.existsByReferenceId(referenceId)) {
      throw new AccountHistoryException(AccountHistoryResponse.DUPLICATE_TRANSACTION);
    }

    BigDecimal balanceAfter = calculateBalanceAfter(account, exchangedAmount, toCurrency);

    AccountHistory history = AccountHistory.builder()
        .account(account)
        .transactionType(TransactionType.EXCHANGE)
        .amount(exchangedAmount)
        .currency(toCurrency)
        .balanceAfter(balanceAfter)
        .description(description)
        .referenceId(referenceId)
        .fromCurrency(fromCurrency)
        .toCurrency(toCurrency)
        .exchangeRate(exchangeRate)
        .originalAmount(originalAmount)
        .build();

    AccountHistory savedHistory = accountHistoryRepository.save(history);
    log.info("환전 거래 내역 생성: accountId={}, {} {} -> {} {} (환율: {})",
        accountId, originalAmount, fromCurrency, exchangedAmount, toCurrency, exchangeRate);
    return HistoryResponseDto.from(savedHistory);
  }

  // 계좌별 거래 내역 조회 (페이징)
  @Override
  public HistoryListResponseDto getAccountHistories(
      Long accountId, int page, int size, Long userId,
      LocalDateTime from, LocalDateTime to
  ) {
    Account account = getAccountByIdWithAuth(accountId, userId);

    var sort = Sort.by(Sort.Direction.DESC, "createdAt")
        .and(Sort.by(Sort.Direction.DESC, "id"));
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<AccountHistory> historyPage = accountQueryRepository
        .findByAccountAndRangeOrderByCreatedAtDesc(account, from, to, pageable);

    List<HistoryResponseDto> histories = historyPage.getContent()
        .stream()
        .map(HistoryResponseDto::from)
        .collect(Collectors.toList());

    BigDecimal totalDeposit = accountQueryRepository
        .getTotalAmountByAccountAndType(account, TransactionType.DEPOSIT);
    BigDecimal totalExchange = accountQueryRepository
        .getTotalAmountByAccountAndType(account, TransactionType.EXCHANGE);

    return new HistoryListResponseDto(
        accountId,
        account.getAccountNumber(),
        account.getAccountName(),
        histories,
        historyPage.getTotalPages(),
        historyPage.getTotalElements(),
        historyPage.hasNext(),
        historyPage.hasPrevious(),
        totalDeposit,
        totalExchange
    );
  }

  // 특정 거래 내역 상세 조회
  @Override
  public HistoryResponseDto getAccountHistory(Long accountId, Long historyId, Long userId) {
    // 계좌 소유권 검증
    getAccountByIdWithAuth(accountId, userId);

    AccountHistory history = accountQueryRepository.findByIdAndAccountId(historyId, accountId)
        .orElseThrow(() -> new AccountHistoryException(AccountHistoryResponse.HISTORY_NOT_FOUND));

    return HistoryResponseDto.from(history);
  }

  // 환전 내역만 조회
  @Override
  public List<HistoryResponseDto> getExchangeHistories(Long accountId, Long userId) {
    Account account = getAccountByIdWithAuth(accountId, userId);

    List<AccountHistory> exchangeHistories = accountQueryRepository
        .findExchangeHistoryByAccount(account);

    return exchangeHistories.stream()
        .map(HistoryResponseDto::from)
        .collect(Collectors.toList());
  }

  // 특정 통화의 거래 내역 조회
  @Override
  public List<HistoryResponseDto> getHistoriesByCurrency(Long accountId, String currency, Long userId) {
    Account account = getAccountByIdWithAuth(accountId, userId);

    var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));

    PageRequest pageable = PageRequest.of(0, 100, sort);

    Page<AccountHistory> page = accountQueryRepository
        .findByAccountAndCurrencyOrderByCreatedAtDesc(account, currency, pageable);

    return page.getContent().stream().map(HistoryResponseDto::from).toList();
  }

  // 기간별 거래 내역 조회
  @Override
  public List<HistoryResponseDto> getHistoriesByDateRange(
      Long accountId, LocalDateTime startDate, LocalDateTime endDate, Long userId
  ) {
    Account account = getAccountByIdWithAuth(accountId, userId);

    var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));

    PageRequest pageable = PageRequest.of(0, 1000, sort);

    Page<AccountHistory> page = accountQueryRepository
        .findByAccountAndRangeOrderByCreatedAtDesc(account, startDate, endDate, pageable);

    return page.getContent().stream().map(HistoryResponseDto::from).toList();
  }

  // 최근 N개 거래 내역 조회
  @Override
  public List<HistoryResponseDto> getRecentHistories(Long accountId, int limit, Long userId) {
    Account account = getAccountByIdWithAuth(accountId, userId);

    var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));

    PageRequest pageable = PageRequest.of(0, limit, sort);

    Page<AccountHistory> page = accountQueryRepository
        .findByAccountAndRangeOrderByCreatedAtDesc(account, null, null, pageable);

    return page.getContent().stream().map(HistoryResponseDto::from).toList();
  }

  // ========== 내부 메서드들 ==========

  // 사용자 소유 계좌인지 검증
  private Account getAccountByIdWithAuth(Long accountId, Long userId) {
    return accountQueryRepository.findByIdAndUserId(accountId, userId)
        .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_ACCESS_DENIED));
  }

  // 계좌 조회 (내부 호출용)
  private Account getAccountByIdInternal(Long accountId) {
    return accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));
  }

  // 금액 유효성 검증
  private void validateAmount(BigDecimal amount) {
    MoneyUtils.validatePositiveAmount(amount, "거래 금액");
  }

  // 통화 유효성 검증
  private void validateCurrency(String currency) {
    if (currency == null || (!currency.equals("KRW") && !currency.equals("USD"))) {
      throw new AccountHistoryException(AccountHistoryResponse.INVALID_CURRENCY);
    }
  }

  // 환전 입력값 검증
  private void validateExchangeInputs(String fromCurrency, String toCurrency,
      BigDecimal originalAmount, BigDecimal exchangedAmount,
      BigDecimal exchangeRate) {
    validateCurrency(fromCurrency);
    validateCurrency(toCurrency);

    if (fromCurrency.equals(toCurrency)) {
      throw new AccountHistoryException(AccountHistoryResponse.SAME_CURRENCY_EXCHANGE);
    }

    validateAmount(originalAmount);
    validateAmount(exchangedAmount);

    if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new AccountHistoryException(AccountHistoryResponse.INVALID_EXCHANGE_RATE);
    }
  }

  // 거래 후 잔액 계산
  private BigDecimal calculateBalanceAfter(Account account, BigDecimal amount, String currency) {
    return switch (currency) {
      case "KRW" -> account.getBalanceKrw().add(amount);
      case "USD" -> account.getBalanceUsd().add(amount);
      default -> throw new AccountHistoryException(AccountHistoryResponse.INVALID_CURRENCY);
    };
  }
}