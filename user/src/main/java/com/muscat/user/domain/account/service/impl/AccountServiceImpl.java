package com.muscat.user.domain.account.service.impl;

import com.muscat.user.common.enums.type.CurrencyType;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.logging.TransactionLogger;
import com.muscat.user.common.responses.AccountResponse;
import com.muscat.user.common.responses.UserResponse;
import com.muscat.user.common.util.AccountCalculatorUtil;
import com.muscat.user.domain.account.dto.response.ExchangeCalculationResult;
import com.muscat.user.common.util.MoneyUtils;
import com.muscat.user.common.responses.ApiResponse;
import com.muscat.user.infra.client.MarketDataServiceClient;
import com.muscat.user.infra.client.dto.FxRateDto;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.service.AccountHistoryService;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AccountServiceImpl implements AccountService {

  private final AccountRepository accountRepository;
  private final AccountHistoryRepository accountHistoryRepository;
  private final UserRepository userRepository;
  private final AccountHistoryService accountHistoryService;
  private final AccountCalculatorUtil accountCalculator;
  private final TransactionLogger transactionLogger;
  private final MarketDataServiceClient marketDataServiceClient;

  @Value("${app.account.initial-krw-amount:1000000}")
  private BigDecimal initialKrwAmount;

  @Value("${app.exchange-rate.min-valid-rate}")
  private BigDecimal minValidRate;

  @Value("${app.exchange-rate.max-valid-rate}")
  private BigDecimal maxValidRate;

  @Value("${app.exchange-rate.fallback-rate}")
  private BigDecimal fallbackRate;

  // 새 계좌 생성 (초기 KRW 잔액 설정)
  @Override
  public Account createAccount(Long userId, CreateAccountRequestDto request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserException(UserResponse.USER_NOT_FOUND));

    if (accountRepository.existsByUserIdAndAccountName(userId, request.getAccountName())) {
      throw new AccountException(AccountResponse.DUPLICATE_ACCOUNT_NAME);
    }

    Account account = Account.builder()
        .user(user)
        .accountNumber(generateUniqueAccountNumber())
        .accountName(request.getAccountName())
        .balanceKrw(MoneyUtils.roundKrw(initialKrwAmount))
        .balanceUsd(BigDecimal.ZERO)
        .totalExchangedKrw(BigDecimal.ZERO)
        .avgExchangeRate(BigDecimal.ZERO)
        .commissionRate(request.getCommissionRate())
        .build();

    Account savedAccount = accountRepository.save(account);

    transactionLogger.logAccountCreation(userId, savedAccount.getId(),
        savedAccount.getAccountNumber(), initialKrwAmount);

    log.info("계좌 생성 완료: 사용자={}, 계좌번호={}, 초기금액={}",
        userId, savedAccount.getAccountNumber(), MoneyUtils.formatAmount(initialKrwAmount, "KRW"));

    return savedAccount;
  }

  // 사용자의 모든 계좌 목록 조회
  @Override
  @Transactional(readOnly = true)
  public List<Account> getUserAccounts(Long userId) {
    return accountRepository.findByUserId(userId);
  }

  // 계좌 잔액 조회 (현재 환율 기준 총 자산 포함)
  @Override
  @Transactional(readOnly = true)
  public BalanceResponseDto getAccountBalance(Long accountId, Long userId) {
    Account account = accountRepository.findByIdAndUserId(accountId, userId)
        .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));

    BigDecimal currentExchangeRate = getCurrentExchangeRate();

    return BalanceResponseDto.from(account, currentExchangeRate, accountCalculator);
  }

  // KRW → USD 환전 처리 (수수료 없음)
  @Override
  public void exchangeKrwToUsd(Long accountId, Long userId, BigDecimal krwAmount,
      BigDecimal exchangeRate) {

    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
        .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));
    transactionLogger.logExchangeStart(accountId, "KRW", "USD", krwAmount, exchangeRate);

    try {
      exchangeRate = MoneyUtils.roundExchangeRate(exchangeRate);

      try {
        accountCalculator.validateExchangeRequest(account, krwAmount, "KRW");
      } catch (AccountException e) {
        if (e.getMessage().contains("잔액 부족")) {
          transactionLogger.logInsufficientBalance(accountId, "KRW",
              krwAmount, account.getBalanceKrw());
        }
        throw e;
      }

      ExchangeCalculationResult calculation = accountCalculator.calculateExchangeWithCommission(
          account, krwAmount, "KRW", "USD", exchangeRate);

      if (krwAmount.compareTo(new BigDecimal("10000000")) > 0) {
        transactionLogger.logLargeTransaction(accountId, "KRW_TO_USD", krwAmount, "KRW");
      }
      BigDecimal totalDeduction = calculation.getTotalKrwDeduction();
      account.setBalanceKrw(account.getBalanceKrw().subtract(totalDeduction));
      account.setBalanceUsd(account.getBalanceUsd().add(calculation.getFinalAmount()));

      BigDecimal newAvgRate = accountCalculator.calculateNewAverageRate(account, krwAmount,
          exchangeRate);
      account.setTotalExchangedKrw(account.getTotalExchangedKrw().add(krwAmount));
      account.setAvgExchangeRate(newAvgRate);
      String referenceId = generateReferenceId(TransactionType.EXCHANGE);

      accountHistoryService.createExchangeHistory(
          accountId, CurrencyType.KRW.name(), CurrencyType.USD.name(),
          krwAmount, calculation.getFinalAmount(), exchangeRate,
          String.format("KRW → USD 환전 (환율: %s)", exchangeRate),
          referenceId);
      transactionLogger.logExchangeComplete(accountId, calculation, account, referenceId);

      log.info("KRW→USD 환전 완료: 계좌={}, {}, 평균환율={}",
          accountId, calculation.getSummary(), newAvgRate);

    } catch (Exception e) {
      transactionLogger.logExchangeFailure(accountId, "KRW", "USD", krwAmount, e.getMessage(), e);
      log.error("KRW→USD 환전 처리 실패: accountId={}, 원인: {}", accountId, e.getMessage(), e);
      throw new AccountException(AccountResponse.EXCHANGE_RATE_SERVICE_ERROR,
          "환전 처리 중 오류가 발생했습니다: " + e.getMessage());
    }
  }

  // USD → KRW 환전 처리
  @Override
  public void exchangeUsdToKrw(Long accountId, Long userId, BigDecimal usdAmount,
      BigDecimal exchangeRate) {

    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
        .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));
    transactionLogger.logExchangeStart(accountId, "USD", "KRW", usdAmount, exchangeRate);

    try {
      exchangeRate = MoneyUtils.roundExchangeRate(exchangeRate);

      try {
        accountCalculator.validateExchangeRequest(account, usdAmount, "USD");
      } catch (AccountException e) {
        if (e.getMessage().contains("잔액 부족")) {
          transactionLogger.logInsufficientBalance(accountId, "USD",
              usdAmount, account.getBalanceUsd());
        }
        throw e;
      }

      ExchangeCalculationResult calculation = accountCalculator.calculateExchangeWithCommission(
          account, usdAmount, "USD", "KRW", exchangeRate);

      if (usdAmount.compareTo(new BigDecimal("10000")) > 0) {
        transactionLogger.logLargeTransaction(accountId, "USD_TO_KRW", usdAmount, "USD");
      }
      account.setBalanceUsd(account.getBalanceUsd().subtract(usdAmount));
      account.setBalanceKrw(account.getBalanceKrw().add(calculation.getFinalAmount()));

      if (MoneyUtils.isEqual(account.getBalanceUsd(), BigDecimal.ZERO, "USD")) {
        account.setAvgExchangeRate(BigDecimal.ZERO);
        account.setTotalExchangedKrw(BigDecimal.ZERO);
        log.debug("USD 잔액 0 - 평균환율 리셋: accountId={}", accountId);
      }
      String referenceId = generateReferenceId(TransactionType.EXCHANGE);

      accountHistoryService.createExchangeHistory(
          accountId, CurrencyType.USD.name(), CurrencyType.KRW.name(),
          usdAmount, calculation.getBeforeCommissionAmount(), exchangeRate,
          String.format("USD → KRW 환전 (환율: %s)", exchangeRate),
          referenceId);
      transactionLogger.logExchangeComplete(accountId, calculation, account, referenceId);

      log.info("USD→KRW 환전 완료: 계좌={}, {}",
          accountId, calculation.getSummary());

    } catch (Exception e) {
      transactionLogger.logExchangeFailure(accountId, "USD", "KRW", usdAmount, e.getMessage(), e);
      log.error("USD→KRW 환전 처리 실패: accountId={}, 원인: {}", accountId, e.getMessage(), e);
      throw new AccountException(AccountResponse.EXCHANGE_RATE_SERVICE_ERROR,
          "환전 처리 중 오류가 발생했습니다: " + e.getMessage());
    }
  }

  // KRW 입금 처리
  @Override
  public void depositKrw(Long accountId, Long userId, BigDecimal krwAmount) {
    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
        .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));

    MoneyUtils.validatePositiveAmount(krwAmount, "입금 금액");
    krwAmount = MoneyUtils.roundKrw(krwAmount);

    if (krwAmount.compareTo(new BigDecimal("50000000")) > 0) {
      transactionLogger.logLargeTransaction(accountId, "KRW_DEPOSIT", krwAmount, "KRW");
    }

    BigDecimal newBalance = account.getBalanceKrw().add(krwAmount);
    account.setBalanceKrw(newBalance);

    String referenceId = generateReferenceId(TransactionType.DEPOSIT);
    accountHistoryService.createDepositHistory(
        accountId, krwAmount, CurrencyType.KRW.name(),
        "KRW 입금", referenceId);

    transactionLogger.logKrwDeposit(accountId, krwAmount, referenceId);

    log.info("KRW 입금 완료: 계좌={}, 입금액={}", accountId, MoneyUtils.formatAmount(krwAmount, "KRW"));
  }

  // ================== 내부 메서드 ================ //

  // 중복되지 않는 계좌번호 생성 (최대 10번 시도)
  private String generateUniqueAccountNumber() {
    for (int i = 0; i < 10; i++) {
      String accountNumber = generateAccountNumber();
      if (accountRepository.findByAccountNumber(accountNumber).isEmpty()) {
        return accountNumber;
      }
    }
    throw new AccountException(AccountResponse.ACCOUNT_CREATION_FAILED);
  }

  // 랜덤 계좌번호 생성 (ACC + 13자리)
  private String generateAccountNumber() {
    return "ACC" + UUID.randomUUID().toString().replace("-", "").substring(0, 13).toUpperCase();
  }

  // 현재 USD/KRW 환율 조회
  @Override
  public BigDecimal getCurrentExchangeRate() {
    try {
      // 1. 최신 환율 조회 시도
      log.debug("최신 환율 조회 시도");
      ApiResponse<FxRateDto> response = marketDataServiceClient.getLatestFxRate();
      
      // 디버그: 응답 확인
      log.debug("Market-data 응답: response={}, data={}, rate={}", 
          response, 
          response != null ? response.getData() : "null",
          response != null && response.getData() != null ? response.getData().getRate() : "null");
      
      if (response != null && response.getData() != null && response.getData().getRate() != null) {
        BigDecimal rate = MoneyUtils.roundExchangeRate(response.getData().getRate());
        
        // 환율 유효성 검증
        if (rate.compareTo(minValidRate) >= 0 && rate.compareTo(maxValidRate) <= 0) {
          log.info("환율 조회 성공: {} (일자: {})", rate, response.getData().getDate());
          return rate;
        } else {
          log.warn("비정상적인 환율 감지: {}, fallback으로 전환", rate);
          transactionLogger.logAbnormalExchangeRate(rate, "MarketDataService에서 조회된 환율");
        }
      }
      
    } catch (Exception e) {
      log.warn("환율 조회 실패, fallback으로 전환: {}", e.getMessage());
    }
    
    // 2. Fallback: 고정 환율 사용
    log.info("Fallback 환율 사용: {}", fallbackRate);
    transactionLogger.logExchangeRateFallback(fallbackRate, "Market-data 서비스 연결 실패");
    
    return MoneyUtils.roundExchangeRate(fallbackRate);
  }

  // 특정 날짜의 USD/KRW 환율 조회
  @Override
  public BigDecimal getExchangeRateByDate(LocalDate date) {
    try {
      // 1. 특정 날짜 환율 조회 시도
      log.debug("특정 날짜 환율 조회 시도: {}", date);
      ApiResponse<FxRateDto> response = marketDataServiceClient.getFxRate(date.toString());
      
      if (response != null && response.getData() != null && response.getData().getRate() != null) {
        BigDecimal rate = MoneyUtils.roundExchangeRate(response.getData().getRate());
        
        // 환율 유효성 검증
        if (rate.compareTo(minValidRate) >= 0 && rate.compareTo(maxValidRate) <= 0) {
          log.info("특정 날짜 환율 조회 성공: {} (일자: {})", rate, date);
          return rate;
        } else {
          log.warn("비정상적인 환율 감지: {}, 최신 환율로 fallback", rate);
          transactionLogger.logAbnormalExchangeRate(rate, "특정 날짜(" + date + ") 환율");
        }
      } else {
        log.info("특정 날짜({}) 환율 데이터 없음, 최신 환율로 fallback", date);
      }
      
    } catch (Exception e) {
      log.warn("특정 날짜({}) 환율 조회 실패, 최신 환율로 fallback: {}", date, e.getMessage());
    }
    
    // 2. Fallback: 최신 환율 사용
    return getCurrentExchangeRate();
  }

  // 수동 환율 입력 (manual)
  @Override
  public BigDecimal createManualExchangeRate(BigDecimal manualRate) {
    if (manualRate == null) {
      throw new AccountException(AccountResponse.INVALID_EXCHANGE_RATE, "환율이 입력되지 않았습니다");
    }
    
    // 환율 유효성 검증
    if (manualRate.compareTo(minValidRate) < 0 || manualRate.compareTo(maxValidRate) > 0) {
      throw new AccountException(AccountResponse.INVALID_EXCHANGE_RATE, 
          String.format("비정상적인 환율입니다: %s (유효 범위: %s~%s)", manualRate, minValidRate, maxValidRate));
    }
    
    BigDecimal rate = MoneyUtils.roundExchangeRate(manualRate);
    log.info("수동 환율 입력: {}", rate);
    transactionLogger.logManualExchangeRate(rate, "사용자 수동 입력");
    
    return rate;
  }

  // 거래 참조 ID 생성
  private String generateReferenceId(TransactionType type) {
    return type.name() + "_" + System.currentTimeMillis() + "_" +
        UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  // Trade 서비스 전용: USD 잔고 직접 업데이트
  @Override
  @Transactional
  public void updateUsdBalance(Long accountId, Long userId, BigDecimal usdAmount, String description) {
    Account account = accountRepository.findByIdWithLock(accountId)
        .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));
    
    // 계좌 소유권 검증
    if (!account.getUser().getId().equals(userId)) {
      throw new AccountException(AccountResponse.ACCOUNT_ACCESS_DENIED, "해당 계좌에 대한 접근 권한이 없습니다");
    }

    // USD 금액 검증 (음수도 허용, 0은 불허)
    if (usdAmount == null || usdAmount.compareTo(BigDecimal.ZERO) == 0) {
      throw new AccountException(AccountResponse.INVALID_DEPOSIT_AMOUNT, "USD 잔고 변경 금액은 0이 될 수 없습니다");
    }
    usdAmount = MoneyUtils.roundUsd(usdAmount);

    BigDecimal newBalance = account.getBalanceUsd().add(usdAmount);
    
    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
      throw new AccountException(AccountResponse.INSUFFICIENT_USD_BALANCE,
          String.format("USD 잔고가 부족합니다. 현재: $%s, 필요: $%s", 
              account.getBalanceUsd(), usdAmount.abs()));
    }

    account.setBalanceUsd(newBalance);
    accountRepository.save(account);

    // 거래 내역 기록
    TransactionType transactionType = usdAmount.compareTo(BigDecimal.ZERO) > 0 
        ? TransactionType.TRADE_SELL : TransactionType.TRADE_BUY;
    String referenceId = generateReferenceId(transactionType);

    AccountHistory history = AccountHistory.builder()
        .account(account)
        .transactionType(transactionType)
        .amount(usdAmount.abs())
        .currency("USD")
        .balanceAfter(newBalance)
        .referenceId(referenceId)
        .description(description)
        .build();

    accountHistoryRepository.save(history);

    log.info("USD 잔고 업데이트 완료: accountId={}, 변경금액={}, 변경후잔고={}", 
        accountId, usdAmount, newBalance);
  }
}