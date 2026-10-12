package com.muscat.user.domain.account.service.impl;

import com.muscat.commonlib.util.MoneyUtils;
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
import com.muscat.user.domain.account.service.AccountHistoryService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AccountHistoryServiceImpl implements AccountHistoryService {

  private final AccountHistoryRepository accountHistoryRepository;
  private final AccountRepository accountRepository;

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

  // ========== 내부 메서드들 ==========

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
