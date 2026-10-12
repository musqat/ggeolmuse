package com.muscat.user.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.user.domain.account.dto.response.ExchangeCalculationResult;
import com.muscat.user.domain.account.entity.Account;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

// 요청 스레드는 재사용된다. 거래 로그가 남긴 MDC 값이 다음 요청 로그에 묻어나면 안 된다
@DisplayName("거래 · 감사 로그의 MDC 정리")
class UserLoggerTest {

  private static final List<String> TX_KEYS = List.of("txType", "accountId", "txId", "txStatus");

  private final UserLogger userLogger = new UserLogger();

  @AfterEach
  void clear() {
    MDC.clear();
  }

  private static void assertTxKeysCleared() {
    TX_KEYS.forEach(key -> assertThat(MDC.get(key)).as(key).isNull());
  }

  @Test
  @DisplayName("환전 시작 말고는 로그를 남긴 뒤 거래 MDC 값을 지우고, 원래 있던 값(traceId)은 남긴다")
  void everyMethod_CleansTransactionMdc() {
    MDC.put("traceId", "trace-1");
    Account account = Account.builder().id(3L).balanceKrw(new BigDecimal("150000"))
      .balanceUsd(new BigDecimal("12.50")).build();
    ExchangeCalculationResult result = ExchangeCalculationResult.ofKrwToUsd(new BigDecimal("13000"), new BigDecimal("1300"));

    List<Runnable> calls = List.of(
      () -> userLogger.logAccountCreation(7L, 3L, "100-1", BigDecimal.ZERO),
      () -> userLogger.logKrwDeposit(3L, new BigDecimal("50000"), "ref-1"),
      () -> userLogger.logExchangeComplete(3L, result, account, "ref-2"),
      () -> userLogger.logExchangeFailure(3L, "KRW", "USD", new BigDecimal("13000"), "잔액 부족",
        new IllegalStateException("잔액 부족")),
      () -> userLogger.logInsufficientBalance(3L, "KRW", new BigDecimal("200000"), new BigDecimal("150000")),
      () -> userLogger.logAbnormalExchangeRate(new BigDecimal("1"), "환전"),
      () -> userLogger.logLargeTransaction(3L, "KRW_DEPOSIT", new BigDecimal("100000000"), "KRW"),
      () -> userLogger.logManualExchangeRate(new BigDecimal("1300"), "환전"),
      () -> userLogger.logExchangeRateFallback(new BigDecimal("1300"), "시세 서비스 응답 없음"));

    for (Runnable call : calls) {
      call.run();
      assertTxKeysCleared();
      assertThat(MDC.get("traceId")).isEqualTo("trace-1");
    }
  }

  @Test
  @DisplayName("환전 시작은 완료 로그까지 거래 MDC 를 유지하고, 완료 로그가 지운다")
  void exchangeStart_KeepsMdcUntilComplete() {
    userLogger.logExchangeStart(3L, "KRW", "USD", new BigDecimal("13000"), new BigDecimal("1300"));

    assertThat(MDC.get("txType")).isEqualTo("KRW_TO_USD");
    assertThat(MDC.get("txStatus")).isEqualTo("STARTED");

    userLogger.logExchangeComplete(3L,
      ExchangeCalculationResult.ofKrwToUsd(new BigDecimal("13000"), new BigDecimal("1300")),
      Account.builder().id(3L).build(), "ref-2");

    assertTxKeysCleared();
  }
}
