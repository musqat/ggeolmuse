package com.muscat.user.domain.account.service.impl;

import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.messaging.event.TradeCancelledEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.enums.responses.UserResponse;
import com.muscat.user.common.enums.type.CurrencyType;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.exceptions.UserException;
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
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.client.dto.FxRateDto;
import com.muscat.user.infra.kafka.AccountEventProducer;
import com.muscat.user.infra.kafka.DepositWithdrawalEventProducer;
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
  private final UserLogger userLogger;
  private final MarketDataServiceClientWrapper marketDataServiceClientWrapper;
  private final AccountEventProducer accountEventProducer;
  private final DepositWithdrawalEventProducer depositWithdrawalEventProducer;

  @Value("${app.account.initial-krw-amount:1000000}")
  private BigDecimal initialKrwAmount;

  @Value("${app.exchange-rate.min-valid-rate:1000}")
  private BigDecimal minValidRate;

  @Value("${app.exchange-rate.max-valid-rate:2000}")
  private BigDecimal maxValidRate;

  @Value("${app.exchange-rate.fallback-rate:1350}")
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

    userLogger.logAccountCreation(userId, savedAccount.getId(),
      savedAccount.getAccountNumber(), initialKrwAmount);

    log.info("계좌 생성 완료: 사용자={}, 계좌번호={}, 초기금액={}",
      userId, savedAccount.getAccountNumber(), MoneyUtils.formatAmount(initialKrwAmount, "KRW"));

    return savedAccount;
  }

  // 사용자의 모든 계좌 목록 조회
  @Override
  @Transactional(readOnly = true)
  public List<Account> getUserAccounts(Long userId) {
    return accountRepository.findByUserIdWithUser(userId);
  }

  // 계좌 삭제
  @Override
  public void deleteAccount(Long accountId, Long userId) {
    Account account = accountRepository.findByIdAndUserId(accountId, userId)
      .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));

    // 잔액 확인 로직 제거 - 잔액이 있어도 삭제 가능
    // 삭제 시 잔액 정보를 로그에 기록
    log.info("계좌 삭제 요청: accountId={}, KRW잔액={}, USD잔액={}",
      accountId, account.getBalanceKrw(), account.getBalanceUsd());

    // 계좌 거래 내역 먼저 삭제 (외래 키 제약 조건 해결)
    accountHistoryRepository.deleteByAccount(account);
    log.debug("계좌 거래 내역 삭제 완료: accountId={}", accountId);

    // 계좌 삭제
    accountRepository.delete(account);

    log.info("계좌 삭제 완료: 사용자={}, 계좌ID={}, 계좌번호={}",
      userId, accountId, account.getAccountNumber());
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
    userLogger.logExchangeStart(accountId, "KRW", "USD", krwAmount, exchangeRate);

    exchangeRate = MoneyUtils.roundExchangeRate(exchangeRate);

    try {
      accountCalculator.validateExchangeRequest(account, krwAmount, "KRW");
    } catch (AccountException e) {
      if (e.getMessage().contains("잔액 부족")) {
        userLogger.logInsufficientBalance(accountId, "KRW",
          krwAmount, account.getBalanceKrw());
      }
      throw e;
    }

    ExchangeCalculationResult calculation = accountCalculator.calculateExchangeWithCommission(
      account, krwAmount, "KRW", "USD", exchangeRate);

    if (krwAmount.compareTo(new BigDecimal("10000000")) > 0) {
      userLogger.logLargeTransaction(accountId, "KRW_TO_USD", krwAmount, "KRW");
    }

    // 변경 전 잔액 저장
    BigDecimal previousBalanceKrw = account.getBalanceKrw();
    BigDecimal previousBalanceUsd = account.getBalanceUsd();

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

    // Kafka 이벤트 발행: 계좌 잔액 변경
    accountEventProducer.publishAccountBalanceUpdated(
      account, "EXCHANGE_TO_USD",
      previousBalanceKrw, previousBalanceUsd,
      totalDeduction.negate(), calculation.getFinalAmount(),
      String.format("KRW → USD 환전 (환율: %s)", exchangeRate),
      null, exchangeRate);

    // Kafka 이벤트 발행: KRW 출금 (환전)
    depositWithdrawalEventProducer.publishWithdrawalCompleted(
      account, "KRW", totalDeduction, previousBalanceKrw,
      "EXCHANGE", referenceId,
      String.format("KRW → USD 환전 (환율: %s)", exchangeRate), true);

    userLogger.logExchangeComplete(accountId, calculation, account, referenceId);

    log.info("KRW→USD 환전 완료: 계좌={}, {}, 평균환율={}",
      accountId, calculation.getSummary(), newAvgRate);
  }

  // USD → KRW 환전 처리
  @Override
  public void exchangeUsdToKrw(Long accountId, Long userId, BigDecimal usdAmount,
    BigDecimal exchangeRate) {

    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
      .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));
    userLogger.logExchangeStart(accountId, "USD", "KRW", usdAmount, exchangeRate);

    exchangeRate = MoneyUtils.roundExchangeRate(exchangeRate);

    try {
      accountCalculator.validateExchangeRequest(account, usdAmount, "USD");
    } catch (AccountException e) {
      if (e.getMessage().contains("잔액 부족")) {
        userLogger.logInsufficientBalance(accountId, "USD",
          usdAmount, account.getBalanceUsd());
      }
      throw e;
    }

    ExchangeCalculationResult calculation = accountCalculator.calculateExchangeWithCommission(
      account, usdAmount, "USD", "KRW", exchangeRate);

    if (usdAmount.compareTo(new BigDecimal("10000")) > 0) {
      userLogger.logLargeTransaction(accountId, "USD_TO_KRW", usdAmount, "USD");
    }

    // 변경 전 잔액 저장
    BigDecimal previousBalanceKrw = account.getBalanceKrw();
    BigDecimal previousBalanceUsd = account.getBalanceUsd();

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

    // Kafka 이벤트 발행: 계좌 잔액 변경
    accountEventProducer.publishAccountBalanceUpdated(
      account, "EXCHANGE_TO_KRW",
      previousBalanceKrw, previousBalanceUsd,
      calculation.getFinalAmount(), usdAmount.negate(),
      String.format("USD → KRW 환전 (환율: %s)", exchangeRate),
      null, exchangeRate);

    // Kafka 이벤트 발행: USD 출금 (환전)
    depositWithdrawalEventProducer.publishWithdrawalCompleted(
      account, "USD", usdAmount, previousBalanceUsd,
      "EXCHANGE", referenceId,
      String.format("USD → KRW 환전 (환율: %s)", exchangeRate), true);

    userLogger.logExchangeComplete(accountId, calculation, account, referenceId);

    log.info("USD→KRW 환전 완료: 계좌={}, {}",
      accountId, calculation.getSummary());
  }

  // KRW 입금 처리
  @Override
  public void depositKrw(Long accountId, Long userId, BigDecimal krwAmount) {
    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
      .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));

    MoneyUtils.validatePositiveAmount(krwAmount, "입금 금액");
    krwAmount = MoneyUtils.roundKrw(krwAmount);

    if (krwAmount.compareTo(new BigDecimal("50000000")) > 0) {
      userLogger.logLargeTransaction(accountId, "KRW_DEPOSIT", krwAmount, "KRW");
    }

    // 변경 전 잔액 저장
    BigDecimal previousBalanceKrw = account.getBalanceKrw();
    BigDecimal previousBalanceUsd = account.getBalanceUsd();

    BigDecimal newBalance = account.getBalanceKrw().add(krwAmount);
    account.setBalanceKrw(newBalance);

    String referenceId = generateReferenceId(TransactionType.DEPOSIT);
    accountHistoryService.createDepositHistory(
      accountId, krwAmount, CurrencyType.KRW.name(),
      "KRW 입금", referenceId);

    // Kafka 이벤트 발행: 계좌 잔액 변경
    accountEventProducer.publishAccountBalanceUpdated(
      account, "DEPOSIT_KRW",
      previousBalanceKrw, previousBalanceUsd,
      krwAmount, BigDecimal.ZERO,
      "KRW 입금", null, null);

    // Kafka 이벤트 발행: 입금 완료
    depositWithdrawalEventProducer.publishDepositCompleted(
      account, "KRW", krwAmount, previousBalanceKrw,
      "MANUAL", referenceId, "KRW 입금");

    userLogger.logKrwDeposit(accountId, krwAmount, referenceId);

    log.info("KRW 입금 완료: 계좌={}, 입금액={}", accountId, MoneyUtils.formatAmount(krwAmount, "KRW"));
  }

  // Trade 서비스 전용: USD 잔고 직접 업데이트
  @Override
  @Transactional
  public void updateUsdBalance(Long accountId, Long userId, BigDecimal usdAmount,
    String description) {
    Account account = validateAccountAccess(accountId, userId);
    BigDecimal validatedAmount = validateUsdAmount(usdAmount);

    // 변경 전 잔액 저장
    BigDecimal previousBalanceKrw = account.getBalanceKrw();
    BigDecimal previousBalanceUsd = account.getBalanceUsd();

    BigDecimal newBalance = updateAccountBalance(account, validatedAmount);
    createTradeHistory(account, validatedAmount, newBalance, description);

    // 업데이트 타입 결정 (description에서 추론)
    String updateType;
    if (description.contains("주식 매수")) {
      updateType = "TRADE_COMPLETED";
    } else if (description.contains("주식 매도")) {
      updateType = "TRADE_COMPLETED";
    } else if (description.contains("거래 취소")) {
      updateType = "TRADE_CANCELLED";
    } else if (description.contains("배당금")) {
      updateType = "DIVIDEND_RECEIVED";
    } else {
      updateType = "USD_BALANCE_UPDATE";
    }

    // Kafka 이벤트 발행: 계좌 잔액 변경
    accountEventProducer.publishAccountBalanceUpdated(
      account, updateType,
      previousBalanceKrw, previousBalanceUsd,
      BigDecimal.ZERO, validatedAmount,
      description, null, null);

    log.info("USD 잔고 업데이트 완료: accountId={}, 변경금액={}, 변경후잔고={}",
      accountId, validatedAmount, newBalance);
  }

  // 현재 USD/KRW 환율 조회
  @Override
  public BigDecimal getCurrentExchangeRate() {
    try {
      // 1. 최신 환율 조회 시도
      log.debug("최신 환율 조회 시도");
      FxRateDto response = marketDataServiceClientWrapper.getLatestFxRate();

      // 디버그: 응답 확인
      log.debug("Market-data 응답: response={}, rate={}",
        response,
        response != null ? response.getRate() : "null");

      if (response != null && response.getRate() != null) {
        BigDecimal rate = MoneyUtils.roundExchangeRate(response.getRate());

        // 환율 유효성 검증
        if (rate.compareTo(minValidRate) >= 0 && rate.compareTo(maxValidRate) <= 0) {
          log.info("환율 조회 성공: {} (일자: {})", rate, response.getDate());
          return rate;
        } else {
          log.warn("비정상적인 환율 감지: {}, fallback으로 전환", rate);
          userLogger.logAbnormalExchangeRate(rate, "MarketDataService에서 조회된 환율");
        }
      }

    } catch (Exception e) {
      log.warn("환율 조회 실패, fallback으로 전환: {}", e.getMessage());
    }

    // 2. Fallback: 고정 환율 사용
    log.info("Fallback 환율 사용: {}", fallbackRate);
    userLogger.logExchangeRateFallback(fallbackRate, "Market-data 서비스 연결 실패");

    return MoneyUtils.roundExchangeRate(fallbackRate);
  }

  // 특정 날짜의 USD/KRW 환율 조회
  @Override
  public BigDecimal getExchangeRateByDate(LocalDate date) {
    try {
      // 1. 특정 날짜 환율 조회 시도
      log.debug("특정 날짜 환율 조회 시도: {}", date);
      FxRateDto response = marketDataServiceClientWrapper.getFxRate(date.toString());

      if (response != null && response.getRate() != null) {
        BigDecimal rate = MoneyUtils.roundExchangeRate(response.getRate());

        // 환율 유효성 검증
        if (rate.compareTo(minValidRate) >= 0 && rate.compareTo(maxValidRate) <= 0) {
          log.info("특정 날짜 환율 조회 성공: {} (일자: {})", rate, date);
          return rate;
        } else {
          log.warn("비정상적인 환율 감지: {}, 최신 환율로 fallback", rate);
          userLogger.logAbnormalExchangeRate(rate, "특정 날짜(" + date + ") 환율");
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
      throw new AccountException(AccountResponse.INVALID_EXCHANGE_RATE);
    }

    // 환율 유효성 검증
    if (manualRate.compareTo(minValidRate) < 0 || manualRate.compareTo(maxValidRate) > 0) {
      throw new AccountException(AccountResponse.INVALID_EXCHANGE_RATE);
    }

    BigDecimal rate = MoneyUtils.roundExchangeRate(manualRate);
    log.info("수동 환율 입력: {}", rate);
    userLogger.logManualExchangeRate(rate, "사용자 수동 입력");

    return rate;
  }

  /**
   * Kafka 이벤트로부터 거래 완료 정보를 받아 계좌 잔액 업데이트
   */
  @Override
  @Transactional
  public void processTradeEvent(TradeCompletedEvent event) {
    log.info("처리 시작: TradeCompletedEvent - tradeId={}, userId={}, tradeType={}, totalAmount={}",
      event.getTradeId(), event.getUserId(), event.getTradeType(), event.getTotalAmount());

    // 1. 사용자 ID를 Long으로 변환
    Long userId;
    try {
      userId = Long.parseLong(event.getUserId());
    } catch (NumberFormatException e) {
      log.error("잘못된 userId 형식: {}", event.getUserId());
      throw new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
    }

    // 2. 사용자의 계좌 조회 (첫 번째 계좌 사용)
    List<Account> accounts = accountRepository.findByUserIdWithUser(userId);
    if (accounts.isEmpty()) {
      log.error("사용자의 계좌가 존재하지 않음: userId={}", userId);
      throw new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
    }

    Account account = accounts.get(0); // 첫 번째 계좌 사용
    log.debug("계좌 선택: accountId={}, accountNumber={}", account.getId(), account.getAccountNumber());

    // 3. 거래 타입에 따라 잔액 변경 금액 계산
    // BUY: 사용자가 USD를 지불하고 주식을 구매 -> USD 잔액 감소 (음수)
    // SELL: 사용자가 주식을 팔고 USD를 받음 -> USD 잔액 증가 (양수)
    BigDecimal balanceChange;
    String description;

    if ("BUY".equalsIgnoreCase(event.getTradeType())) {
      balanceChange = event.getTotalAmount().negate(); // 음수
      description = String.format("주식 매수: %s %s주 @ $%s (거래ID: %s)",
        event.getSymbol(), event.getQuantity(), event.getPrice(), event.getTradeId());
    } else if ("SELL".equalsIgnoreCase(event.getTradeType())) {
      balanceChange = event.getTotalAmount(); // 양수
      description = String.format("주식 매도: %s %s주 @ $%s (거래ID: %s)",
        event.getSymbol(), event.getQuantity(), event.getPrice(), event.getTradeId());
    } else {
      log.error("알 수 없는 거래 타입: {}", event.getTradeType());
      throw new AccountException(AccountResponse.INVALID_TRANSACTION_TYPE);
    }

    log.debug("잔액 변경 계산: tradeType={}, balanceChange={}", event.getTradeType(), balanceChange);

    // 4. 기존 updateUsdBalance 메서드 재사용
    try {
      updateUsdBalance(account.getId(), userId, balanceChange, description);

      log.info("거래 이벤트 처리 완료: tradeId={}, userId={}, accountId={}, balanceChange={}",
        event.getTradeId(), userId, account.getId(), balanceChange);

    } catch (AccountException e) {
      log.error("잔액 업데이트 실패: tradeId={}, userId={}, accountId={}, error={}",
        event.getTradeId(), userId, account.getId(), e.getMessage());
      throw e;
    }
  }

  /**
   * 거래 취소 이벤트 처리
   */
  @Override
  public void processTradeCancellationEvent(TradeCancelledEvent event) {
    log.info("처리 시작: TradeCancelledEvent (Saga 보상) - tradeId={}, userId={}, tradeType={}, totalAmount={}, reason={}",
      event.getTradeId(), event.getUserId(), event.getTradeType(), event.getTotalAmount(),
      event.getCancellationReason());

    // 1. 사용자 ID를 Long으로 변환
    Long userId;
    try {
      userId = Long.parseLong(event.getUserId());
    } catch (NumberFormatException e) {
      log.error("잘못된 userId 형식: {}", event.getUserId());
      throw new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
    }

    // 2. 사용자의 계좌 조회 (첫 번째 계좌 사용)
    List<Account> accounts = accountRepository.findByUserIdWithUser(userId);
    if (accounts.isEmpty()) {
      log.error("사용자의 계좌가 존재하지 않음: userId={}", userId);
      throw new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
    }

    Account account = accounts.get(0); // 첫 번째 계좌 사용
    log.debug("계좌 선택: accountId={}, accountNumber={}", account.getId(), account.getAccountNumber());

    // 3. 거래 취소를 위한 잔액 변경 계산 (원래 거래의 반대)
    // 원래 BUY였으면: USD가 감소했었음 -> 다시 증가시켜야 함 (양수)
    // 원래 SELL이었으면: USD가 증가했었음 -> 다시 감소시켜야 함 (음수)
    BigDecimal compensationAmount;
    String description;

    if ("BUY".equalsIgnoreCase(event.getTradeType())) {
      // BUY 취소: 차감된 금액을 다시 돌려줌 (양수)
      compensationAmount = event.getTotalAmount(); // 양수
      description = String.format("거래 취소 (매수 원복): %s %s주 @ $%s (거래ID: %s, 사유: %s)",
        event.getSymbol(), event.getQuantity(), event.getPrice(), event.getTradeId(),
        event.getCancellationReason());
    } else if ("SELL".equalsIgnoreCase(event.getTradeType())) {
      // SELL 취소: 증가된 금액을 다시 차감 (음수)
      compensationAmount = event.getTotalAmount().negate(); // 음수
      description = String.format("거래 취소 (매도 원복): %s %s주 @ $%s (거래ID: %s, 사유: %s)",
        event.getSymbol(), event.getQuantity(), event.getPrice(), event.getTradeId(),
        event.getCancellationReason());
    } else {
      log.error("알 수 없는 거래 타입: {}", event.getTradeType());
      throw new AccountException(AccountResponse.INVALID_TRANSACTION_TYPE);
    }

    log.debug("보상 트랜잭션 계산: tradeType={}, originalAmount={}, compensationAmount={}",
      event.getTradeType(), event.getTotalAmount(), compensationAmount);

    // 4. 잔액 원복 (보상 트랜잭션)
    try {
      updateUsdBalance(account.getId(), userId, compensationAmount, description);

      log.info("거래 취소 이벤트 처리 완료 (잔액 원복): tradeId={}, userId={}, accountId={}, compensationAmount={}, originalEventId={}",
        event.getTradeId(), userId, account.getId(), compensationAmount, event.getOriginalEventId());

    } catch (AccountException e) {
      log.error("보상 트랜잭션 실패: tradeId={}, userId={}, accountId={}, error={}",
        event.getTradeId(), userId, account.getId(), e.getMessage());
      throw e;
    }
  }

  /**
   * 배당금 수령 이벤트 처리
   *
   * DividendReceivedEvent를 처리하여 사용자 계좌에 배당금을 입금합니다.
   */
  @Override
  public void processDividendReceivedEvent(DividendReceivedEvent event) {
    log.info("처리 시작: DividendReceivedEvent - userId={}, accountId={}, symbol={}, amount={}",
      event.getUserId(), event.getAccountId(), event.getSymbol(), event.getTotalAmount());

    // 1. 사용자 ID를 Long으로 변환
    Long userId;
    try {
      userId = Long.parseLong(event.getUserId());
    } catch (NumberFormatException e) {
      log.error("잘못된 userId 형식: {}", event.getUserId());
      throw new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
    }

    // 2. 계좌 조회
    Account account = accountRepository.findById(event.getAccountId())
      .orElseThrow(() -> {
        log.error("계좌를 찾을 수 없음: accountId={}", event.getAccountId());
        return new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
      });

    // 3. 계좌 소유자 확인
    if (!account.getUser().getId().equals(userId)) {
      log.error("계좌 소유자 불일치: accountId={}, userId={}, owner={}",
        event.getAccountId(), userId, account.getUser().getId());
      throw new AccountException(AccountResponse.ACCOUNT_ACCESS_DENIED);
    }

    // 4. 배당금 입금
    String description = String.format("배당금 수령: %s (기준일: %s, 주당: $%s × %s주)",
      event.getSymbol(), event.getExDate(), event.getDividendPerShare(), event.getQuantity());

    try {
      updateUsdBalance(account.getId(), userId, event.getTotalAmount(), description);

      log.info("배당금 입금 완료: userId={}, accountId={}, symbol={}, amount={}",
        userId, event.getAccountId(), event.getSymbol(), event.getTotalAmount());

    } catch (AccountException e) {
      log.error("배당금 입금 실패: userId={}, accountId={}, symbol={}, error={}",
        userId, event.getAccountId(), event.getSymbol(), e.getMessage());
      throw e;
    }
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


  // 거래 참조 ID 생성
  private String generateReferenceId(TransactionType type) {
    return type.name() + "_" + System.currentTimeMillis() + "_" +
      UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }


  // 계좌 접근 권한 검증
  private Account validateAccountAccess(Long accountId, Long userId) {
    Account account = accountRepository.findByIdWithLock(accountId)
      .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));

    if (!account.getUser().getId().equals(userId)) {
      throw new AccountException(AccountResponse.ACCOUNT_ACCESS_DENIED);
    }

    return account;
  }

  // USD 금액 유효성 검증
  private BigDecimal validateUsdAmount(BigDecimal usdAmount) {
    if (usdAmount == null || usdAmount.compareTo(BigDecimal.ZERO) == 0) {
      throw new AccountException(AccountResponse.INVALID_DEPOSIT_AMOUNT);
    }
    return MoneyUtils.roundUsd(usdAmount);
  }

  // 계좌 잔액 업데이트
  private BigDecimal updateAccountBalance(Account account, BigDecimal usdAmount) {
    BigDecimal newBalance = account.getBalanceUsd().add(usdAmount);

    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
      throw new AccountException(AccountResponse.INSUFFICIENT_USD_BALANCE);
    }

    account.setBalanceUsd(newBalance);
    accountRepository.save(account);

    return newBalance;
  }

  // 거래 내역 생성
  private void createTradeHistory(Account account, BigDecimal usdAmount,
    BigDecimal newBalance, String description) {
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
  }

  // ==================== ADMIN API ==================== //

  @Override
  @Transactional(readOnly = true)
  public List<Account> findAccountsByUserId(Long userId) {
    return accountRepository.findByUserIdWithUser(userId);
  }
}
