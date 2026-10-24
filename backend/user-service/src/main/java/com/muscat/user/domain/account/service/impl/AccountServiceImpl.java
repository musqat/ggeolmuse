package com.muscat.user.domain.account.service.impl;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.dto.FxRateRangeDto;
import com.muscat.commonlib.exception.BaseException;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.response.AccountResponse;
import com.muscat.user.common.enums.response.UserResponse;
import com.muscat.user.common.enums.type.CurrencyType;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.common.exception.AccountException;
import com.muscat.user.common.exception.UserException;
import com.muscat.user.common.logging.UserLogger;
import com.muscat.user.common.util.AccountCalculatorUtil;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.dto.response.ExchangeCalculationResult;
import com.muscat.user.domain.account.dto.response.ExchangeRateRangeDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.entity.DividendSettlement;
import com.muscat.user.domain.account.repository.AccountHistoryRepository;
import com.muscat.user.domain.account.repository.AccountRepository;
import com.muscat.user.domain.account.repository.DividendSettlementRepository;
import com.muscat.user.domain.account.service.AccountHistoryService;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepository;
import com.muscat.user.infra.client.MarketDataServiceClientWrapper;
import com.muscat.user.infra.kafka.AccountEventProducer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AccountServiceImpl implements AccountService {

  // 잔액 부족 오류 코드. MoneyUtils(ServiceException) 와 AccountResponse(AccountException) 가 같은 이름을 쓴다
  private static final String INSUFFICIENT_BALANCE_CODE = "INSUFFICIENT_BALANCE";

  private final AccountRepository accountRepository;
  private final AccountHistoryRepository accountHistoryRepository;
  private final UserRepository userRepository;
  private final AccountHistoryService accountHistoryService;
  private final AccountCalculatorUtil accountCalculator;
  private final UserLogger userLogger;
  private final MarketDataServiceClientWrapper marketDataServiceClientWrapper;
  private final AccountEventProducer accountEventProducer;
  private final DividendSettlementRepository dividendSettlementRepository;

  @Value("${app.account.initial-krw-amount:1000000}")
  private BigDecimal initialKrwAmount;

  @Value("${app.exchange-rate.min-valid-rate:1000}")
  private BigDecimal minValidRate;

  @Value("${app.exchange-rate.max-valid-rate:2000}")
  private BigDecimal maxValidRate;

  @Value("${app.exchange-rate.fallback-rate:1350}")
  private BigDecimal fallbackRate;

  // 환전에 쓸 수 있는 환율은 저장된 환율의 (최저 − 이 비율) ~ (최고 + 이 비율). 사용자가 환율을 직접 넣을 수 있어
  // 1원 같은 값으로 잔고를 늘리지 못하게 막는다. 범위가 데이터를 따라가 실제 환율이 새 기록을 내도 정상 환전은 막히지 않는다
  @Value("${app.exchange-rate.exchange-tolerance:0.05}")
  private BigDecimal exchangeTolerance;

  // market-data 에서 범위를 못 받을 때(장애 · 환율 데이터 없음) 대신 쓰는 범위
  @Value("${app.exchange-rate.exchange-fallback-min:1000}")
  private BigDecimal exchangeFallbackMin;

  @Value("${app.exchange-rate.exchange-fallback-max:1650}")
  private BigDecimal exchangeFallbackMax;

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
      .slippageRate(request.getSlippageRate() != null
        ? request.getSlippageRate() : Account.DEFAULT_SLIPPAGE_RATE)
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

    // Kafka 이벤트 발행: 계좌 삭제 (trade-service에서 거래 내역/보유 자산 삭제)
    String deletionReason = String.format("사용자 요청 (KRW: %s, USD: %s)",
      account.getBalanceKrw(), account.getBalanceUsd());
    accountEventProducer.publishAccountDeleted(account, deletionReason);

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

    BigDecimal currentExchangeRate = currentRateOrFallback();

    return BalanceResponseDto.of(account, currentExchangeRate, accountCalculator);
  }

  // KRW → USD 환전 처리 (수수료 없음)
  @Override
  public void exchangeKrwToUsd(Long accountId, Long userId, BigDecimal krwAmount,
    BigDecimal exchangeRate) {
    validateExchangeRate(exchangeRate);

    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
      .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));
    userLogger.logExchangeStart(accountId, "KRW", "USD", krwAmount, exchangeRate);

    exchangeRate = MoneyUtils.roundExchangeRate(exchangeRate);

    // 잔액 검사는 MoneyUtils 의 ServiceException 으로 온다. 메시지가 아니라 오류 코드로 본다
    try {
      accountCalculator.validateExchangeRequest(account, krwAmount, "KRW");
    } catch (BaseException e) {
      if (INSUFFICIENT_BALANCE_CODE.equals(e.getErrorCode())) {
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
    BigDecimal newAvgRate = accountCalculator.calculateNewAverageRate(account, krwAmount,
      exchangeRate);

    // 검증 + 상태 변경
    account.executeExchangeKrwToUsd(totalDeduction, calculation.getFinalAmount(),
      newAvgRate, krwAmount);

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

    userLogger.logExchangeComplete(accountId, calculation, account, referenceId);

    log.info("KRW→USD 환전 완료: 계좌={}, {}, 평균환율={}",
      accountId, calculation.getSummary(), newAvgRate);
  }

  // USD → KRW 환전 처리
  @Override
  public void exchangeUsdToKrw(Long accountId, Long userId, BigDecimal usdAmount,
    BigDecimal exchangeRate) {
    validateExchangeRate(exchangeRate);

    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
      .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));
    userLogger.logExchangeStart(accountId, "USD", "KRW", usdAmount, exchangeRate);

    exchangeRate = MoneyUtils.roundExchangeRate(exchangeRate);

    try {
      accountCalculator.validateExchangeRequest(account, usdAmount, "USD");
    } catch (BaseException e) {
      if (INSUFFICIENT_BALANCE_CODE.equals(e.getErrorCode())) {
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

    // 검증 + 상태 변경 + 평균환율 리셋
    account.executeExchangeUsdToKrw(usdAmount, calculation.getFinalAmount());

    if (account.getBalanceUsd().compareTo(BigDecimal.ZERO) == 0) {
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

    userLogger.logExchangeComplete(accountId, calculation, account, referenceId);

    log.info("USD→KRW 환전 완료: 계좌={}, {}",
      accountId, calculation.getSummary());
  }

  // KRW 입금 처리
  @Override
  public void depositKrw(Long accountId, Long userId, BigDecimal krwAmount) {
    Account account = accountRepository.findByIdAndUserIdWithLock(accountId, userId)
      .orElseThrow(() -> new AccountException(AccountResponse.ACCOUNT_NOT_FOUND));

    krwAmount = MoneyUtils.roundKrw(krwAmount);

    if (krwAmount.compareTo(new BigDecimal("50000000")) > 0) {
      userLogger.logLargeTransaction(accountId, "KRW_DEPOSIT", krwAmount, "KRW");
    }

    // 변경 전 잔액 저장 (이벤트 발행용)
    BigDecimal previousBalanceKrw = account.getBalanceKrw();
    BigDecimal previousBalanceUsd = account.getBalanceUsd();

    // 검증 + 상태 변경
    account.depositKrw(krwAmount);

    // 인프라 처리 (Service 책임)
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

    userLogger.logKrwDeposit(accountId, krwAmount, referenceId);

    log.info("KRW 입금 완료: 계좌={}, 입금액={}", accountId, MoneyUtils.formatAmount(krwAmount, "KRW"));
  }

  // Trade 서비스 전용: USD 잔고 직접 업데이트
  @Override
  @Transactional
  public void updateUsdBalance(Long accountId, Long userId, BigDecimal usdAmount,
    TransactionType type, String description) {
    changeUsdBalance(accountId, userId, usdAmount, type, description, false);
  }

  private void changeUsdBalance(Long accountId, Long userId, BigDecimal usdAmount,
    TransactionType type, String description, boolean allowNegative) {
    String updateType = balanceUpdateType(type);
    Account account = validateAccountAccess(accountId, userId);
    BigDecimal validatedAmount = validateUsdAmount(usdAmount);

    // 변경 전 잔액 저장 (이벤트 발행용)
    BigDecimal previousBalanceKrw = account.getBalanceKrw();
    BigDecimal previousBalanceUsd = account.getBalanceUsd();

    // 검증 + 상태 변경
    if (allowNegative) {
      account.applyDividendAdjustment(validatedAmount);
    } else {
      account.adjustUsdBalance(validatedAmount);
    }
    BigDecimal newBalance = account.getBalanceUsd();

    // 인프라 처리 (Service 책임)
    createTradeHistory(account, validatedAmount, newBalance, type, description);

    // Kafka 이벤트 발행: 계좌 잔액 변경
    accountEventProducer.publishAccountBalanceUpdated(
      account, updateType,
      previousBalanceKrw, previousBalanceUsd,
      BigDecimal.ZERO, validatedAmount,
      description, null, null);

    log.info("USD 잔고 업데이트 완료: accountId={}, 변경금액={}, 변경후잔고={}",
      accountId, validatedAmount, newBalance);
  }

  // 현재 USD/KRW 환율. 못 받거나 정상 범위 밖이면 환율 서비스 오류
  @Override
  public BigDecimal getCurrentExchangeRate() {
    BigDecimal rate = latestValidRate();
    if (rate == null) {
      throw new AccountException(AccountResponse.EXCHANGE_RATE_SERVICE_ERROR);
    }
    return rate;
  }

  // 최신 환율, 못 받으면 설정의 고정 대체 환율
  private BigDecimal currentRateOrFallback() {
    BigDecimal rate = latestValidRate();
    if (rate != null) {
      return rate;
    }
    log.info("Fallback 환율 사용: {}", fallbackRate);
    userLogger.logExchangeRateFallback(fallbackRate, "Market-data 서비스 연결 실패");
    return MoneyUtils.roundExchangeRate(fallbackRate);
  }

  // market-data 의 최신 환율. 못 받거나 정상 범위 밖이면 null
  private BigDecimal latestValidRate() {
    try {
      FxRateDto response = marketDataServiceClientWrapper.getLatestFxRate();
      log.debug("Market-data 응답: response={}, rate={}",
        response,
        response != null ? response.rate() : "null");

      if (response != null && response.rate() != null) {
        BigDecimal rate = MoneyUtils.roundExchangeRate(response.rate());
        if (rate.compareTo(minValidRate) >= 0 && rate.compareTo(maxValidRate) <= 0) {
          log.info("환율 조회 성공: {} (일자: {})", rate, response.date());
          return rate;
        }
        log.warn("비정상적인 환율 감지: {}", rate);
        userLogger.logAbnormalExchangeRate(rate, "MarketDataService에서 조회된 환율");
      }
    } catch (Exception e) {
      log.warn("환율 조회 실패: {}", e.getMessage());
    }
    return null;
  }

  // 범위는 정수로 둔다. 최저는 내리고 최고는 올려 ±허용 비율보다 좁아지지 않게 한다
  @Override
  public ExchangeRateRangeDto getExchangeRateRange() {
    BigDecimal low = exchangeFallbackMin;
    BigDecimal high = exchangeFallbackMax;
    FxRateRangeDto stored = marketDataServiceClientWrapper.getFxRateRange();
    if (stored != null && stored.min() != null && stored.max() != null) {
      low = stored.min().multiply(BigDecimal.ONE.subtract(exchangeTolerance));
      high = stored.max().multiply(BigDecimal.ONE.add(exchangeTolerance));
    } else {
      log.warn("환율 범위를 못 받아 대체 범위를 씀: {} ~ {}", low, high);
    }
    return new ExchangeRateRangeDto(low.setScale(0, RoundingMode.FLOOR), high.setScale(0, RoundingMode.CEILING));
  }

  /**
   * Kafka 이벤트로부터 거래 완료 정보를 받아 계좌 잔액 업데이트
   */
  @Override
  @Transactional
  public void processTradeEvent(TradeCompletedEvent event) {
    log.info("처리 시작: TradeCompletedEvent - tradeId={}, userId={}, tradeType={}, totalAmount={}",
      event.getTradeId(), event.getUserId(), event.getTradeType(), event.getTotalAmount());

    // 1. 사용자 ID 조회
    Long userId = resolveUserId(event.getUserId());

    // 2. 거래한 계좌. 소유자 확인은 updateUsdBalance 가 한다
    Long accountId = resolveTradeAccountId(event.getAccountId(), userId, event.getTradeId());

    // 3. 거래 타입에 따라 잔액 변경 금액 계산
    // BUY: 사용자가 USD를 지불하고 주식을 구매 -> USD 잔액 감소 (음수)
    // SELL: 사용자가 주식을 팔고 USD를 받음 -> USD 잔액 증가 (양수)
    BigDecimal balanceChange;
    TransactionType type;
    String description;

    if ("BUY".equalsIgnoreCase(event.getTradeType())) {
      balanceChange = event.getTotalAmount().negate(); // 음수
      type = TransactionType.TRADE_BUY;
      description = String.format("주식 매수: %s %s주 @ $%s (거래ID: %s)",
        event.getSymbol(), event.getQuantity(), event.getPrice(), event.getTradeId());
    } else if ("SELL".equalsIgnoreCase(event.getTradeType())) {
      balanceChange = event.getTotalAmount(); // 양수
      type = TransactionType.TRADE_SELL;
      description = String.format("주식 매도: %s %s주 @ $%s (거래ID: %s)",
        event.getSymbol(), event.getQuantity(), event.getPrice(), event.getTradeId());
    } else {
      log.error("알 수 없는 거래 타입: {}", event.getTradeType());
      throw new AccountException(AccountResponse.INVALID_TRANSACTION_TYPE);
    }

    log.debug("잔액 변경 계산: tradeType={}, balanceChange={}", event.getTradeType(), balanceChange);

    // 4. 기존 updateUsdBalance 메서드 재사용
    try {
      updateUsdBalance(accountId, userId, balanceChange, type, description);

      log.info("거래 이벤트 처리 완료: tradeId={}, userId={}, accountId={}, balanceChange={}",
        event.getTradeId(), userId, accountId, balanceChange);

    } catch (AccountException e) {
      log.error("잔액 업데이트 실패: tradeId={}, userId={}, accountId={}, error={}",
        event.getTradeId(), userId, accountId, e.getMessage());
      throw e;
    }
  }

  // 거래가 일어난 계좌 ID. 계좌 ID 를 싣기 전에 나간 이벤트는 사용자의 첫 번째 계좌로 처리한다
  private Long resolveTradeAccountId(Long eventAccountId, Long userId, Long tradeId) {
    if (eventAccountId != null) {
      return eventAccountId;
    }

    log.warn("계좌 ID 가 없는 거래 이벤트라 첫 번째 계좌로 처리: tradeId={}, userId={}", tradeId, userId);
    List<Account> accounts = accountRepository.findByUserIdWithUser(userId);
    if (accounts.isEmpty()) {
      log.error("사용자의 계좌가 존재하지 않음: userId={}", userId);
      throw new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
    }
    return accounts.getFirst().getId();
  }

  @Override
  public void applyDividendPayout(DividendPayoutEvent event) {
    // 계좌를 지운 뒤 도착한 몫은 넣을 곳이 없어 건너뜀
    if (!accountRepository.existsById(event.getAccountId())) {
      log.warn("지운 계좌로 온 배당 몫 건너뜀: accountId={}, symbol={}, exDate={}",
        event.getAccountId(), event.getSymbol(), event.getExDate());
      return;
    }
    Long userId = resolveUserId(event.getUserId());
    Account account = findOwnedAccount(event.getAccountId(), userId);
    LocalDate exDate = LocalDate.parse(event.getExDate());
    Optional<DividendSettlement> settlement = dividendSettlementRepository
      .findByAccountIdAndSymbolAndExDate(account.getId(), event.getSymbol(), exDate);

    BigDecimal target = MoneyUtils.roundUsd(event.getNetAmount());
    BigDecimal delta = target.subtract(settlement.map(DividendSettlement::getAmount).orElse(BigDecimal.ZERO));
    String basis = String.format("%s (기준일: %s, 주당: $%s × %s주)",
      event.getSymbol(), event.getExDate(), event.getDividendPerShare(), event.getQuantity());

    if (delta.signum() > 0) {
      changeUsdBalance(account.getId(), userId, delta, TransactionType.DIVIDEND, "배당금 수령: " + basis, false);
    } else if (delta.signum() < 0) {
      changeUsdBalance(account.getId(), userId, delta, TransactionType.DIVIDEND_REVERSAL, "배당 반환: " + basis, true);
    }

    if (settlement.isPresent()) {
      settlement.get().changeAmount(target, LocalDateTime.now());
    } else if (target.signum() > 0) {
      dividendSettlementRepository.saveAndFlush(
        DividendSettlement.of(account.getId(), event.getSymbol(), exDate, target, LocalDateTime.now()));
    }
    log.info("배당 몫 반영: accountId={}, symbol={}, exDate={}, 목표={}, 차이={}",
      account.getId(), event.getSymbol(), exDate, target, delta);
  }

  // ================== 내부 메서드 ================ //

  private Account findOwnedAccount(Long accountId, Long userId) {
    Account account = accountRepository.findById(accountId)
      .orElseThrow(() -> {
        log.error("계좌를 찾을 수 없음: accountId={}", accountId);
        return new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
      });
    if (!account.getUser().getId().equals(userId)) {
      log.error("계좌 소유자 불일치: accountId={}, userId={}, owner={}", accountId, userId, account.getUser().getId());
      throw new AccountException(AccountResponse.ACCOUNT_ACCESS_DENIED);
    }
    return account;
  }

  // 환전 환율이 허용 범위 안인지 본다
  private void validateExchangeRate(BigDecimal exchangeRate) {
    ExchangeRateRangeDto range = getExchangeRateRange();
    if (exchangeRate == null || exchangeRate.compareTo(range.min()) < 0 || exchangeRate.compareTo(range.max()) > 0) {
      throw new AccountException(AccountResponse.INVALID_EXCHANGE_RATE,
        String.format("환율은 %s ~ %s원 사이로 넣어 주세요.", range.min().toPlainString(), range.max().toPlainString()));
    }
  }

  // 중복되지 않는 계좌번호 생성 (최대 10번 시도)
  private String generateUniqueAccountNumber() {
    for (int i = 0; i < 10; i++) {
      String accountNumber = generateAccountNumber();
      if (!accountRepository.existsByAccountNumber(accountNumber)) {
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

  // USD 잔액을 바로 바꾸는 매매 · 배당의 잔액 변경 이벤트 종류
  private static String balanceUpdateType(TransactionType type) {
    return switch (type) {
      case TRADE_BUY, TRADE_SELL -> "TRADE_COMPLETED";
      case DIVIDEND -> "DIVIDEND_RECEIVED";
      case DIVIDEND_REVERSAL -> "DIVIDEND_REVERSED";
      case DEPOSIT, EXCHANGE -> throw new AccountException(AccountResponse.INVALID_TRANSACTION_TYPE);
    };
  }

  // 거래 내역 생성
  private void createTradeHistory(Account account, BigDecimal usdAmount,
    BigDecimal newBalance, TransactionType transactionType, String description) {
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


 //사용자 ID 해석 (Long ID 또는 Keycloak UUID 지원)

  private Long resolveUserId(String userIdStr) {
    // 1. Long으로 파싱 시도
    try {
      return Long.parseLong(userIdStr);
    } catch (NumberFormatException e) {
      log.debug("userId가 Long 형식이 아님, Keycloak UUID로 조회 시도: {}", userIdStr);
    }

    // 2. Keycloak UUID로 User 조회
    User user = userRepository.findByKeycloakId(userIdStr)
      .orElseThrow(() -> {
        log.error("사용자를 찾을 수 없음: keycloakId={}", userIdStr);
        return new AccountException(AccountResponse.ACCOUNT_NOT_FOUND);
      });

    log.debug("Keycloak UUID로 사용자 조회 성공: keycloakId={}, userId={}", userIdStr, user.getId());
    return user.getId();
  }

  // ==================== ADMIN API ==================== //

  @Override
  @Transactional(readOnly = true)
  public List<Account> findAccountsByUserId(Long userId) {
    return accountRepository.findByUserIdWithUser(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<DividendSettlement> getDividendSettlements(int page, int size) {
    return dividendSettlementRepository.findAll(PageRequest.of(page, size, Sort.by("accountId", "symbol", "exDate")));
  }
}
