package com.muscat.user.common.logging;

import com.muscat.user.domain.account.dto.response.ExchangeCalculationResult;
import com.muscat.user.domain.account.entity.Account;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

// 거래 전용 로거 - 금융 거래 감사 로그 및 보안 이벤트 추적
@Component
@Slf4j
public class TransactionLogger {

  private static final org.slf4j.Logger TRANSACTION_LOG = LoggerFactory.getLogger("TRANSACTION");
  private static final org.slf4j.Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

  // MDC 키
  public static final String TX_TYPE = "txType";
  public static final String ACCOUNT_ID = "accountId";
  public static final String TX_ID = "txId";
  public static final String TX_STATUS = "txStatus";

  // 거래 상태
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_FAILED = "FAILED";
  private static final String STATUS_STARTED = "STARTED";
  private static final String STATUS_REJECTED = "REJECTED";
  private static final String STATUS_WARNING = "WARNING";
  private static final String STATUS_MONITORED = "MONITORED";

  // 계좌 생성 로그
  public void logAccountCreation(Long userId, Long accountId, String accountNumber,
      BigDecimal initialAmount) {
    executeWithMDC("ACCOUNT_CREATE", accountId, STATUS_SUCCESS, null,
        () -> AUDIT_LOG.info("계좌 생성 - 사용자: {}, 계좌: {}, 번호: {}, 초기금액: {}원",
            userId, accountId, accountNumber, initialAmount));
  }

  // KRW 입금 로그
  public void logKrwDeposit(Long accountId, BigDecimal amount, String referenceId) {
    executeWithMDC("KRW_DEPOSIT", accountId, STATUS_SUCCESS, referenceId,
        () -> TRANSACTION_LOG.info("KRW 입금 - 계좌: {}, 금액: {}원, 참조ID: {}",
            accountId, amount, referenceId));
  }

  // 환전 시작 로그
  public void logExchangeStart(Long accountId, String fromCurrency, String toCurrency,
      BigDecimal amount, BigDecimal exchangeRate) {
    String txType = fromCurrency + "_TO_" + toCurrency;
    setupMDC(txType, accountId, STATUS_STARTED, null);

    TRANSACTION_LOG.info("환전 시작 - 계좌: {}, {} {}→{}, 환율: {}",
        accountId, amount, fromCurrency, toCurrency, exchangeRate);
  }

  // 환전 완료 로그
  public void logExchangeComplete(Long accountId, ExchangeCalculationResult result,
      Account account, String referenceId) {
    String txType = result.getFromCurrency() + "_TO_" + result.getToCurrency();

    executeWithMDC(txType, accountId, STATUS_SUCCESS, referenceId, () -> {
      TRANSACTION_LOG.info("환전 완료 - 계좌: {}, {}, 참조ID: {}",
          accountId, result.getDetailedSummary(), referenceId);

      TRANSACTION_LOG.info("환전 후 잔액 - 계좌: {}, KRW: {}원, USD: ${}",
          accountId, account.getBalanceKrw(), account.getBalanceUsd());

      if (hasUsdPosition(account)) {
        TRANSACTION_LOG.info("포지션 정보 - 계좌: {}, 평균환율: {}, 누적환전: {}원",
            accountId, account.getAvgExchangeRate(), account.getTotalExchangedKrw());
      }
    });
  }

  // 환전 실패 로그
  public void logExchangeFailure(Long accountId, String fromCurrency, String toCurrency,
      BigDecimal amount, String errorMessage, Exception e) {
    String txType = fromCurrency + "_TO_" + toCurrency;

    executeWithMDC(txType, accountId, STATUS_FAILED, null,
        () -> TRANSACTION_LOG.error("환전 실패 - 계좌: {}, {} {}→{}, 원인: {}",
            accountId, amount, fromCurrency, toCurrency, errorMessage, e));
  }

  // 잔액 부족 로그
  public void logInsufficientBalance(Long accountId, String currency,
      BigDecimal requested, BigDecimal available) {
    executeWithMDC("INSUFFICIENT_BALANCE", accountId, STATUS_REJECTED, null,
        () -> AUDIT_LOG.warn("잔액 부족 시도 - 계좌: {}, 통화: {}, 요청: {}, 가용: {}",
            accountId, currency, requested, available));
  }

  // 비정상 환율 감지 로그
  public void logAbnormalExchangeRate(BigDecimal exchangeRate, String context) {
    executeWithMDC("ABNORMAL_RATE", null, STATUS_WARNING, null,
        () -> AUDIT_LOG.warn("비정상 환율 감지 - 환율: {}, 컨텍스트: {}", exchangeRate, context));
  }

  // 대용량 거래 로그
  public void logLargeTransaction(Long accountId, String txType, BigDecimal amount,
      String currency) {
    executeWithMDC("LARGE_TRANSACTION", accountId, STATUS_MONITORED, null,
        () -> AUDIT_LOG.info("대용량 거래 - 계좌: {}, 타입: {}, 금액: {} {}",
            accountId, txType, amount, currency));
  }


  // MDC 설정
  private void setupMDC(String txType, Long accountId, String status, String txId) {
    if (txType != null) {
      MDC.put(TX_TYPE, txType);
    }
    if (accountId != null) {
      MDC.put(ACCOUNT_ID, String.valueOf(accountId));
    }
    if (status != null) {
      MDC.put(TX_STATUS, status);
    }
    if (txId != null) {
      MDC.put(TX_ID, txId);
    }
  }

  // MDC로 래핑된 로깅 실행
  private void executeWithMDC(String txType, Long accountId, String status, String txId,
      Runnable logAction) {
    try {
      setupMDC(txType, accountId, status, txId);
      logAction.run();
    } finally {
      cleanupTransactionMDC();
    }
  }

  // USD 포지션 존재 확인
  private boolean hasUsdPosition(Account account) {
    return account.getBalanceUsd().compareTo(BigDecimal.ZERO) > 0 &&
        account.getAvgExchangeRate() != null &&
        account.getAvgExchangeRate().compareTo(BigDecimal.ZERO) > 0;
  }

  // MDC 정리
  private void cleanupTransactionMDC() {
    MDC.remove(TX_TYPE);
    MDC.remove(ACCOUNT_ID);
    MDC.remove(TX_ID);
    MDC.remove(TX_STATUS);
  }

}